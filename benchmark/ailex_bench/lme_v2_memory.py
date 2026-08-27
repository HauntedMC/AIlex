from __future__ import annotations

import json
import re
import shutil
import subprocess
import tempfile
import threading
from pathlib import Path
from typing import Any, Iterable

from memory_modules.memory import Memory, MemoryContextItem, register_memory

_SKIP_FIELDS = {
    "base64",
    "image",
    "image_path",
    "images",
    "screenshot",
    "screenshot_path",
    "screenshots",
}


@register_memory
class AIlexTextMemory(Memory):
    """LongMemEval-V2 backend using AIlex's Java event-memory store/retriever for text trajectory evidence."""

    memory_type = "ailex_text"

    def __init__(self, memory_params: dict[str, object]) -> None:
        super().__init__(memory_params)
        repository_root = str(memory_params.get("repository_root") or "").strip()
        if not repository_root:
            raise RuntimeError("ailex_text requires memory_params.repository_root")
        self.repository_root = Path(repository_root).expanduser().resolve()
        if not (self.repository_root / "gradlew").is_file():
            raise RuntimeError(f"AIlex repository_root is invalid: {self.repository_root}")
        self.max_results = max(1, min(96, int(memory_params.get("max_results") or 24)))
        self.chunk_characters = max(120, min(300, int(memory_params.get("chunk_characters") or 300)))
        self.batch_size = max(16, min(512, int(memory_params.get("batch_size") or 256)))
        workspace_root_value = str(memory_params.get("workspace_root") or "").strip()
        workspace_root = (
            Path(workspace_root_value).expanduser().resolve()
            if workspace_root_value
            else self.repository_root / "benchmark/.cache/longmemeval-v2/bridge"
        )
        workspace_root.mkdir(parents=True, exist_ok=True)
        self.workspace = Path(tempfile.mkdtemp(prefix="ailex-text-", dir=workspace_root))
        self._lock = threading.Lock()
        self._closed = False
        self._sequence = 0
        self._ignored_query_images = 0
        self._process = subprocess.Popen(
            [
                str(self.repository_root / "gradlew"),
                "--no-daemon",
                "-q",
                "benchmarkV2Bridge",
                f"-PbenchmarkBridgeWorkspace={self.workspace}",
            ],
            cwd=self.repository_root,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            bufsize=1,
        )
        self._read_bridge_response(require_ready=True)

    def __del__(self) -> None:
        try:
            self.close()
        except Exception:
            pass

    def insert(self, trajectory: dict[str, object]) -> None:
        trajectory_id = str(trajectory.get("id") or f"trajectory-{self._sequence}")
        batch: list[dict[str, Any]] = []
        for field_path, text in _iter_text(trajectory):
            source = f"trajectory={trajectory_id} field={field_path} {text}"
            for chunk in _chunk_text(source, self.chunk_characters):
                batch.append({
                    "content": chunk,
                    "trajectory_id": trajectory_id,
                    "occurred_at_epoch_ms": 1_700_000_000_000 + self._sequence,
                })
                self._sequence += 1
                if len(batch) >= self.batch_size:
                    self._request({"op": "insert_batch", "items": batch})
                    batch = []
        if batch:
            self._request({"op": "insert_batch", "items": batch})

    def query(self, query: str, query_image: str | None = None) -> list[MemoryContextItem]:
        if query_image:
            self._ignored_query_images += 1
        response = self._request({"op": "query", "query": query, "max_results": self.max_results})
        raw_items = response.get("items") or []
        items: list[MemoryContextItem] = []
        for item in raw_items:
            if not isinstance(item, dict) or item.get("type") != "text":
                continue
            value = str(item.get("value") or "").strip()
            if value:
                items.append({"type": "text", "value": value})
        return items

    def post_query_hook(
        self,
        *,
        query: str,
        query_image: str | None,
        memory_context: list[MemoryContextItem],
    ) -> dict[str, object] | None:
        return {
            "backend": "ailex_text",
            "text_only": True,
            "query_image_ignored": bool(query_image),
            "returned_items": len(memory_context),
        }

    def close(self) -> None:
        if self._closed:
            return
        self._closed = True
        try:
            if self._process.poll() is None:
                self._request({"op": "close"}, allow_closed=True)
        except (BrokenPipeError, RuntimeError):
            pass
        try:
            self._process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            self._process.terminate()
            try:
                self._process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self._process.kill()
        shutil.rmtree(self.workspace, ignore_errors=True)

    def _request(self, payload: dict[str, Any], *, allow_closed: bool = False) -> dict[str, Any]:
        if self._closed and not allow_closed:
            raise RuntimeError("AIlex LongMemEval-V2 memory bridge is closed")
        if self._process.stdin is None:
            raise RuntimeError("AIlex LongMemEval-V2 memory bridge stdin is unavailable")
        with self._lock:
            if self._process.poll() is not None:
                raise RuntimeError(f"AIlex LongMemEval-V2 memory bridge exited with {self._process.returncode}")
            self._process.stdin.write(json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n")
            self._process.stdin.flush()
            response = self._read_bridge_response(require_ready=False)
        if not response.get("ok", False):
            raise RuntimeError(str(response.get("error") or "AIlex memory bridge request failed"))
        return response

    def _read_bridge_response(self, *, require_ready: bool) -> dict[str, Any]:
        if self._process.stdout is None:
            raise RuntimeError("AIlex LongMemEval-V2 memory bridge stdout is unavailable")
        while True:
            line = self._process.stdout.readline()
            if not line:
                raise RuntimeError(
                    f"AIlex LongMemEval-V2 memory bridge ended before responding (exit={self._process.poll()})"
                )
            try:
                payload = json.loads(line)
            except json.JSONDecodeError:
                continue
            if not isinstance(payload, dict) or payload.get("bridge") is not True:
                continue
            if require_ready and payload.get("ready") is not True:
                continue
            return payload


def _iter_text(value: Any, path: str = "trajectory") -> Iterable[tuple[str, str]]:
    if isinstance(value, dict):
        for key in sorted(value):
            normalized_key = str(key).lower()
            if normalized_key in _SKIP_FIELDS or "screenshot" in normalized_key or normalized_key.endswith("_image"):
                continue
            yield from _iter_text(value[key], f"{path}.{key}")
        return
    if isinstance(value, (list, tuple)):
        for index, item in enumerate(value):
            yield from _iter_text(item, f"{path}[{index}]")
        return
    if not isinstance(value, str):
        return
    clean = re.sub(r"\s+", " ", value).strip()
    if len(clean) < 2 or clean.startswith("data:image/"):
        return
    yield path, clean


def _chunk_text(text: str, maximum: int) -> Iterable[str]:
    clean = re.sub(r"\s+", " ", text).strip()
    while clean:
        if len(clean) <= maximum:
            yield clean
            return
        cut = clean.rfind(" ", 0, maximum + 1)
        if cut < maximum // 2:
            cut = maximum
        yield clean[:cut].strip()
        clean = clean[cut:].strip()
