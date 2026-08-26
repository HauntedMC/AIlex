package nl.hauntedmc.ailex.assistant.infrastructure.live;

import nl.hauntedmc.ailex.assistant.security.AssistantDataSafety;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * Trusted read-only integration point for HauntedMC features that can expose player-safe current state to AIlex.
 * Providers must never return secrets, network addresses, hidden staff data, reports, sanctions or implementation internals.
 */
public interface AssistantContextProvider {

    /** Stable provider identifier, for example serverfeatures.economy or serverfeatures.combat. */
    String id();

    /**
     * Returns only facts relevant to the supplied player request. This method is called on the Paper server thread;
     * implementations should use already-loaded state and avoid blocking I/O.
     */
    List<ContextFact> collect(Player player, String playerMessage);

    /** A compact key/value fact that is validated before it can enter model context. */
    record ContextFact(String key, String value) {
        public ContextFact {
            key = sanitizeKey(key);
            value = sanitizeValue(value);
        }

        public boolean valid() {
            return !key.isBlank()
                    && !value.isBlank()
                    && !AssistantDataSafety.forbiddenLiveIntegration(key, value);
        }

        private static String sanitizeKey(String value) {
            String safe = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("[^a-z0-9._-]+", "_")
                    .replaceAll("_+", "_");
            return safe.length() <= 80 ? safe : safe.substring(0, 80);
        }

        private static String sanitizeValue(String value) {
            String safe = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ")
                    .replaceAll("\\s+", " ").trim();
            return safe.length() <= 500 ? safe : safe.substring(0, 499) + "…";
        }
    }
}
