package nl.hauntedmc.ailex.assistant.infrastructure.memory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight personalized-PageRank style associative retrieval over the currently visible memory set. The graph is
 * derived from explicit subjects, relations, keys and tags; it never invents inferred social or psychological edges.
 */
public final class MemoryGraphRetriever {

    private static final double DAMPING = 0.85D;
    private static final int ITERATIONS = 8;
    private static final int MAX_NODES = 256;

    public Map<String, Double> graphScores(List<MemoryRecord> records, Map<String, Double> seedScores) {
        if (records == null || records.isEmpty() || seedScores == null || seedScores.isEmpty()) {
            return Map.of();
        }
        List<MemoryRecord> nodes = records.stream().limit(MAX_NODES).toList();
        Map<String, Map<String, Double>> adjacency = buildAdjacency(nodes);
        Map<String, Double> personalization = normalizeSeeds(nodes, seedScores);
        if (personalization.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> rank = new LinkedHashMap<>(personalization);
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            Map<String, Double> next = new LinkedHashMap<>();
            for (MemoryRecord node : nodes) {
                next.put(node.id(), (1.0D - DAMPING) * personalization.getOrDefault(node.id(), 0.0D));
            }
            for (MemoryRecord source : nodes) {
                double sourceRank = rank.getOrDefault(source.id(), 0.0D);
                Map<String, Double> edges = adjacency.getOrDefault(source.id(), Map.of());
                double outgoing = edges.values().stream().mapToDouble(Double::doubleValue).sum();
                if (outgoing <= 0.0D) {
                    continue;
                }
                for (Map.Entry<String, Double> edge : edges.entrySet()) {
                    next.merge(edge.getKey(), DAMPING * sourceRank * edge.getValue() / outgoing, Double::sum);
                }
            }
            rank = next;
        }
        double maximum = rank.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0D);
        if (maximum <= 0.0D) {
            return Map.of();
        }
        Map<String, Double> normalized = new LinkedHashMap<>();
        rank.forEach((id, value) -> normalized.put(id, Math.clamp(value / maximum, 0.0D, 1.0D)));
        return Map.copyOf(normalized);
    }

    private Map<String, Map<String, Double>> buildAdjacency(List<MemoryRecord> records) {
        Map<String, Map<String, Double>> adjacency = new HashMap<>();
        for (int leftIndex = 0; leftIndex < records.size(); leftIndex++) {
            MemoryRecord left = records.get(leftIndex);
            for (int rightIndex = leftIndex + 1; rightIndex < records.size(); rightIndex++) {
                MemoryRecord right = records.get(rightIndex);
                double weight = edgeWeight(left, right);
                if (weight <= 0.0D) {
                    continue;
                }
                adjacency.computeIfAbsent(left.id(), ignored -> new HashMap<>()).put(right.id(), weight);
                adjacency.computeIfAbsent(right.id(), ignored -> new HashMap<>()).put(left.id(), weight);
            }
        }
        return adjacency;
    }

    private double edgeWeight(MemoryRecord left, MemoryRecord right) {
        double weight = 0.0D;
        if (!left.subjectId().isBlank() && left.subjectId().equals(right.subjectId())) {
            weight += 0.65D;
        }
        if (!left.relationId().isBlank() && left.relationId().equals(right.relationId())) {
            weight += 0.75D;
        }
        if (!left.relationId().isBlank() && left.relationId().equals(right.subjectId())
                || !right.relationId().isBlank() && right.relationId().equals(left.subjectId())) {
            weight += 1.0D;
        }
        if (left.key().equals(right.key())) {
            weight += 0.80D;
        }
        weight += 0.70D * jaccard(left.tags(), right.tags());
        weight += 0.45D * jaccard(terms(left.key() + ' ' + left.value()), terms(right.key() + ' ' + right.value()));
        if (left.kind() == MemoryKind.EVENT && right.kind() == MemoryKind.EPISODE
                || left.kind() == MemoryKind.EPISODE && right.kind() == MemoryKind.EVENT) {
            weight += 0.25D;
        }
        return Math.min(3.0D, weight);
    }

    private Map<String, Double> normalizeSeeds(List<MemoryRecord> records, Map<String, Double> raw) {
        Set<String> allowed = records.stream().map(MemoryRecord::id).collect(java.util.stream.Collectors.toSet());
        List<Map.Entry<String, Double>> strongest = raw.entrySet().stream()
                .filter(entry -> allowed.contains(entry.getKey()) && entry.getValue() > 0.0D)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(12)
                .toList();
        double total = strongest.stream().mapToDouble(Map.Entry::getValue).sum();
        if (total <= 0.0D) {
            return Map.of();
        }
        Map<String, Double> normalized = new LinkedHashMap<>();
        strongest.forEach(entry -> normalized.put(entry.getKey(), entry.getValue() / total));
        return normalized;
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0.0D;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 0.0D : (double) intersection.size() / union.size();
    }

    private Set<String> terms(String value) {
        Set<String> result = new HashSet<>();
        if (value == null) {
            return result;
        }
        for (String token : value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 3) {
                result.add(token);
            }
        }
        return result;
    }
}
