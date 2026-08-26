package nl.hauntedmc.ailex.assistant.infrastructure.live;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Thread-safe registry for trusted read-only HauntedMC context providers. */
public final class AssistantContextProviderRegistry {

    private static final int MAX_PROVIDER_FACTS = 32;
    private static final int MAX_TOTAL_CHARACTERS = 6_000;
    private final Map<String, AssistantContextProvider> providers = new ConcurrentHashMap<>();

    public void register(AssistantContextProvider provider) {
        if (provider == null || provider.id() == null || provider.id().isBlank()) {
            throw new IllegalArgumentException("Context provider and stable id are required");
        }
        String id = normalizeId(provider.id());
        AssistantContextProvider previous = providers.putIfAbsent(id, provider);
        if (previous != null && previous != provider) {
            throw new IllegalStateException("An AIlex context provider is already registered as " + id);
        }
    }

    public void unregister(String providerId) {
        if (providerId != null) {
            providers.remove(normalizeId(providerId));
        }
    }

    public int size() {
        return providers.size();
    }

    /** Collects compact provider-qualified metadata for the current request. */
    public String collect(Player player, String playerMessage) {
        if (player == null || providers.isEmpty()) {
            return "";
        }
        StringBuilder output = new StringBuilder();
        List<Map.Entry<String, AssistantContextProvider>> ordered = providers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        int facts = 0;
        for (Map.Entry<String, AssistantContextProvider> entry : ordered) {
            List<AssistantContextProvider.ContextFact> collected;
            try {
                collected = entry.getValue().collect(player, playerMessage);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (collected == null || collected.isEmpty()) {
                continue;
            }
            for (AssistantContextProvider.ContextFact fact : collected) {
                if (fact == null || !fact.valid() || facts >= MAX_PROVIDER_FACTS) {
                    continue;
                }
                String part = "integration_" + entry.getKey().replace('.', '_') + '_' + fact.key() + '=' + fact.value();
                if (output.length() + part.length() + 3 > MAX_TOTAL_CHARACTERS) {
                    return output.toString();
                }
                if (!output.isEmpty()) {
                    output.append(" | ");
                }
                output.append(part);
                facts++;
            }
        }
        return output.toString();
    }

    public List<String> providerIds() {
        return providers.keySet().stream().sorted().collect(Collectors.toUnmodifiableList());
    }

    private String normalizeId(String value) {
        String safe = value.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("-+", "-");
        if (safe.isBlank() || safe.length() > 80) {
            throw new IllegalArgumentException("Invalid AIlex context provider id");
        }
        return safe;
    }
}
