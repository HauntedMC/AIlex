from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from typing import Any

RESPONSES_URL = "https://api.openai.com/v1/responses"


def judge_result(result: dict[str, Any], model: str | None = None) -> dict[str, Any]:
    expected = str(result.get("expected_answer") or "").strip()
    answer = str(result.get("answer") or "").strip()
    question = str(result.get("question") or "").strip()
    if not expected:
        return {"judged": False, "reason": "no semantic reference"}
    key = os.environ.get("OPENAI_API_KEY", "").strip()
    if not key:
        raise RuntimeError("OPENAI_API_KEY is required for semantic benchmark judging")
    judge_model = model or os.environ.get("AILEX_BENCH_JUDGE_MODEL", "gpt-5.6-sol")
    schema = {
        "type": "json_schema",
        "name": "ailex_benchmark_judgment",
        "strict": True,
        "schema": {
            "type": "object",
            "properties": {
                "correct": {"type": "boolean"},
                "complete": {"type": "boolean"},
                "natural": {"type": "boolean"},
                "score": {"type": "integer", "minimum": 0, "maximum": 4},
                "reason": {"type": "string"},
            },
            "required": ["correct", "complete", "natural", "score", "reason"],
            "additionalProperties": False,
        },
    }
    prompt = (
        "Evaluate a Minecraft community assistant answer. Judge semantic correctness against the reference, not exact wording. "
        "Do not reward claims unsupported by the reference. A concise answer can be complete. Score 4=fully correct and useful, "
        "3=correct with a minor omission, 2=partially correct, 1=mostly incorrect, 0=wrong or harmful.\n\n"
        f"Question:\n{question}\n\nReference expectation:\n{expected}\n\nAssistant answer:\n{answer}"
    )
    payload = {
        "model": judge_model,
        "store": False,
        "reasoning": {"effort": "medium"},
        "instructions": "Return only the requested JSON judgment. Be strict and concise.",
        "input": [{"role": "user", "content": [{"type": "input_text", "text": prompt}]}],
        "text": {"format": schema},
        "max_output_tokens": 250,
    }
    request = urllib.request.Request(
        RESPONSES_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            body = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"OpenAI judge request failed ({exc.code}): {detail[:500]}") from exc
    text = _output_text(body)
    try:
        judgment = json.loads(text)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"Judge returned invalid JSON: {text[:500]}") from exc
    usage = body.get("usage") or {}
    judgment.update({
        "judged": True,
        "model": judge_model,
        "usage": {
            "input_tokens": int(usage.get("input_tokens") or 0),
            "output_tokens": int(usage.get("output_tokens") or 0),
            "total_tokens": int(usage.get("total_tokens") or 0),
        },
    })
    return judgment


def _output_text(body: dict[str, Any]) -> str:
    direct = body.get("output_text")
    if isinstance(direct, str) and direct.strip():
        return direct.strip()
    for item in body.get("output") or []:
        if item.get("type") != "message":
            continue
        for part in item.get("content") or []:
            if part.get("type") == "output_text" and str(part.get("text") or "").strip():
                return str(part["text"]).strip()
    return ""
