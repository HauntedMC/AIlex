from __future__ import annotations

import argparse
import compileall
import importlib.util
import json
import os
import random
import shutil
import subprocess
import sys
import tempfile
import time
import webbrowser
from pathlib import Path
from typing import Any

from .external import (
    run_inspect,
    run_longmemeval_v2,
    run_ragchecker,
    score_longmemeval,
    setup_longmemeval_v2,
    setup_upstreams,
)
from .judge import judge_result
from .report import compare_runs, load_jsonl, write_jsonl, write_report
from .suites import materialize

REPO = Path(__file__).resolve().parents[2]
BENCH = REPO / "benchmark"
CACHE = BENCH / ".cache"
RESULTS = BENCH / "results"
LOCAL = BENCH / "local"
V2_VENV = BENCH / ".venv-v2"
SUITES = [
    "smoke",
    "haunted",
    "holdout",
    "replays",
    "longmemeval",
    "longmemeval-oracle",
    "memoryagentbench",
    "standard",
    "extended",
]


def main(argv: list[str] | None = None) -> None:
    parser = build_parser()
    args = parser.parse_args(argv)
    if args.command == "doctor":
        doctor()
    elif args.command == "setup":
        setup(args)
    elif args.command == "selftest":
        selftest()
    elif args.command == "list":
        list_suites()
    elif args.command == "estimate":
        estimate_command(args)
    elif args.command == "run":
        run_command(args)
    elif args.command == "compare":
        compare_command(args)
    elif args.command == "view":
        view_command(args)
    elif args.command == "score-longmemeval":
        score_longmemeval(resolve_run(args.run), CACHE, args.model)
    elif args.command == "ragcheck":
        run_ragchecker(resolve_run(args.run), args.model)
    elif args.command == "inspect":
        run_inspect(REPO, resolve_run(args.run), args.model)
    elif args.command == "external-setup":
        setup_upstreams(CACHE, REPO)
    elif args.command == "v2-setup":
        v2_setup_command(args)
    elif args.command == "v2-run":
        v2_run_command(args)
    else:
        parser.print_help()


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="./bench", description="Local-only live benchmark runner for AIlex")
    sub = parser.add_subparsers(dest="command")
    sub.add_parser("doctor", help="Check Java, key, optional evaluators and local environment")
    sub.add_parser("selftest", help="Compile/check the benchmark harness without provider calls")

    setup_parser = sub.add_parser("setup", help="Create benchmark/.venv and install evaluation tooling")
    setup_parser.add_argument("--ragchecker", action="store_true", help="Also install RAGChecker and its spaCy model")
    setup_parser.add_argument("--official", action="store_true", help="Also install the official LongMemEval scorer requirements")
    setup_parser.add_argument("--v2", action="store_true", help="Also create the isolated LongMemEval-V2 environment")

    sub.add_parser("list", help="List benchmark suites")

    estimate_parser = sub.add_parser("estimate", help="Estimate a suite's conservative token envelope and optional cost")
    add_suite_filters(estimate_parser)

    run_parser = sub.add_parser("run", help="Run AIlex through a materialized benchmark suite using OPENAI_API_KEY")
    add_suite_filters(run_parser)
    run_parser.add_argument("--repeat", type=int, default=1)
    run_parser.add_argument("--judge", choices=["auto", "none", "all"], default="auto")
    run_parser.add_argument("--judge-model", default=None)
    run_parser.add_argument("--official", action="store_true", help="Run the upstream LongMemEval judge after generation")
    run_parser.add_argument("--ragchecker", action="store_true", help="Run RAGChecker on eligible RAG cases")
    run_parser.add_argument("--inspect", action="store_true", help="Create an Inspect AI evaluation log for saved answers")
    run_parser.add_argument("--max-cost", type=float, default=None, help="Refuse to start above this conservative USD estimate")
    run_parser.add_argument("--set", action="append", default=[], metavar="KEY=VALUE", help="Override one AIlex config key")
    run_parser.add_argument("--run-id", default=None)

    compare_parser = sub.add_parser("compare", help="Compare two saved benchmark runs by paired case id")
    compare_parser.add_argument("left")
    compare_parser.add_argument("right")

    view_parser = sub.add_parser("view", help="Open the generated HTML report")
    view_parser.add_argument("run", nargs="?", default="latest")

    score_parser = sub.add_parser("score-longmemeval", help="Run the official LongMemEval QA evaluator")
    score_parser.add_argument("run")
    score_parser.add_argument("--model", default="gpt-4o")

    rag_parser = sub.add_parser("ragcheck", help="Run upstream RAGChecker on a saved AIlex run")
    rag_parser.add_argument("run")
    rag_parser.add_argument("--model", default=None)

    inspect_parser = sub.add_parser("inspect", help="Evaluate saved AIlex answers with Inspect AI")
    inspect_parser.add_argument("run")
    inspect_parser.add_argument("--model", default=None)

    sub.add_parser("external-setup", help="Clone official benchmark repositories into the local cache")

    v2_setup = sub.add_parser("v2-setup", help="Install the AIlex LongMemEval-V2 adapter and isolated environment")
    v2_setup.add_argument("--download-data", action="store_true", help="Also download, prepare and validate the official dataset")
    v2_setup.add_argument("--data-root", default=None, help="Dataset directory; defaults to benchmark/.cache/longmemeval-v2/data")

    v2_run = sub.add_parser("v2-run", help="Run AIlex text memory through the official LongMemEval-V2 harness")
    v2_run.add_argument("--data-root", default=None, help="Dataset directory; defaults to benchmark/.cache/longmemeval-v2/data")
    v2_run.add_argument("--tier", choices=["small", "medium"], default="small")
    v2_run.add_argument("--domain", choices=["web", "enterprise", "both"], default="both")
    v2_run.add_argument("--limit", type=int, default=0)
    v2_run.add_argument("--reader-model", default=None)
    v2_run.add_argument("--reader-base-url", default=None)
    v2_run.add_argument("--evaluator-model", default=None)
    v2_run.add_argument("--run-id", default=None)
    return parser


def add_suite_filters(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("suite", choices=SUITES)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--sample", type=int, default=0, help="Deterministically sample N cases after materialization")
    parser.add_argument("--category", default="")
    parser.add_argument("--case", dest="case_id", default="")


def doctor() -> None:
    print(f"repo: {REPO}")
    print(f"python: {sys.version.split()[0]}")
    print(f"OPENAI_API_KEY: {'present' if os.environ.get('OPENAI_API_KEY', '').strip() else 'MISSING'}")
    print(f"CI: {os.environ.get('CI', 'false')}")
    java = command_output(["java", "-version"], stderr=True).splitlines()
    print(f"java: {java[0] if java else 'not found'}")
    print(f"gradle wrapper: {'ok' if (REPO / 'gradlew').is_file() else 'missing'}")
    for module in ("datasets", "inspect_ai", "ragchecker"):
        print(f"{module}: {'installed' if importlib.util.find_spec(module) else 'not installed'}")
    print(f"LongMemEval-V2 env: {'installed' if v2_python().is_file() else 'not installed'}")
    data_root = default_v2_data_root()
    v2_data = (data_root / "questions.jsonl").is_file() and (data_root / "trajectories.jsonl").is_file()
    print(f"LongMemEval-V2 data: {'ready' if v2_data else 'not downloaded'} ({data_root})")
    if os.environ.get("CI", "").lower() == "true":
        print("live benchmark: BLOCKED (local runner refuses CI=true)")
    elif os.environ.get("OPENAI_API_KEY", "").strip():
        print("live benchmark: ready")
    else:
        print("live benchmark: set OPENAI_API_KEY first")


def setup(args: argparse.Namespace) -> None:
    venv = BENCH / ".venv"
    if not (venv / "bin/python").exists():
        subprocess.run([sys.executable, "-m", "venv", str(venv)], check=True)
    python = venv / "bin/python"
    pip = [str(python), "-m", "pip"]
    subprocess.run(pip + ["install", "--upgrade", "pip", "setuptools", "wheel"], check=True)
    extra = "external,ragchecker" if args.ragchecker else "external"
    subprocess.run(pip + ["install", "-e", f"{BENCH}[{extra}]"], check=True)
    setup_upstreams(CACHE, REPO)
    if args.ragchecker:
        subprocess.run([str(python), "-m", "spacy", "download", "en_core_web_sm"], check=True)
    if args.official:
        longmemeval = CACHE / "upstream/LongMemEval"
        subprocess.run(pip + ["install", "-r", str(longmemeval / "requirements-lite.txt")], check=True)
    if args.v2:
        ensure_v2_environment()
    print("Setup complete. Re-run ./bench so the wrapper picks benchmark/.venv/bin/python.")


def selftest() -> None:
    print("Checking Python syntax...")
    if not compileall.compile_dir(BENCH / "ailex_bench", quiet=1, force=True):
        raise RuntimeError("Python benchmark syntax check failed")
    cases = materialize(REPO, "haunted", CACHE)
    ids = {str(case.get("id")) for case in cases}
    required = {"memory-correction-current", "missing-server-evidence", "discord-explicit-aankondigingen"}
    missing = sorted(required - ids)
    if missing:
        raise RuntimeError(f"Haunted benchmark materialization is missing required cases: {missing}")
    generated = [case for case in cases if (case.get("metadata") or {}).get("generated_from") == "knowledge/entities.tsv"]
    if len(generated) < 25:
        raise RuntimeError(f"Expected generated canonical-identifier coverage, found only {len(generated)} cases")
    print(f"Haunted suite materialization: {len(cases)} cases ({len(generated)} generated identifier cases)")
    print("Compiling Java benchmark source set...")
    subprocess.run([str(REPO / "gradlew"), "--no-daemon", "benchmarkCheck"], cwd=REPO, check=True)
    print("Benchmark self-test passed without provider calls.")


def list_suites() -> None:
    descriptions = {
        "smoke": "small real-model HauntedMC sanity suite",
        "haunted": "generated + authored full-product HauntedMC cases",
        "holdout": "private gitignored local holdout cases",
        "replays": "sanitized gitignored production regressions",
        "longmemeval": "LongMemEval-S cleaned, adapted to AIlex memory retrieval/reasoning",
        "longmemeval-oracle": "LongMemEval oracle evidence sessions, native-routing diagnostic",
        "memoryagentbench": "official exact/substr core MemoryAgentBench subsets",
        "standard": "HauntedMC + LongMemEval-S + relevant MemoryAgentBench",
        "extended": "HauntedMC + LongMemEval-M + relevant MemoryAgentBench; potentially very large",
    }
    for name in SUITES:
        print(f"{name:20} {descriptions[name]}")


def estimate_command(args: argparse.Namespace) -> None:
    cases = filtered_cases(args)
    estimate = estimate_cases(cases, judge_mode="auto")
    print_estimate(estimate)


def run_command(args: argparse.Namespace) -> None:
    refuse_ci()
    require_key()
    cases = filtered_cases(args)
    if not cases:
        raise RuntimeError("No benchmark cases matched the filters")
    estimate = estimate_cases(cases, args.judge)
    print_estimate(estimate)
    if args.max_cost is not None:
        cost = estimate.get("estimated_usd")
        if cost is None:
            raise RuntimeError(
                "--max-cost requires benchmark/local/pricing.json; copy benchmark/pricing.example.json locally and update it"
            )
        if float(cost) > args.max_cost:
            raise RuntimeError(f"Conservative estimate ${cost:.2f} exceeds --max-cost ${args.max_cost:.2f}")

    run_id = args.run_id or time.strftime("%Y%m%d-%H%M%S") + f"-{args.suite}"
    run_dir = RESULTS / run_id
    run_dir.mkdir(parents=True, exist_ok=False)
    suite_path = run_dir / "suite.jsonl"
    write_jsonl(suite_path, cases)
    request = {
        "repository_root": str(REPO),
        "suite_path": str(suite_path),
        "output_dir": str(run_dir),
        "run_id": run_id,
        "limit": 0,
        "repeat": max(1, args.repeat),
        "category": "",
        "case_id": "",
        "overrides": parse_overrides(args.set),
    }
    request_path = run_dir / "request.json"
    request_path.write_text(json.dumps(request, indent=2), encoding="utf-8")
    subprocess.run([
        str(REPO / "gradlew"),
        "--no-daemon",
        "benchmarkRun",
        f"-PbenchmarkRequest={request_path.resolve()}",
    ], cwd=REPO, check=True)

    rows = load_jsonl(run_dir / "results.jsonl")
    if args.judge != "none":
        rows = judge_rows(rows, args.judge, args.judge_model)
        write_jsonl(run_dir / "results.jsonl", rows)
    summary = write_report(run_dir, rows)
    print(json.dumps(summary, indent=2, ensure_ascii=False))
    update_latest(run_dir)

    if args.official and any((row.get("metadata") or {}).get("benchmark") == "LongMemEval" for row in rows):
        score_longmemeval(run_dir, CACHE)
    if args.ragchecker:
        run_ragchecker(run_dir)
    if args.inspect:
        run_inspect(REPO, run_dir)
    print(f"Report: {run_dir / 'report.html'}")


def v2_setup_command(args: argparse.Namespace) -> None:
    refuse_ci()
    python = ensure_v2_environment()
    data_root = Path(args.data_root).expanduser().resolve() if args.data_root else default_v2_data_root()
    setup_longmemeval_v2(
        CACHE,
        REPO,
        python=python,
        data_root=data_root,
        download_data=args.download_data,
    )
    print(f"LongMemEval-V2 environment: {python}")
    print(f"LongMemEval-V2 data root: {data_root}")


def v2_run_command(args: argparse.Namespace) -> None:
    refuse_ci()
    require_key()
    python = v2_python()
    if not python.is_file():
        raise RuntimeError("LongMemEval-V2 environment is missing; run ./bench v2-setup first")
    data_root = Path(args.data_root).expanduser().resolve() if args.data_root else default_v2_data_root()
    run_id = args.run_id or time.strftime("%Y%m%d-%H%M%S") + f"-longmemeval-v2-{args.tier}"
    output_dir = RESULTS / run_id
    if output_dir.exists():
        raise RuntimeError(f"Benchmark run already exists: {output_dir}")
    domains = ["web", "enterprise"] if args.domain == "both" else [args.domain]
    run_longmemeval_v2(
        CACHE,
        REPO,
        python,
        data_root,
        output_dir,
        args.tier,
        domains,
        max(0, args.limit),
        args.reader_model,
        args.reader_base_url,
        args.evaluator_model,
    )
    print(f"LongMemEval-V2 metrics: {output_dir / 'combined_metrics.json'}")


def ensure_v2_environment() -> Path:
    setup_upstreams(CACHE, REPO)
    python = v2_python()
    if not python.is_file():
        subprocess.run([sys.executable, "-m", "venv", str(V2_VENV)], check=True)
    pip = [str(python), "-m", "pip"]
    subprocess.run(pip + ["install", "--upgrade", "pip", "setuptools", "wheel"], check=True)
    upstream = CACHE / "upstream/LongMemEval-V2"
    subprocess.run(pip + ["install", "-e", str(upstream)], check=True)
    return python


def v2_python() -> Path:
    return V2_VENV / "bin/python"


def default_v2_data_root() -> Path:
    return (CACHE / "longmemeval-v2/data").resolve()


def filtered_cases(args: argparse.Namespace) -> list[dict[str, Any]]:
    cases = materialize(REPO, args.suite, CACHE)
    if args.category:
        cases = [case for case in cases if str(case.get("category") or "").lower() == args.category.lower()]
    if args.case_id:
        cases = [case for case in cases if case.get("id") == args.case_id]
    if args.sample and args.sample > 0 and len(cases) > args.sample:
        rng = random.Random(42)
        cases = rng.sample(cases, args.sample)
        cases.sort(key=lambda case: str(case.get("id")))
    if args.limit and args.limit > 0:
        cases = cases[: args.limit]
    return cases


def judge_rows(rows: list[dict[str, Any]], mode: str, model: str | None) -> list[dict[str, Any]]:
    judged_rows = []
    for index, row in enumerate(rows, start=1):
        should_judge = bool(row.get("expected_answer")) and (
            mode == "all" or (mode == "auto" and row.get("suite") in {"haunted", "holdout", "replays"})
        )
        if should_judge:
            print(f"judge [{index}/{len(rows)}] {row.get('id')}")
            try:
                row["judge"] = judge_result(row, model)
            except RuntimeError as exception:
                # Semantic judges are diagnostic supplements; retain the completed live result when one is unavailable.
                row["judge"] = {"judged": False, "error": str(exception)}
                print(f"judge [{index}/{len(rows)}] unavailable: {exception}")
        judged_rows.append(row)
    return judged_rows


def estimate_cases(cases: list[dict[str, Any]], judge_mode: str) -> dict[str, Any]:
    user_turns = sum(
        sum(1 for turn in case.get("turns", []) if str(turn.get("role", "user")).lower() != "assistant")
        for case in cases
    )
    input_tokens = user_turns * 13_500
    output_tokens = user_turns * 1_250
    judged = sum(1 for case in cases if case.get("expect", {}).get("answer") and (
        judge_mode == "all"
        or (judge_mode == "auto" and case.get("suite") in {"haunted", "holdout", "replays"})
    ))
    judge_input = judged * 1_500
    judge_output = judged * 250
    estimate: dict[str, Any] = {
        "cases": len(cases),
        "user_turns": user_turns,
        "conservative_generation_input_tokens": input_tokens,
        "conservative_generation_output_tokens": output_tokens,
        "semantic_judge_cases": judged,
        "conservative_judge_input_tokens": judge_input,
        "conservative_judge_output_tokens": judge_output,
        "estimated_usd": None,
    }
    pricing = load_pricing()
    if pricing:
        rates = list((pricing.get("models") or {}).values())
        if rates:
            max_input = max(float(rate.get("input_per_million", 0)) for rate in rates)
            max_output = max(float(rate.get("output_per_million", 0)) for rate in rates)
            estimate["estimated_usd"] = round(
                (input_tokens + judge_input) / 1_000_000 * max_input
                + (output_tokens + judge_output) / 1_000_000 * max_output,
                4,
            )
            estimate["pricing_note"] = "uses highest configured local model rates as a conservative ceiling"
    return estimate


def load_pricing() -> dict[str, Any] | None:
    path = LOCAL / "pricing.json"
    if not path.is_file():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def print_estimate(estimate: dict[str, Any]) -> None:
    print(json.dumps(estimate, indent=2))
    if estimate.get("estimated_usd") is None:
        print("USD estimate unavailable until benchmark/local/pricing.json is configured; token envelope is still shown.")


def parse_overrides(values: list[str]) -> dict[str, Any]:
    overrides: dict[str, Any] = {}
    for raw in values:
        if "=" not in raw:
            raise ValueError(f"Invalid --set value: {raw}; expected dotted.key=value")
        key, value = raw.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not key:
            raise ValueError("Config override key cannot be empty")
        lowered = value.lower()
        if lowered in {"true", "false"}:
            parsed: Any = lowered == "true"
        else:
            try:
                parsed = int(value)
            except ValueError:
                try:
                    parsed = float(value)
                except ValueError:
                    parsed = value
        overrides[key] = parsed
    return overrides


def compare_command(args: argparse.Namespace) -> None:
    comparison = compare_runs(resolve_run(args.left), resolve_run(args.right))
    print(json.dumps(comparison, indent=2, ensure_ascii=False))


def view_command(args: argparse.Namespace) -> None:
    run_dir = resolve_run(args.run)
    report = run_dir / "report.html"
    if not report.is_file():
        rows = load_jsonl(run_dir / "results.jsonl")
        if not rows:
            raise RuntimeError(f"No results found in {run_dir}")
        write_report(run_dir, rows)
    print(report)
    webbrowser.open(report.resolve().as_uri())


def resolve_run(value: str) -> Path:
    if value == "latest":
        latest = RESULTS / "latest"
        if latest.is_symlink():
            return latest.resolve()
        marker = RESULTS / "LATEST"
        if marker.is_file():
            return RESULTS / marker.read_text(encoding="utf-8").strip()
        raise RuntimeError("No latest benchmark run exists")
    candidate = Path(value)
    if candidate.is_dir():
        return candidate.resolve()
    candidate = RESULTS / value
    if candidate.is_dir():
        return candidate.resolve()
    raise RuntimeError(f"Benchmark run not found: {value}")


def update_latest(run_dir: Path) -> None:
    RESULTS.mkdir(parents=True, exist_ok=True)
    (RESULTS / "LATEST").write_text(run_dir.name, encoding="utf-8")


def require_key() -> None:
    if not os.environ.get("OPENAI_API_KEY", "").strip():
        raise RuntimeError(
            "Set OPENAI_API_KEY in your shell; the key is never read from the repository or written to results"
        )


def refuse_ci() -> None:
    if os.environ.get("CI", "").strip().lower() == "true":
        raise RuntimeError("Live AIlex benchmarks are local-only and refuse to run when CI=true")


def command_output(command: list[str], stderr: bool = False) -> str:
    try:
        completed = subprocess.run(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT if stderr else subprocess.PIPE,
            text=True,
            check=False,
        )
        return completed.stdout or ""
    except OSError:
        return ""


if __name__ == "__main__":
    main()
