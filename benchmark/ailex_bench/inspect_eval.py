from __future__ import annotations

import json
import os
from pathlib import Path

from inspect_ai import Task, task
from inspect_ai.dataset import MemoryDataset, Sample
from inspect_ai.model import ModelOutput
from inspect_ai.scorer import model_graded_qa
from inspect_ai.solver import Generate, Solver, TaskState, solver


@solver
def use_ailex_output() -> Solver:
    async def solve(state: TaskState, generate: Generate) -> TaskState:
        answer = str((state.metadata or {}).get("ailex_answer") or "")
        state.output = ModelOutput.from_content("ailex", answer)
        return state

    return solve


@task
def ailex_saved_results() -> Task:
    result_path = os.environ.get("AILEX_BENCH_RESULTS", "").strip()
    if not result_path:
        raise RuntimeError("AILEX_BENCH_RESULTS must point to a benchmark results.jsonl file")
    samples: list[Sample] = []
    for line in Path(result_path).read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        row = json.loads(line)
        target = str(row.get("expected_answer") or "").strip()
        if not target:
            continue
        samples.append(Sample(
            id=f"{row.get('id')}:{row.get('repetition', 1)}",
            input=str(row.get("question") or ""),
            target=target,
            metadata={
                "ailex_answer": str(row.get("answer") or ""),
                "suite": row.get("suite"),
                "category": row.get("category"),
            },
        ))
    if not samples:
        raise RuntimeError("No benchmark rows with reference answers are available for Inspect")
    return Task(
        dataset=MemoryDataset(samples=samples, name="ailex-saved-results"),
        solver=use_ailex_output(),
        scorer=model_graded_qa(),
    )
