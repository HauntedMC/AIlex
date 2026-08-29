from __future__ import annotations

import hashlib
import json
import re
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

LONGMEMEVAL_URLS = {
    "longmemeval": "https://huggingface.co/datasets/xiaowu0162/longmemeval-cleaned/resolve/main/longmemeval_s_cleaned.json",
    "longmemeval-s": "https://huggingface.co/datasets/xiaowu0162/longmemeval-cleaned/resolve/main/longmemeval_s_cleaned.json",
    "longmemeval-oracle": "https://huggingface.co/datasets/xiaowu0162/longmemeval-cleaned/resolve/main/longmemeval_oracle.json",
    "longmemeval-m": "https://huggingface.co/datasets/xiaowu0162/longmemeval-cleaned/resolve/main/longmemeval_m_cleaned.json",
}
SUPPORTED_MEMORYAGENT_METRICS = {"exact_match", "substring_exact_match"}


def materialize(repo: Path, name: str, cache: Path) -> list[dict[str, Any]]:
    normalized = name.strip().lower()
    if normalized in {"haunted", "smoke"}:
        cases = haunted_cases(repo)
        return smoke_cases(cases) if normalized == "smoke" else cases
    if normalized in {"holdout", "replays"}:
        return local_cases(repo, normalized)
    if normalized in LONGMEMEVAL_URLS:
        return longmemeval_cases(normalized, cache)
    if normalized == "memoryagentbench":
        return memoryagentbench_cases(cache)
    if normalized == "standard":
        return haunted_cases(repo) + longmemeval_cases("longmemeval-s", cache) + memoryagentbench_cases(cache)
    if normalized == "extended":
        return haunted_cases(repo) + longmemeval_cases("longmemeval-m", cache) + memoryagentbench_cases(cache)
    raise ValueError(f"Unknown benchmark suite: {name}")


def smoke_cases(cases: list[dict[str, Any]]) -> list[dict[str, Any]]:
    preferred = [
        "conversation-followup",
        "memory-preference-recall",
        "memory-correction-current",
        "memory-forget",
        "live-biome",
        "missing-server-evidence",
        "discord-explicit-aankondigingen",
    ]
    by_id = {case["id"]: case for case in cases}
    chosen = [by_id[item] for item in preferred if item in by_id]
    for case in cases:
        if len(chosen) >= 24:
            break
        if case not in chosen and case.get("category") in {"identifiers", "knowledge"}:
            chosen.append(case)
    return chosen[:24]


def local_cases(repo: Path, name: str) -> list[dict[str, Any]]:
    path = repo / "benchmark/local" / f"{name}.jsonl"
    if not path.is_file():
        raise FileNotFoundError(
            f"Local {name} suite does not exist: {path}. This file is intentionally gitignored."
        )
    cases: list[dict[str, Any]] = []
    for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not raw.strip():
            continue
        item = json.loads(raw)
        if not isinstance(item, dict):
            raise ValueError(f"{path}:{line_number} must contain a JSON object")
        if not str(item.get("id") or "").strip():
            raise ValueError(f"{path}:{line_number} is missing a stable id")
        item = dict(item)
        item.setdefault("suite", name)
        item.setdefault("category", "local")
        cases.append(item)
    return cases


def haunted_cases(repo: Path) -> list[dict[str, Any]]:
    cases = authored_haunted_cases()
    entities = repo / "src/main/resources/knowledge/entities.tsv"
    if not entities.is_file():
        raise FileNotFoundError(f"Canonical entity registry missing: {entities}")
    complete: set[str] = set()
    entries: list[tuple[str, str, list[str], str]] = []
    for raw in entities.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        columns = raw.split("\t")
        if columns[0].strip() == "@complete" and len(columns) > 1:
            complete.add(columns[1].strip())
            continue
        if len(columns) < 2:
            continue
        kind = columns[0].strip()
        canonical = columns[1].strip()
        aliases = [value.strip() for value in (columns[2] if len(columns) > 2 else "").split(",") if value.strip()]
        description = columns[3].strip() if len(columns) > 3 else ""
        entries.append((kind, canonical, aliases, description))

    for kind, canonical, aliases, description in entries:
        evidence = f"entity.{safe_id(kind)}.{safe_id(canonical)}"
        cases.append({
            "id": f"entity-exact-{safe_id(kind)}-{safe_id(canonical)}",
            "suite": "haunted",
            "category": "identifiers",
            "turns": [{"role": "user", "content": exact_identifier_question(kind, canonical)}],
            "expect": {
                "contains": [canonical],
                "evidence_all": [evidence],
                "answer": f"The exact canonical HauntedMC {kind} identifier is {canonical}. {description}".strip(),
            },
            "metadata": {"generated_from": "knowledge/entities.tsv", "kind": kind, "canonical": canonical},
        })
        natural_aliases = [alias for alias in aliases if normalize(alias) != normalize(canonical)]
        if natural_aliases:
            alias = natural_aliases[0]
            cases.append({
                "id": f"entity-alias-{safe_id(kind)}-{safe_id(canonical)}",
                "suite": "haunted",
                "category": "identifiers",
                "turns": [{"role": "user", "content": alias_identifier_question(kind, alias)}],
                "expect": {
                    "contains": [canonical],
                    "evidence_all": [evidence],
                    "answer": f"Resolve the alias {alias!r} to the canonical HauntedMC {kind} identifier {canonical}.",
                },
                "metadata": {"generated_from": "knowledge/entities.tsv", "alias": alias, "canonical": canonical},
            })
        if kind in complete and kind == "discord-channel":
            explicit_alias = next((
                alias for alias in natural_aliases
                if re.fullmatch(r"[a-z0-9_-]+", alias.lower())
                and f"#{alias.lower()}" != canonical.lower()
            ), None)
            if explicit_alias:
                invalid = f"#{explicit_alias.lower()}"
                cases.append({
                    "id": f"entity-negative-{safe_id(invalid)}",
                    "suite": "haunted",
                    "category": "identifiers",
                    "turns": [{"role": "user", "content": f"Bestaat {invalid} op de HauntedMC Discord?"}],
                    "expect": {
                        "contains": [canonical],
                        "evidence_all": [evidence, f"entity.missing.discord-channel.{safe_id(invalid)}"],
                        "answer": f"{invalid} does not exist in the complete reviewed Discord-channel registry; the relevant canonical channel is {canonical}.",
                    },
                    "metadata": {"generated_from": "knowledge/entities.tsv", "negative_identifier": invalid},
                })
    return cases


def authored_haunted_cases() -> list[dict[str, Any]]:
    return [
        {
            "id": "conversation-followup",
            "suite": "haunted",
            "category": "conversation",
            "turns": [
                {"role": "user", "content": "AIlex, noem in één zin waarom claims handig zijn."},
                {"role": "user", "content": "Waarom precies?"},
            ],
            "expect": {"answer": "The second answer should naturally continue the first answer rather than act like an unrelated new conversation."},
        },
        {
            "id": "memory-preference-recall",
            "suite": "haunted",
            "category": "memory",
            "turns": [
                {"role": "user", "content": "Mijn favoriete gamemode is Creative."},
                {"role": "user", "content": "Wat weet je over mijn favoriete gamemode?"},
            ],
            "expect": {"contains": ["Creative"], "answer": "The player's stated favourite game mode is Creative."},
        },
        {
            "id": "memory-correction-current",
            "suite": "haunted",
            "category": "memory",
            "turns": [
                {"role": "user", "content": "Mijn favoriete gamemode is Creative."},
                {"role": "user", "content": "Eigenlijk is mijn favoriete gamemode Survival."},
                {"role": "user", "content": "Wat is nu mijn favoriete gamemode?"},
            ],
            "expect": {"contains": ["Survival"], "answer": "The current corrected favourite game mode is Survival, not Creative."},
        },
        {
            "id": "memory-forget",
            "suite": "haunted",
            "category": "memory",
            "turns": [
                {"role": "user", "content": "Mijn favoriete gamemode is Creative."},
                {"role": "user", "content": "Vergeet mijn favoriete gamemode."},
                {"role": "user", "content": "Wat is mijn favoriete gamemode?"},
            ],
            "expect": {
                "not_contains": ["Creative"],
                "answer": "AIlex should not present Creative as a currently remembered favourite after the explicit forget request.",
            },
        },
        {
            "id": "live-biome",
            "suite": "haunted",
            "category": "live-state",
            "turns": [{
                "role": "user",
                "content": "In welk biome ben ik nu?",
                "live_values": ["player_biome=PLAINS", "player_world=Survival"],
                "live_source_ids": ["live.world"],
            }],
            "expect": {"contains": ["PLAINS"], "evidence_all": ["live.world"], "answer": "The player is currently in the PLAINS biome."},
        },
        {
            "id": "missing-server-evidence",
            "suite": "haunted",
            "category": "abstention",
            "turns": [{"role": "user", "content": "Wat kost de geheime /dragonvault upgrade op HauntedMC?"}],
            "expect": {
                "abstain": True,
                "answer": "There is insufficient reviewed evidence for this invented HauntedMC feature; AIlex should abstain rather than invent a price or command.",
            },
        },
        {
            "id": "multilingual-identifier-preservation",
            "suite": "haunted",
            "category": "multilingual",
            "turns": [{"role": "user", "content": "Welchen Discord-Kanal nutze ich für Ankündigungen?"}],
            "expect": {"contains": ["#announcements"], "evidence_all": ["entity.discord-channel.announcements"], "answer": "The exact channel remains #announcements even in a German response."},
        },
        {
            "id": "discord-explicit-aankondigingen",
            "suite": "haunted",
            "category": "identifiers",
            "turns": [{"role": "user", "content": "Waar vind ik #aankondigingen?"}],
            "expect": {
                "contains": ["#announcements"],
                "evidence_all": ["entity.discord-channel.announcements", "entity.missing.discord-channel.aankondigingen"],
                "answer": "#aankondigingen is not a real registered channel; the canonical relevant channel is #announcements.",
            },
        },
    ]


def longmemeval_cases(name: str, cache: Path) -> list[dict[str, Any]]:
    url = LONGMEMEVAL_URLS[name]
    path = cache / "longmemeval" / Path(url).name
    download(url, path)
    payload = json.loads(path.read_text(encoding="utf-8"))
    cases: list[dict[str, Any]] = []
    for item in payload:
        question_id = str(item.get("question_id", len(cases)))
        fixture_path, fixture_hash = write_event_fixture(
            cache,
            "longmemeval",
            f"{name}-{question_id}",
            longmemeval_events(item),
        )
        cases.append({
            "id": f"longmemeval-{safe_id(question_id)}",
            "suite": "longmemeval",
            "category": str(item.get("question_type", "memory")),
            "intent_override": "EVENT_RECALL",
            "seed_events_file": str(fixture_path),
            "turns": [{"role": "user", "content": str(item.get("question", ""))}],
            "expect": {"answer": str(item.get("answer", ""))},
            "metadata": {
                "benchmark": "LongMemEval",
                "dataset_variant": name,
                "question_id": question_id,
                "question_type": item.get("question_type", ""),
                "question_date": item.get("question_date", ""),
                "answer_session_ids": item.get("answer_session_ids", []),
                "history_fixture_sha256": fixture_hash,
                "official_metric": "official_llm_judge",
                "adapter_protocol": "trusted timestamped event ingestion into AIlex memory; retrieval/reasoning evaluation",
            },
        })
    return cases


def longmemeval_events(item: dict[str, Any]) -> Iterable[dict[str, Any]]:
    sessions = item.get("haystack_sessions") or []
    session_ids = item.get("haystack_session_ids") or []
    dates = item.get("haystack_dates") or []
    question_date = str(item.get("question_date") or "")
    question_time = parse_longmemeval_date(question_date)
    now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
    fallback_sequence = 0
    for session_index, session in enumerate(sessions):
        session_id = session_ids[session_index] if session_index < len(session_ids) else str(session_index)
        date = str(dates[session_index]) if session_index < len(dates) else ""
        session_time = parse_longmemeval_date(date)
        if question_time is not None and session_time is not None:
            base_time = now_ms + int((session_time - question_time).total_seconds() * 1000)
        else:
            base_time = 1_700_000_000_000 + session_index * 100_000
        for turn_index, turn in enumerate(session or []):
            role = str(turn.get("role", "unknown"))
            content = str(turn.get("content", ""))
            prefix = f"session={session_id} date={date} {role}: "
            for chunk_index, chunk in enumerate(chunk_text(prefix + content, 300)):
                occurred_at = base_time + turn_index * 1000 + chunk_index
                if session_time is None:
                    occurred_at += fallback_sequence
                    fallback_sequence += 1
                yield {"content": chunk, "occurred_at_epoch_ms": occurred_at}


def memoryagentbench_cases(cache: Path) -> list[dict[str, Any]]:
    try:
        from datasets import load_dataset  # type: ignore
    except ImportError as exc:
        raise RuntimeError("MemoryAgentBench requires ./bench setup (the optional datasets package is missing)") from exc
    dataset = load_dataset("ai-hyz/MemoryAgentBench")
    cases: list[dict[str, Any]] = []
    for split_name, split in dataset.items():
        for row_index, row in enumerate(split):
            context = str(row.get("context") or "")
            metadata = object_value(row.get("metadata"))
            source = str(metadata.get("source") or "")
            metric = memoryagent_metric(source, split_name)
            if metric not in SUPPORTED_MEMORYAGENT_METRICS:
                continue
            questions = list_value(row.get("questions"))
            answers = list_value(row.get("answers"))
            qa_ids = list_value(metadata.get("qa_pair_ids"))
            fixture_path, fixture_hash = write_event_fixture(
                cache,
                "memoryagentbench",
                f"{split_name}-{row_index}",
                memoryagent_events(context),
            )
            for question_index, question in enumerate(questions):
                accepted = answers[question_index] if question_index < len(answers) else []
                if isinstance(accepted, str):
                    accepted = [accepted]
                elif not isinstance(accepted, list):
                    accepted = list(accepted) if accepted is not None else []
                expected = str(accepted[0]) if accepted else ""
                qa_id = str(qa_ids[question_index]) if question_index < len(qa_ids) else f"{row_index}-{question_index}"
                expect: dict[str, Any] = {"answer": expected}
                if metric == "exact_match":
                    expect["exact"] = expected
                else:
                    expect["substring_exact"] = expected
                cases.append({
                    "id": f"memoryagentbench-{safe_id(split_name)}-{safe_id(qa_id)}",
                    "suite": "memoryagentbench",
                    "category": split_name,
                    "intent_override": "EVENT_RECALL",
                    "seed_events_file": str(fixture_path),
                    "turns": [{"role": "user", "content": str(question)}],
                    "expect": expect,
                    "metadata": {
                        "benchmark": "MemoryAgentBench",
                        "split": split_name,
                        "source": source,
                        "qa_pair_id": qa_id,
                        "acceptable_answers": list(accepted),
                        "history_fixture_sha256": fixture_hash,
                        "official_metric": metric,
                        "adapter_protocol": "trusted incremental-context ingestion into AIlex memory; retrieval/reasoning evaluation",
                    },
                })
    return cases


def memoryagent_events(context: str) -> Iterable[dict[str, Any]]:
    for index, chunk in enumerate(chunk_text(context, 300)):
        yield {"content": chunk, "occurred_at_epoch_ms": 1_700_000_000_000 + index}


def memoryagent_metric(source: str, split_name: str) -> str:
    token = f"{source} {split_name}".lower().replace("-", "_")
    if any(name in token for name in ("event_qa", "ruler_qa1", "ruler_qa2", "fact_mh", "fact_sh")):
        return "substring_exact_match"
    if any(name in token for name in ("detective", "icl_banking", "icl_clinic", "icl_nlu", "icl_trec")):
        return "exact_match"
    if "recsys" in token or "recommend" in token:
        return "recall_at_5"
    if "longmemeval" in token:
        return "llm_judge"
    return "semantic"


def write_event_fixture(
    cache: Path,
    namespace: str,
    hint: str,
    events: Iterable[dict[str, Any]],
) -> tuple[Path, str]:
    directory = cache / "fixtures" / safe_id(namespace)
    directory.mkdir(parents=True, exist_ok=True)
    temporary = directory / f".{safe_id(hint)}.part"
    digest = hashlib.sha256()
    with temporary.open("w", encoding="utf-8") as output:
        for event in events:
            line = json.dumps(event, ensure_ascii=False, separators=(",", ":"))
            encoded = (line + "\n").encode("utf-8")
            digest.update(encoded)
            output.write(line + "\n")
    fixture_hash = digest.hexdigest()
    destination = directory / f"{fixture_hash}.jsonl"
    if destination.is_file():
        temporary.unlink(missing_ok=True)
    else:
        temporary.replace(destination)
    return destination.resolve(), fixture_hash


def parse_longmemeval_date(value: str) -> datetime | None:
    text = str(value or "").strip()
    for pattern in ("%Y/%m/%d (%a) %H:%M UTC", "%Y/%m/%d (%a) %H:%M"):
        try:
            parsed = datetime.strptime(text, pattern)
            return parsed.replace(tzinfo=timezone.utc)
        except ValueError:
            continue
    return None


def exact_identifier_question(kind: str, canonical: str) -> str:
    if kind == "discord-channel":
        return f"Hoe heet het HauntedMC Discord-kanaal {canonical} exact?"
    if kind == "command":
        return f"Wat is het exacte HauntedMC command {canonical}?"
    if kind == "rank":
        return f"Hoe schrijf je de HauntedMC rank {canonical} exact?"
    if kind == "game-mode":
        return f"Hoe heet de HauntedMC gamemode {canonical} exact?"
    return f"Wat is de exacte HauntedMC identifier {canonical}?"


def alias_identifier_question(kind: str, alias: str) -> str:
    if kind == "discord-channel":
        return f"Welk exact Discord-kanaal bedoel ik met {alias}?"
    if kind == "command":
        return f"Welk exact HauntedMC command hoort bij {alias}?"
    if kind == "rank":
        return f"Welke exacte HauntedMC rank bedoel ik met {alias}?"
    if kind == "game-mode":
        return f"Welke exacte HauntedMC gamemode bedoel ik met {alias}?"
    return f"Welke exacte identifier hoort bij {alias}?"


def chunk_text(text: str, maximum: int) -> Iterable[str]:
    clean = re.sub(r"\s+", " ", text).strip()
    while clean:
        if len(clean) <= maximum:
            yield clean
            break
        cut = clean.rfind(" ", 0, maximum + 1)
        if cut < maximum // 2:
            cut = maximum
        yield clean[:cut].strip()
        clean = clean[cut:].strip()


def download(url: str, path: Path) -> None:
    if path.is_file() and path.stat().st_size > 0:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".part")
    with urllib.request.urlopen(url, timeout=120) as response, temporary.open("wb") as output:
        output.write(response.read())
    temporary.replace(path)


def object_value(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        return dict(value)
    if isinstance(value, str) and value.strip():
        parsed = json.loads(value)
        return dict(parsed) if isinstance(parsed, dict) else {}
    return {}


def list_value(value: Any) -> list[Any]:
    if isinstance(value, list):
        return list(value)
    if isinstance(value, tuple):
        return list(value)
    if isinstance(value, str) and value.strip():
        try:
            parsed = json.loads(value)
            return list(parsed) if isinstance(parsed, list) else [value]
        except json.JSONDecodeError:
            return [value]
    return []


def safe_id(value: str) -> str:
    normalized = normalize(value)
    normalized = re.sub(r"[^a-z0-9._-]+", "-", normalized)
    normalized = re.sub(r"-+", "-", normalized).strip("-")
    return normalized[:80] or "unknown"


def normalize(value: str) -> str:
    return re.sub(r"\s+", " ", str(value)).strip().lower()
