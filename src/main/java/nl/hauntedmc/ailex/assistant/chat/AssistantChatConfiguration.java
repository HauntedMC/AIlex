package nl.hauntedmc.ailex.assistant.chat;

import nl.hauntedmc.ailex.assistant.runtime.PlayerResponseRateLimiter;
import nl.hauntedmc.ailex.assistant.runtime.context.ChatContextStore;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Typed, bounded configuration view for the Paper chat adapter. */
public final class AssistantChatConfiguration {

    private static final String CHAT = "openai.chat";
    private static final String CONTEXT = "openai.chat_context";
    private final Supplier<FileConfiguration> configSupplier;

    public AssistantChatConfiguration(Supplier<FileConfiguration> configSupplier) {
        this.configSupplier = configSupplier;
    }

    public boolean mayUse(Player player) {
        FileConfiguration config = config();
        if (config != null && !config.getBoolean(CHAT + ".enabled", true)) {
            return false;
        }
        String permission = string(CHAT + ".access_permission", "");
        return permission.isBlank() || player.hasPermission(permission);
    }

    public int maximumConcurrentRequests() {
        return integer(CHAT + ".max_concurrent_requests", 4, 1, 32);
    }

    public int maximumQueuedRequests() {
        return integer(CHAT + ".max_queued_requests", 8, 1, 64);
    }

    public long sessionTimeoutMillis() {
        return TimeUnit.SECONDS.toMillis(integer(CHAT + ".session_timeout_seconds", 300, 30, 1_800));
    }

    /**
     * Whether an active dialogue may consume a later player chat line that does not mention the assistant again.
     * Disabled by default so ordinary player conversation can never be silently routed into an AI request.
     */
    public boolean allowImplicitFollowUps() {
        return bool(CHAT + ".allow_implicit_followups", false);
    }

    public String responseVisibility() {
        return visibility(string(CHAT + ".response_visibility", "requester"));
    }

    public int nearbyResponseRadius() {
        return integer(CHAT + ".nearby_response_radius", 32, 1, 128);
    }

    public String feedback(String key, String fallback) {
        return string(CHAT + ".feedback." + key, fallback);
    }

    public boolean standaloneEnabled() {
        return bool(CHAT + ".standalone.enabled", false);
    }

    public AssistantChatTarget standaloneTarget() {
        return AssistantChatTarget.standalone(
                string(CHAT + ".standalone.mention", "AIlex"),
                string(CHAT + ".standalone.display_name", "AIlex"),
                string(CHAT + ".standalone.system_prompt", "")
        );
    }

    public PlayerResponseRateLimiter.ResponseRateLimit responseRateLimit() {
        boolean enabled = bool("openai.rate_limit.enabled", true);
        int maxResponses = integer("openai.rate_limit.max_responses_per_player", 10, 1, 1000);
        long window = TimeUnit.SECONDS.toMillis(integer("openai.rate_limit.window_seconds", 600, 1, 86_400));
        return new PlayerResponseRateLimiter.ResponseRateLimit(enabled, maxResponses, window);
    }

    public boolean bypassRateLimit(Player player) {
        if (player == null) {
            return false;
        }
        if (bool("openai.rate_limit.bypass_operators", true) && player.isOp()) {
            return true;
        }
        String permission = string("openai.rate_limit.bypass_permission", "ailex.rate_limit.bypass");
        return !permission.isBlank() && player.hasPermission(permission);
    }

    public boolean rateLimitFeedbackEnabled() {
        return bool("openai.rate_limit.feedback.enabled", true);
    }

    public String rateLimitFeedback() {
        return string(
                "openai.rate_limit.feedback.message",
                "Je hebt de AI-limiet bereikt. Probeer het over {remaining_seconds} seconden opnieuw."
        );
    }

    public ChatContextStore.ContextSettings contextSettings() {
        return new ChatContextStore.ContextSettings(
                bool(CONTEXT + ".enabled", true),
                bool(CONTEXT + ".persist_to_disk", false),
                integer(CONTEXT + ".max_message_characters", 900, 80, 2_000),
                bool(CONTEXT + ".include_timestamps", true),
                string(CONTEXT + ".timestamp_format", "HH:mm:ss"),
                history("general_chat", 160, 3_600, 4_000),
                history("conversation", 80, 21_600, 8_000),
                history("bot_memory", 100, 21_600, 5_000),
                integer(CONTEXT + ".max_context_characters", 18_000, 1_000, 32_000)
        );
    }

    private ChatContextStore.HistorySettings history(
            String section,
            int defaultMessages,
            int defaultAgeSeconds,
            int defaultContextCharacters
    ) {
        String path = CONTEXT + '.' + section;
        return new ChatContextStore.HistorySettings(
                bool(path + ".enabled", true),
                integer(path + ".max_messages", defaultMessages, 1, 500),
                TimeUnit.SECONDS.toMillis(integer(path + ".max_age_seconds", defaultAgeSeconds, 10, 86_400)),
                integer(path + ".max_context_characters", defaultContextCharacters, 100, 16_000)
        );
    }

    private String visibility(String value) {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "server", "nearby", "requester" -> value.toLowerCase(java.util.Locale.ROOT);
            default -> "requester";
        };
    }

    private boolean bool(String path, boolean fallback) {
        FileConfiguration config = config();
        return config == null ? fallback : config.getBoolean(path, fallback);
    }

    private int integer(String path, int fallback, int minimum, int maximum) {
        FileConfiguration config = config();
        int value = config == null ? fallback : config.getInt(path, fallback);
        return Math.clamp(value, minimum, maximum);
    }

    private String string(String path, String fallback) {
        FileConfiguration config = config();
        String value = config == null ? fallback : config.getString(path, fallback);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private FileConfiguration config() {
        return configSupplier == null ? null : configSupplier.get();
    }
}
