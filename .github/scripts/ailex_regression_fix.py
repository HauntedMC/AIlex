from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    if old not in text:
        raise SystemExit(f"missing patch anchor in {path}: {old[:100]!r}")
    file.write_text(text.replace(old, new, 1))


# A cold semantic query must be able to score documents immediately. Async warm-up is still retained for the normal
# startup path, while query-time filling makes the first semantic-only question correct and deterministic.
path = "src/main/java/nl/hauntedmc/ailex/assistant/infrastructure/knowledge/LocalKnowledgeIndex.java"
replace_once(
    path,
    """        warmSemanticIndexAsync();\n        List<double[]> queryVector = provider.embed(List.of(query));\n""",
    """        ensureSemanticVectors(candidates, provider);\n        warmSemanticIndexAsync();\n        List<double[]> queryVector = provider.embed(List.of(query));\n""",
)
replace_once(
    path,
    """    private void warmSemanticIndexAsync() {\n""",
    """    /** Ensures a cold query has document vectors instead of silently degrading a semantic-only request. */\n    private void ensureSemanticVectors(List<KnowledgeChunk> candidates, SemanticEmbeddingProvider provider) {\n        List<KnowledgeChunk> missing = candidates.stream()\n                .filter(chunk -> !semanticVectors.containsKey(chunk.id()))\n                .toList();\n        for (int offset = 0; offset < missing.size(); offset += 48) {\n            List<KnowledgeChunk> batch = missing.subList(offset, Math.min(missing.size(), offset + 48));\n            List<double[]> vectors = provider.embed(batch.stream().map(this::embeddingText).toList());\n            if (vectors.size() != batch.size()) {\n                return;\n            }\n            for (int index = 0; index < batch.size(); index++) {\n                double[] vector = vectors.get(index);\n                if (vector != null && vector.length > 0) {\n                    semanticVectors.putIfAbsent(batch.get(index).id(), vector);\n                }\n            }\n        }\n    }\n\n    private void warmSemanticIndexAsync() {\n""",
)

# Four factual relationship fields fit comfortably in eight results; keep the query aligned with the stable-key test.
replace_once(
    "src/main/java/nl/hauntedmc/ailex/assistant/infrastructure/memory/AssistantEventMemoryService.java",
    '                playerId, npcId, "interaction count", Set.of(MemoryKind.RELATIONSHIP), 16\n',
    '                playerId, npcId, "interaction count", Set.of(MemoryKind.RELATIONSHIP), 8\n',
)

# Prefer a concept that is present in both the event key and value. This rejects synthetic one-sided tokens such as
# section/build bookkeeping and gives repeated project events a stable semantic anchor (e.g. "castle").
path = "src/main/java/nl/hauntedmc/ailex/assistant/infrastructure/memory/AssistantMemoryConsolidator.java"
replace_once(
    path,
    """    private String primaryTopic(MemoryRecord record) {\n        String specificTag = record.tags().stream()\n                .filter(tag -> !Set.of(\"event\", \"session\", \"join\", \"quit\", \"world\", \"project\").contains(tag))\n                .filter(tag -> !tag.startsWith(\"world:\"))\n                .map(this::safeTopic)\n                .filter(tag -> !tag.isBlank())\n                .sorted()\n                .findFirst()\n                .orElse(\"\");\n        if (!specificTag.isBlank()) {\n            return specificTag;\n        }\n        String key = record.key();\n        int separator = key.indexOf('.');\n        return safeTopic(separator > 0 ? key.substring(0, separator) : key);\n    }\n""",
    """    private String primaryTopic(MemoryRecord record) {\n        Set<String> valueTerms = topicTerms(record.value());\n        String anchoredTopic = topicTerms(record.key()).stream()\n                .filter(valueTerms::contains)\n                .filter(tag -> !Set.of(\"event\", \"session\", \"join\", \"quit\", \"world\", \"project\").contains(tag))\n                .sorted()\n                .findFirst()\n                .orElse(\"\");\n        if (!anchoredTopic.isBlank()) {\n            return anchoredTopic;\n        }\n        String specificTag = record.tags().stream()\n                .filter(tag -> !Set.of(\"event\", \"session\", \"join\", \"quit\", \"world\", \"project\").contains(tag))\n                .filter(tag -> !tag.startsWith(\"world:\"))\n                .map(this::safeTopic)\n                .filter(tag -> !tag.isBlank())\n                .sorted()\n                .findFirst()\n                .orElse(\"\");\n        if (!specificTag.isBlank()) {\n            return specificTag;\n        }\n        String key = record.key();\n        int separator = key.indexOf('.');\n        return safeTopic(separator > 0 ? key.substring(0, separator) : key);\n    }\n\n    private Set<String> topicTerms(String value) {\n        if (value == null || value.isBlank()) {\n            return Set.of();\n        }\n        return java.util.Arrays.stream(value.toLowerCase(Locale.ROOT).split(\"[^a-z0-9]+\"))\n                .filter(term -> term.length() >= 3)\n                .map(this::safeTopic)\n                .collect(java.util.stream.Collectors.toUnmodifiableSet());\n    }\n""",
)

# Text blocks intentionally wrap long sentences; normalize whitespace before checking the semantic prompt invariant.
replace_once(
    "src/test/java/nl/hauntedmc/ailex/assistant/application/prompt/AssistantPromptComposerTest.java",
    """        assertTrue(prompt.contains(\"INTERACTION CONTRACT\"));\n        assertTrue(prompt.contains(\"procedural experience\"));\n        assertTrue(prompt.contains(\"Physical actions are proposals only\"));\n""",
    """        assertTrue(prompt.contains(\"INTERACTION CONTRACT\"));\n        String normalizedPrompt = prompt.replaceAll(\"\\\\s+\", \" \");\n        assertTrue(normalizedPrompt.contains(\"procedural experience\"));\n        assertTrue(normalizedPrompt.contains(\"Physical actions are proposals only\"));\n""",
)
