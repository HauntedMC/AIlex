package nl.hauntedmc.ailex.benchmark;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Deterministic benchmark checks for hard product invariants and exact-match external metrics. */
final class BenchmarkScorer {

    private BenchmarkScorer() {
    }

    static Score score(JsonObject benchmarkCase, String answer, Set<String> evidenceIds, String handoff) {
        JsonObject expect = object(benchmarkCase, "expect");
        if (expect == null) {
            return new Score(true, 0, 0, List.of());
        }
        String normalizedAnswer = normalize(answer);
        Set<String> normalizedEvidence = new HashSet<>();
        for (String evidenceId : evidenceIds == null ? Set.<String>of() : evidenceIds) {
            normalizedEvidence.add(evidenceId.toLowerCase(Locale.ROOT));
        }
        List<String> failures = new ArrayList<>();
        int checks = 0;
        int passed = 0;

        for (String required : strings(expect, "contains")) {
            checks++;
            if (normalizedAnswer.contains(normalize(required))) {
                passed++;
            } else {
                failures.add("missing text: " + required);
            }
        }
        for (String forbidden : strings(expect, "not_contains")) {
            checks++;
            if (!normalizedAnswer.contains(normalize(forbidden))) {
                passed++;
            } else {
                failures.add("forbidden text: " + forbidden);
            }
        }
        for (String required : strings(expect, "evidence_all")) {
            checks++;
            if (normalizedEvidence.contains(required.toLowerCase(Locale.ROOT))) {
                passed++;
            } else {
                failures.add("missing evidence: " + required);
            }
        }
        List<String> anyEvidence = strings(expect, "evidence_any");
        if (!anyEvidence.isEmpty()) {
            checks++;
            boolean found = anyEvidence.stream()
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .anyMatch(normalizedEvidence::contains);
            if (found) {
                passed++;
            } else {
                failures.add("none of expected evidence ids present: " + String.join(", ", anyEvidence));
            }
        }
        if (expect.has("abstain") && expect.get("abstain").isJsonPrimitive()) {
            checks++;
            boolean expected = expect.get("abstain").getAsBoolean();
            boolean actual = handoff != null && !handoff.isBlank();
            if (actual == expected) {
                passed++;
            } else {
                failures.add("abstention expected=" + expected + " actual=" + actual);
            }
        }
        if (expect.has("exact") && expect.get("exact").isJsonPrimitive()) {
            checks++;
            String expected = normalize(expect.get("exact").getAsString());
            if (normalizedAnswer.equals(expected)) {
                passed++;
            } else {
                failures.add("exact answer mismatch");
            }
        }
        if (expect.has("substring_exact") && expect.get("substring_exact").isJsonPrimitive()) {
            checks++;
            String expected = normalize(expect.get("substring_exact").getAsString());
            if (!expected.isBlank() && normalizedAnswer.contains(expected)) {
                passed++;
            } else {
                failures.add("substring exact-match failed");
            }
        }
        return new Score(failures.isEmpty(), passed, checks, List.copyOf(failures));
    }

    private static JsonObject object(JsonObject object, String name) {
        return object != null && object.has(name) && object.get(name).isJsonObject()
                ? object.getAsJsonObject(name) : null;
    }

    private static List<String> strings(JsonObject object, String name) {
        if (object == null || !object.has(name) || !object.get(name).isJsonArray()) {
            return List.of();
        }
        JsonArray array = object.getAsJsonArray(name);
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                String value = element.getAsString().trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return List.copyOf(values);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    record Score(boolean passed, int passedChecks, int totalChecks, List<String> failures) {
    }
}
