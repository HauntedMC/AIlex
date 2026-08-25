package nl.hauntedmc.ailex.listener.llm;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.NPCHandler;
import nl.hauntedmc.ailex.npc.NPCProperties;
import nl.hauntedmc.ailex.util.FormatterUtils;
import nl.hauntedmc.ailex.util.LoggerUtils;
import nl.hauntedmc.ailex.ai.llm.ChatGPTClient;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Listener for chat events.
 * This listener observes player chat for NPC mentions and never overrides server chat rendering.
 */
public class LLMChatListener implements Listener {

    private static final String RATE_LIMIT_ENABLED_PATH = "openai.rate_limit.enabled";
    private static final String RATE_LIMIT_MAX_RESPONSES_PATH = "openai.rate_limit.max_responses_per_player";
    private static final String RATE_LIMIT_WINDOW_SECONDS_PATH = "openai.rate_limit.window_seconds";
    private static final int DEFAULT_MAX_RESPONSES_PER_PLAYER = 10;
    private static final long DEFAULT_RATE_LIMIT_WINDOW_SECONDS = 60L * 60L;
    private static final String CHAT_CONTEXT_PATH = "openai.chat_context";
    private static final int DEFAULT_CONTEXT_MESSAGE_MAX_CHARACTERS = 240;
    private static final int DEFAULT_GENERAL_CHAT_MAX_MESSAGES = 50;
    private static final long DEFAULT_GENERAL_CHAT_MAX_AGE_SECONDS = 60L * 60L;
    private static final int DEFAULT_CONVERSATION_MAX_MESSAGES = 20;
    private static final long DEFAULT_CONVERSATION_MAX_AGE_SECONDS = 2L * 60L * 60L;

    private final ChatGPTClient chatGPTClient;
    private final AIlexPlugin plugin;
    private final PlayerResponseRateLimiter responseRateLimiter;
    private final ChatContextStore chatContextStore;
    static final String PLACEHOLDER_PLAYER_NAME = "{player_name}";
    static final String PLACEHOLDER_PLAYER_DISPLAY_NAME = "{player_display_name}";
    static final String PLACEHOLDER_NPC_NAME = "{npc_name}";
    static final String PLACEHOLDER_NPC_DISPLAY_NAME = "{npc_display_name}";
    static final String PLACEHOLDER_CHAT_MESSAGE = "{chat_message}";

    /**
     * Constructor for the ChatListener
     * @param plugin the AIlex plugin
     */
    public LLMChatListener(AIlexPlugin plugin) {
        this.chatGPTClient = plugin.getChatGPTClient();
        this.plugin = plugin;
        this.responseRateLimiter = new PlayerResponseRateLimiter(this::getResponseRateLimit, System::currentTimeMillis);
        this.chatContextStore = new ChatContextStore(System::currentTimeMillis);
    }

    /**
     * Handle the chat event and forward player messages to AI when an NPC is mentioned.
     * @param event the chat event
     */
    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player source = event.getPlayer();
        Component message = event.message();
        if (event.isAsynchronous()) {
            Bukkit.getScheduler().runTask(plugin, () -> forwardChatToAI(source, message));
            return;
        }
        forwardChatToAI(source, message);
    }

    /**
     * Forward the chat message to the AI if an NPC is mentioned
     * @param source the chat message
     * @param message the chat message
     */
    void forwardChatToAI(Player source, Component message) {
        NPCHandler npcHandler = plugin.getNPCHandler();
        if (npcHandler == null) {
            return;
        }

        // Get the chat message from the component
        String chatMessage = PlainTextComponentSerializer.plainText().serialize(message);
        ChatContextStore.ContextSettings contextSettings = getChatContextSettings();

        // If an NPC is mentioned in the message forward chat to AI
        for (NPC npc : npcHandler.getNPCRegistry().values()) {
            if (!npc.isChatEnabled()) {
                continue;
            }

            String npcName = npc.getName();
            if (chatMessage.toLowerCase(Locale.ROOT).contains(npcName.toLowerCase(Locale.ROOT))) {
                if (!responseRateLimiter.tryAcquire(source.getUniqueId())) {
                    chatContextStore.recordGeneralChat(source.getName(), chatMessage, contextSettings);
                    return;
                }

                String npcDisplayName = npc.getDisplayName();
                String sourceName = source.getName();
                UUID sourceId = source.getUniqueId();
                int npcId = npc.getId();
                String systemPrompt = buildSystemPrompt(npc);
                String userPrompt = buildUserPrompt(npc, sourceName, chatMessage);
                String contextualPrompt = appendContext(
                        userPrompt,
                        source,
                        npc,
                        contextSettings
                );
                chatContextStore.recordGeneralChat(sourceName, chatMessage, contextSettings);
                chatContextStore.recordConversation(
                        sourceId,
                        npcId,
                        sourceName,
                        chatMessage,
                        contextSettings
                );
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            String response = chatGPTClient.getChatResponse(systemPrompt, contextualPrompt);

                            Component result = FormatterUtils.serializer.deserialize(npcDisplayName + ": ")
                                    .append(Component.text(response, NamedTextColor.WHITE));

                            if (!response.isEmpty()) {
                                chatContextStore.recordConversation(
                                        sourceId,
                                        npcId,
                                        npcName,
                                        response,
                                        contextSettings
                                );
                                Bukkit.getScheduler().runTask(plugin, () -> plugin.getServer().broadcast(result));
                            }
                        } catch (Exception e) {
                            LoggerUtils.logError(e.getMessage());
                        }
                    }
                }.runTaskAsynchronously(plugin);
                return;
            }
        }

        chatContextStore.recordGeneralChat(source.getName(), chatMessage, contextSettings);
    }

    private String appendContext(
            String userPrompt,
            Player source,
            NPC npc,
            ChatContextStore.ContextSettings contextSettings
    ) {
        if (!contextSettings.enabled()) {
            return userPrompt;
        }

        String chatContext = chatContextStore.buildContext(
                source.getUniqueId(),
                npc.getId(),
                npc.getName(),
                contextSettings
        );
        String metadata = buildMetadata(source, npc);
        if (chatContext.isBlank() && metadata.isBlank()) {
            return userPrompt;
        }

        StringBuilder prompt = new StringBuilder(userPrompt);
        if (!metadata.isBlank()) {
            prompt.append("\n\n[Huidige Minecraft-context]\n").append(metadata);
        }
        if (!chatContext.isBlank()) {
            prompt.append("\n\n").append(chatContext);
        }
        return prompt.toString();
    }

    private String buildMetadata(Player source, NPC npc) {
        FileConfiguration config = plugin.getConfig();
        if (config == null || !config.getBoolean(CHAT_CONTEXT_PATH + ".metadata.enabled", true)) {
            return "";
        }

        List<String> metadata = new java.util.ArrayList<>();
        World playerWorld = source.getWorld();
        Location playerLocation = source.getLocation();
        if (config.getBoolean(CHAT_CONTEXT_PATH + ".metadata.include_player_world", true)) {
            metadata.add("player_world=" + playerWorld.getName());
        }
        if (config.getBoolean(CHAT_CONTEXT_PATH + ".metadata.include_player_coordinates", true)) {
            metadata.add("player_pos=" + formatLocation(playerLocation));
        }
        if (config.getBoolean(CHAT_CONTEXT_PATH + ".metadata.include_player_game_mode", true)) {
            GameMode gameMode = source.getGameMode();
            metadata.add("player_gamemode=" + gameMode.name());
        }
        if (config.getBoolean(CHAT_CONTEXT_PATH + ".metadata.include_player_health", true)) {
            metadata.add("player_health=" + String.format(Locale.ROOT, "%.1f", source.getHealth()));
        }
        if (config.getBoolean(CHAT_CONTEXT_PATH + ".metadata.include_player_food_level", true)) {
            metadata.add("player_food=" + source.getFoodLevel());
        }
        if (config.getBoolean(CHAT_CONTEXT_PATH + ".metadata.include_world_time", true)) {
            metadata.add("world_time=" + playerWorld.getTime());
        }
        if (config.getBoolean(CHAT_CONTEXT_PATH + ".metadata.include_weather", true)) {
            metadata.add("weather=" + (playerWorld.hasStorm() ? "rain" : "clear"));
        }
        if (npc.getEntity() != null) {
            Location npcLocation = npc.getEntity().getLocation();
            World npcWorld = npcLocation.getWorld();
            if (npcWorld != null && config.getBoolean(CHAT_CONTEXT_PATH + ".metadata.include_npc_world", true)) {
                metadata.add("bot_world=" + npcWorld.getName());
            }
            if (config.getBoolean(CHAT_CONTEXT_PATH + ".metadata.include_npc_coordinates", true)) {
                metadata.add("bot_pos=" + formatLocation(npcLocation));
            }
        }
        return String.join(" | ", metadata);
    }

    private String formatLocation(Location location) {
        return String.format(Locale.ROOT, "%.0f,%.0f,%.0f", location.getX(), location.getY(), location.getZ());
    }

    private ChatContextStore.ContextSettings getChatContextSettings() {
        FileConfiguration config = plugin.getConfig();
        if (config == null) {
            return defaultChatContextSettings();
        }

        return new ChatContextStore.ContextSettings(
                config.getBoolean(CHAT_CONTEXT_PATH + ".enabled", true),
                Math.max(1, config.getInt(CHAT_CONTEXT_PATH + ".max_message_characters", DEFAULT_CONTEXT_MESSAGE_MAX_CHARACTERS)),
                config.getBoolean(CHAT_CONTEXT_PATH + ".include_timestamps", true),
                config.getString(CHAT_CONTEXT_PATH + ".timestamp_format", "HH:mm:ss"),
                getHistorySettings(config, "general_chat", DEFAULT_GENERAL_CHAT_MAX_MESSAGES, DEFAULT_GENERAL_CHAT_MAX_AGE_SECONDS),
                getHistorySettings(config, "conversation", DEFAULT_CONVERSATION_MAX_MESSAGES, DEFAULT_CONVERSATION_MAX_AGE_SECONDS)
        );
    }

    private ChatContextStore.HistorySettings getHistorySettings(
            FileConfiguration config,
            String historyName,
            int defaultMaxMessages,
            long defaultMaxAgeSeconds
    ) {
        String path = CHAT_CONTEXT_PATH + "." + historyName;
        return new ChatContextStore.HistorySettings(
                config.getBoolean(path + ".enabled", true),
                Math.max(1, config.getInt(path + ".max_messages", defaultMaxMessages)),
                TimeUnit.SECONDS.toMillis(Math.max(1L, config.getLong(path + ".max_age_seconds", defaultMaxAgeSeconds)))
        );
    }

    private ChatContextStore.ContextSettings defaultChatContextSettings() {
        return new ChatContextStore.ContextSettings(
                true,
                DEFAULT_CONTEXT_MESSAGE_MAX_CHARACTERS,
                true,
                "HH:mm:ss",
                new ChatContextStore.HistorySettings(true, DEFAULT_GENERAL_CHAT_MAX_MESSAGES,
                        TimeUnit.SECONDS.toMillis(DEFAULT_GENERAL_CHAT_MAX_AGE_SECONDS)),
                new ChatContextStore.HistorySettings(true, DEFAULT_CONVERSATION_MAX_MESSAGES,
                        TimeUnit.SECONDS.toMillis(DEFAULT_CONVERSATION_MAX_AGE_SECONDS))
        );
    }

    private PlayerResponseRateLimiter.ResponseRateLimit getResponseRateLimit() {
        FileConfiguration config = plugin.getConfig();
        if (config == null) {
            return defaultResponseRateLimit();
        }

        boolean enabled = config.getBoolean(RATE_LIMIT_ENABLED_PATH, true);
        int maxResponses = Math.max(1, config.getInt(
                RATE_LIMIT_MAX_RESPONSES_PATH,
                DEFAULT_MAX_RESPONSES_PER_PLAYER
        ));
        long windowSeconds = Math.max(1L, config.getLong(
                RATE_LIMIT_WINDOW_SECONDS_PATH,
                DEFAULT_RATE_LIMIT_WINDOW_SECONDS
        ));

        return new PlayerResponseRateLimiter.ResponseRateLimit(
                enabled,
                maxResponses,
                TimeUnit.SECONDS.toMillis(windowSeconds)
        );
    }

    private PlayerResponseRateLimiter.ResponseRateLimit defaultResponseRateLimit() {
        return new PlayerResponseRateLimiter.ResponseRateLimit(
                true,
                DEFAULT_MAX_RESPONSES_PER_PLAYER,
                TimeUnit.SECONDS.toMillis(DEFAULT_RATE_LIMIT_WINDOW_SECONDS)
        );
    }

    String buildSystemPrompt(NPC npc) {
        String configuredSystemPrompt = npc.getSystemPrompt();
        if (configuredSystemPrompt == null || configuredSystemPrompt.isBlank()) {
            return NPCProperties.DEFAULT_SYSTEM_PROMPT;
        }
        return configuredSystemPrompt;
    }

    String buildUserPrompt(NPC npc, String sourceName, String chatMessage) {
        String template = npc.getUserPromptTemplate();
        if (template == null || template.isBlank()) {
            template = NPCProperties.DEFAULT_USER_PROMPT_TEMPLATE;
        }

        return template
                .replace(PLACEHOLDER_PLAYER_NAME, sourceName)
                .replace(PLACEHOLDER_PLAYER_DISPLAY_NAME, sourceName)
                .replace(PLACEHOLDER_NPC_NAME, npc.getName())
                .replace(PLACEHOLDER_NPC_DISPLAY_NAME, npc.getDisplayName())
                .replace(PLACEHOLDER_CHAT_MESSAGE, chatMessage);
    }
}
