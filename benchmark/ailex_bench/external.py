from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

from .report import load_jsonl
from .suites import LONGMEMEVAL_URLS, download

LONGMEMEVAL_REPO = "https://github.com/xiaowu0162/LongMemEval.git"
LONGMEMEVAL_V2_REPO = "https://github.com/xiaowu0162/LongMemEval-V2.git"


def ensure_checkout(cache: Path, name: str, url: str) -> Path:
    target = cache / "upstream" / name
    if target.is_dir() and (target / ".git").is_dir():
        return target
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        shutil.rmtree(target)
    subprocess.run(["git", "clone", "--depth", "1", url, str(target)], check=True)
    return target


def setup_upstreams(cache: Path, repo: Path | None = None) -> None:
    ensure_checkout(cache, "LongMemEval", LONGMEMEVAL_REPO)
    ensure_checkout(cache, "LongMemEval-V2", LONGMEMEVAL_V2_REPO)
    if repo is not None:
        install_longmemeval_v2_adapter(cache, repo)
    print("Upstream benchmark repositories are available under benchmark/.cache/upstream")


def score_longmemeval(run_dir: Path, cache: Path, model: str = "gpt-4o") -> Path:
    rows = [
        row for row in load_jsonl(run_dir / "results.jsonl")
        if (row.get("metadata") or {}).get("benchmark") == "LongMemEval"
    ]
    if not rows:
        raise RuntimeError("Run contains no LongMemEval results")
    if not os.environ.get("OPENAI_API_KEY", "").strip():
        raise RuntimeError("OPENAI_API_KEY is required by the official LongMemEval LLM judge")
    variants = {
        str((row.get("metadata") or {}).get("dataset_variant") or "longmemeval-s")
        for row in rows
    }
    if len(variants) != 1:
        raise RuntimeError(
            f"Official LongMemEval scoring expects one dataset variant per run, found: {sorted(variants)}"
        )
    variant = variants.pop()
    url = LONGMEMEVAL_URLS.get(variant)
    if not url:
        raise RuntimeError(f"Unknown LongMemEval variant: {variant}")
    dataset_path = cache / "longmemeval" / Path(url).name
    download(url, dataset_path)
    upstream = ensure_checkout(cache, "LongMemEval", LONGMEMEVAL_REPO)
    hypothesis = run_dir / "longmemeval-hypotheses.jsonl"
    with hypothesis.open("w", encoding="utf-8") as output:
        for row in rows:
            metadata = row.get("metadata") or {}
            output.write(json.dumps({
                "question_id": metadata.get("question_id"),
                "hypothesis": row.get("answer") or "",
            }, ensure_ascii=False) + "\n")
    evaluation_dir = upstream / "src/evaluation"
    subprocess.run(
        [sys.executable, "evaluate_qa.py", model, str(hypothesis), str(dataset_path)],
        cwd=evaluation_dir,
        check=True,
    )
    log_path = Path(str(hypothesis) + ".log")
    print(f"Official LongMemEval evaluation log: {log_path}")
    return log_path


def write_ragchecker_input(run_dir: Path) -> Path:
    results = []
    for row in load_jsonl(run_dir / "results.jsonl"):
        expected = str(row.get("expected_answer") or "").strip()
        contexts = row.get("retrieved_context") or []
        if not expected or not contexts:
            continue
        results.append({
            "query_id": str(row.get("id") or ""),
            "query": str(row.get("question") or ""),
            "gt_answer": expected,
            "response": str(row.get("answer") or ""),
            "retrieved_context": [
                {"doc_id": str(chunk.get("doc_id") or ""), "text": str(chunk.get("text") or "")}
                for chunk in contexts
            ],
        })
    if not results:
        raise RuntimeError("Run has no results with both a reference answer and retrieved context")
    path = run_dir / "ragchecker-input.json"
    path.write_text(json.dumps({"results": results}, indent=2, ensure_ascii=False), encoding="utf-8")
    return path


def run_ragchecker(run_dir: Path, model: str | None = None) -> Path:
    executable = shutil.which("ragchecker-cli")
    if executable is None:
        raise RuntimeError("ragchecker-cli is not installed; run ./bench setup --ragchecker")
    input_path = write_ragchecker_input(run_dir)
    output_path = run_dir / "ragchecker-output.json"
    checker_model = model or os.environ.get("AILEX_RAGCHECKER_MODEL", "gpt-4o")
    subprocess.run([
        executable,
        f"--input_path={input_path}",
        f"--output_path={output_path}",
        f"--extractor_name={checker_model}",
        f"--checker_name={checker_model}",
        "--batch_size_extractor=16",
        "--batch_size_checker=16",
        "--metrics",
        "all_metrics",
    ], check=True)
    print(f"RAGChecker output: {output_path}")
    return output_path


def run_inspect(repo: Path, run_dir: Path, model: str | None = None) -> None:
    executable = shutil.which("inspect")
    if executable is None:
        raise RuntimeError("Inspect is not installed; run ./bench setup")
    judge_model = model or os.environ.get("AILEX_INSPECT_MODEL", "openai/gpt-5.6-sol")
    env = dict(os.environ)
    env["AILEX_BENCH_RESULTS"] = str((run_dir / "results.jsonl").resolve())
    subprocess.run([
        executable,
        "eval",
        str(repo / "benchmark/ailex_bench/inspect_eval.py"),
        "--model",
        judge_model,
        "--log-dir",
        str((run_dir / "inspect-logs").resolve()),
    ], cwd=repo, env=env, check=True)
    print(f"Inspect logs: {run_dir / 'inspect-logs'}")


def install_longmemeval_v2_adapter(cache: Path, repo: Path) -> Path:
    upstream = ensure_checkout(cache, "LongMemEval-V2", LONGMEMEVAL_V2_REPO)
    shim_path = upstream / "memory_modules/ailex_text.py"
    benchmark_package = (repo / "benchmark").resolve()
    shim_path.write_text(
        "from pathlib import Path\n"
        "import sys\n\n"
        f"AILEX_BENCHMARK = Path({str(benchmark_package)!r})\n"
        "if str(AILEX_BENCHMARK) not in sys.path:\n"
        "    sys.path.insert(0, str(AILEX_BENCHMARK))\n\n"
        "from ailex_bench.lme_v2_memory import AIlexTextMemory  # noqa: F401,E402\n",
        encoding="utf-8",
    )

    memory_py = upstream / "memory_modules/memory.py"
    memory_source = memory_py.read_text(encoding="utf-8")
    adapter_import = "from .ailex_text import AIlexTextMemory  # noqa: E402,F401"
    if adapter_import not in memory_source:
        memory_py.write_text(memory_source.rstrip() + "\n" + adapter_import + "\n", encoding="utf-8")

    run_eval = upstream / "evaluation/run_eval.py"
    run_source = run_eval.read_text(encoding="utf-8")
    if '"ailex_text",' not in run_source:
        marker = 'METHODS = {\n    "no_retrieval",'
        if marker not in run_source:
            raise RuntimeError("Could not patch LongMemEval-V2 METHODS; upstream layout changed")
        run_source = run_source.replace(marker, 'METHODS = {\n    "ailex_text",\n    "no_retrieval",', 1)
    config_marker = 'def build_memory_config(args: argparse.Namespace, data_root: Path) -> dict[str, object]:\n'
    adapter_config = (
        config_marker
        + '    if args.method == "ailex_text":\n'
        + '        repository_root = os.environ.get("AILEX_REPOSITORY_ROOT", "").strip()\n'
        + '        if not repository_root:\n'
        + '            raise RuntimeError("AILEX_REPOSITORY_ROOT is required for ailex_text")\n'
        + '        return {\n'
        + '            "memory_type": "ailex_text",\n'
        + '            "memory_params": {\n'
        + '                "repository_root": repository_root,\n'
        + '                "workspace_root": os.environ.get("AILEX_V2_BRIDGE_ROOT", ""),\n'
        + '                "max_results": int(os.environ.get("AILEX_V2_MAX_RESULTS", "24")),\n'
        + '                "chunk_characters": 300,\n'
        + '                "batch_size": 256,\n'
        + '            },\n'
        + '        }\n'
    )
    if '"memory_type": "ailex_text"' not in run_source:
        if config_marker not in run_source:
            raise RuntimeError("Could not patch LongMemEval-V2 memory config; upstream layout changed")
        run_source = run_source.replace(config_marker, adapter_config, 1)
    run_eval.write_text(run_source, encoding="utf-8")
    return upstream


def setup_longmemeval_v2(
    cache: Path,
    repo: Path,
    python: Path | None = None,
    data_root: Path | None = None,
    download_data: bool = False,
) -> Path:
    upstream = install_longmemeval_v2_adapter(cache, repo)
    if download_data:
        if python is None:
            raise RuntimeError("A LongMemEval-V2 Python environment is required to download data")
        target = (data_root or cache / "longmemeval-v2/data").resolve()
        subprocess.run([
            str(python), str(upstream / "data/download_data.py"), "--data-root", str(target)
        ], cwd=upstream, check=True)
        subprocess.run([
            str(python), str(upstream / "data/prepare_data.py"), "--data-root", str(target), "--mode", "symlink"
        ], cwd=upstream, check=True)
        subprocess.run([
            str(python), str(upstream / "data/validate_data.py"), "--data-root", str(target), "--tier", "small"
        ], cwd=upstream, check=True)
    print("LongMemEval-V2 AIlex text-memory adapter is installed in the local upstream checkout.")
    return upstream


def run_longmemeval_v2(
    cache: Path,
    repo: Path,
    python: Path,
    data_root: Path,
    output_dir: Path,
    tier: str,
    domains: list[str],
    limit: int = 0,
    reader_model: str | None = None,
    reader_base_url: str | None = None,
    evaluator_model: str | None = None,
) -> Path:
    upstream = install_longmemeval_v2_adapter(cache, repo)
    data_root = data_root.expanduser().resolve()
    if not (data_root / "questions.jsonl").is_file() or not (data_root / "trajectories.jsonl").is_file():
        raise RuntimeError(f"LongMemEval-V2 data is incomplete: {data_root}; run ./bench v2-setup --download-data")
    output_dir = output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ)
    env["AILEX_REPOSITORY_ROOT"] = str(repo.resolve())
    env["AILEX_V2_BRIDGE_ROOT"] = str((cache / "longmemeval-v2/bridge").resolve())
    metrics: list[Path] = []
    for domain in domains:
        domain_output = output_dir / f"ailex_text_{domain}_{tier}"
        command = [
            str(python),
            str(upstream / "evaluation/run_eval.py"),
            "--method", "ailex_text",
            "--data-root", str(data_root),
            "--domain", domain,
            "--tier", tier,
            "--output-dir", str(domain_output),
        ]
        if limit > 0:
            command += ["--limit", str(limit)]
        if reader_model:
            command += ["--reader-model", reader_model]
        if reader_base_url:
            command += ["--reader-base-url", reader_base_url]
        if evaluator_model:
            command += ["--evaluator-model", evaluator_model]
        subprocess.run(command, cwd=upstream, env=env, check=True)
        metric_path = domain_output / "aggregated_metrics.json"
        if not metric_path.is_file():
            raise RuntimeError(f"LongMemEval-V2 did not produce aggregated metrics: {metric_path}")
        metrics.append(metric_path)

    combined = output_dir / "combined_metrics.json"
    if len(metrics) == 2:
        subprocess.run([
            str(python),
            str(upstream / "leaderboard/combine_aggregated_metrics.py"),
            str(metrics[0]),
            str(metrics[1]),
            "--output", str(combined),
        ], cwd=upstream, check=True)
    elif metrics:
        shutil.copyfile(metrics[0], combined)

    metadata = {
        "benchmark": "LongMemEval-V2",
        "method": "ailex_text",
        "tier": tier,
        "domains": domains,
        "limit": limit,
        "adapter": {
            "production_memory": "AssistantMemoryService",
            "text_only": True,
            "screenshots_ignored": True,
            "query_images_ignored": True,
            "upstream_harness": True,
            "leaderboard_comparable": False,
        },
        "note": (
            "This is an adapted LongMemEval-V2 run through the official harness using AIlex text memory. "
            "It intentionally does not claim multimodal leaderboard comparability."
        ),
    }
    (output_dir / "AILEX_ADAPTER.json").write_text(
        json.dumps(metadata, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    print(f"LongMemEval-V2 output: {output_dir}")
    return combined
