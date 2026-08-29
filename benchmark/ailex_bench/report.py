from __future__ import annotations

import html
import json
import math
import statistics
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.is_file():
        return []
    rows: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            rows.append(json.loads(line))
    return rows


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")


def summarize(rows: list[dict[str, Any]]) -> dict[str, Any]:
    hard_rows = [row for row in rows if int(row.get("hard_checks_total") or 0) > 0]
    judged = [row for row in rows if (row.get("judge") or {}).get("judged")]
    latencies = [int(row.get("latency_ms") or 0) for row in rows]
    usage = Counter()
    official = defaultdict(lambda: [0, 0])
    categories = defaultdict(lambda: {
        "cases": 0, "hard_evaluated": 0, "hard_pass": 0, "judged": 0, "judge_score": 0
    })
    failures = Counter()

    for row in rows:
        category = str(row.get("category") or "uncategorized")
        categories[category]["cases"] += 1
        if int(row.get("hard_checks_total") or 0) > 0:
            categories[category]["hard_evaluated"] += 1
            if row.get("hard_pass"):
                categories[category]["hard_pass"] += 1
        judge = row.get("judge") or {}
        if judge.get("judged"):
            categories[category]["judged"] += 1
            categories[category]["judge_score"] += int(judge.get("score") or 0)
        for key, value in (row.get("usage") or {}).items():
            if isinstance(value, (int, float)):
                usage[key] += value
        metric = str(row.get("official_metric") or "")
        expected = str(row.get("expected_answer") or "").strip()
        answer = str(row.get("answer") or "").strip()
        if metric in {"exact_match", "substring_exact_match"} and expected:
            official[metric][1] += 1
            normalized_answer = normalize(answer)
            normalized_expected = normalize(expected)
            passed = normalized_answer == normalized_expected if metric == "exact_match" else normalized_expected in normalized_answer
            if passed:
                official[metric][0] += 1
        if not row.get("hard_pass") or (judge.get("judged") and not judge.get("correct")):
            failures[classify_failure(row)] += 1

    summary = {
        "cases": len(rows),
        "hard": {
            "evaluated": len(hard_rows),
            "passed": sum(1 for row in hard_rows if row.get("hard_pass")),
            "failed": sum(1 for row in hard_rows if not row.get("hard_pass")),
        },
        "semantic_judge": {
            "evaluated": len(judged),
            "correct": sum(1 for row in judged if (row.get("judge") or {}).get("correct")),
            "mean_score_0_4": round(statistics.mean([int((row.get("judge") or {}).get("score") or 0) for row in judged]), 3) if judged else None,
        },
        "official_local_metrics": {
            key: {"passed": value[0], "total": value[1], "rate": round(value[0] / value[1], 4) if value[1] else None}
            for key, value in official.items()
        },
        "latency_ms": {
            "p50": percentile(latencies, 50),
            "p95": percentile(latencies, 95),
            "mean": round(statistics.mean(latencies), 1) if latencies else None,
        },
        "usage": dict(usage),
        "failure_taxonomy": dict(failures),
        "categories": dict(sorted(categories.items())),
    }
    return summary


def classify_failure(row: dict[str, Any]) -> str:
    answer = str(row.get("answer") or "").strip()
    if not answer:
        return "delivery/liveness"
    failures = " ".join(str(value) for value in row.get("hard_failures") or []).lower()
    if "missing evidence" in failures:
        expected_ids = [part.split(":", 1)[-1].strip() for part in row.get("hard_failures") or [] if "missing evidence" in part]
        retrieved_ids = {str(chunk.get("doc_id") or "") for chunk in row.get("retrieved_context") or []}
        if expected_ids and not all(identifier in retrieved_ids for identifier in expected_ids):
            return "retrieval"
        return "grounding"
    if row.get("handoff") and row.get("expected_answer"):
        return "selection/context-or-grounding"
    judge = row.get("judge") or {}
    if judge.get("judged") and not judge.get("correct"):
        return "generation"
    if failures:
        return "product-invariant"
    return "unknown"


def write_report(run_dir: Path, rows: list[dict[str, Any]]) -> dict[str, Any]:
    summary = summarize(rows)
    (run_dir / "summary.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
    report = [
        "<!doctype html><html><head><meta charset='utf-8'><title>AIlex benchmark</title>",
        "<style>body{font-family:system-ui,sans-serif;max-width:1200px;margin:2rem auto;padding:0 1rem}"
        "table{border-collapse:collapse;width:100%}th,td{padding:.45rem;border-bottom:1px solid #ddd;text-align:left}"
        ".pass{color:#087f23}.fail{color:#b42318}code,pre{white-space:pre-wrap}</style></head><body>",
        "<h1>AIlex benchmark</h1>",
        f"<pre>{html.escape(json.dumps(summary, indent=2, ensure_ascii=False))}</pre>",
        "<h2>Cases</h2><table><thead><tr><th>Case</th><th>Category</th><th>Hard</th><th>Judge</th><th>Latency</th><th>Answer</th></tr></thead><tbody>",
    ]
    for row in rows:
        hard = "N/A" if int(row.get("hard_checks_total") or 0) == 0 else "PASS" if row.get("hard_pass") else "FAIL"
        judge = row.get("judge") or {}
        judge_text = "-" if not judge.get("judged") else f"{judge.get('score')}/4 {'✓' if judge.get('correct') else '✗'}"
        report.append(
            "<tr>"
            f"<td>{html.escape(str(row.get('id') or ''))}</td>"
            f"<td>{html.escape(str(row.get('category') or ''))}</td>"
                f"<td class={'pass' if hard == 'PASS' else 'fail' if hard == 'FAIL' else ''}>{hard}</td>"
            f"<td>{html.escape(judge_text)}</td>"
            f"<td>{int(row.get('latency_ms') or 0)} ms</td>"
            f"<td>{html.escape(str(row.get('answer') or ''))}</td>"
            "</tr>"
        )
    report.append("</tbody></table></body></html>")
    (run_dir / "report.html").write_text("".join(report), encoding="utf-8")
    return summary


def compare_runs(left_dir: Path, right_dir: Path) -> dict[str, Any]:
    left_rows = load_jsonl(left_dir / "results.jsonl")
    right_rows = load_jsonl(right_dir / "results.jsonl")
    left = summarize(left_rows)
    right = summarize(right_rows)
    paired_left = {(row.get("id"), row.get("repetition")): row for row in left_rows}
    paired_right = {(row.get("id"), row.get("repetition")): row for row in right_rows}
    common = sorted(set(paired_left) & set(paired_right), key=str)
    hard_delta = []
    judge_delta = []
    latency_delta = []
    regressions: list[str] = []
    improvements: list[str] = []
    for key in common:
        old = paired_left[key]
        new = paired_right[key]
        hard_delta.append(int(bool(new.get("hard_pass"))) - int(bool(old.get("hard_pass"))))
        old_judge = old.get("judge") or {}
        new_judge = new.get("judge") or {}
        if old_judge.get("judged") and new_judge.get("judged"):
            judge_delta.append(int(new_judge.get("score") or 0) - int(old_judge.get("score") or 0))
        latency_delta.append(int(new.get("latency_ms") or 0) - int(old.get("latency_ms") or 0))
        if old.get("hard_pass") and not new.get("hard_pass"):
            regressions.append(str(key[0]))
        elif not old.get("hard_pass") and new.get("hard_pass"):
            improvements.append(str(key[0]))
    return {
        "left": left,
        "right": right,
        "paired_cases": len(common),
        "paired_hard_pass_delta": sum(hard_delta),
        "mean_judge_score_delta": round(statistics.mean(judge_delta), 3) if judge_delta else None,
        "median_latency_delta_ms": statistics.median(latency_delta) if latency_delta else None,
        "hard_regressions": regressions,
        "hard_improvements": improvements,
    }


def percentile(values: list[int], percentile_value: int) -> int | None:
    if not values:
        return None
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, math.ceil(percentile_value / 100 * len(ordered)) - 1))
    return ordered[index]


def normalize(value: str) -> str:
    return " ".join(value.lower().split())
