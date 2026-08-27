package nl.hauntedmc.ailex.assistant.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.assistant.application.routing.AssistantIntentClassifier;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantEventMemoryService;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.lifecycle.NpcManager;

import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Cheap trusted observation boundary for public chat addressed to an AIlex NPC.
 *
 * <p>The chat payload remains untrusted model input, but Bukkit/Paper is authoritative for the fact that a specific player
 * sent that payload at this time. Capturing that distinction lets event recall answer "who said that?" without treating the
 * player's words as trusted server facts. Player-owned semantic memory is handled by AssistantService for every accepted
 * direct or implicit-follow-up request; this class owns only public observation.</p>
 */
public final class AssistantChatObservationService {

    private final AIlexPlugin plugin;

    public AssistantChatObservationService(AIlexPlugin plugin) {
        this.plugin = plugin;
    }

    /** Must run on the server thread, before the addressed request is prepared. */
    public void observe(Player source, Component component) {
        if (source == null || component == null) {
            return;
        }
        String message = PlainTextComponentSerializer.plainText().serialize(component).replaceAll("\\s+", " ").trim();
        if (message.isBlank()) {
            return;
        }

        AssistantIntent intent = AssistantIntentClassifier.analyze(message).intent();
        boolean recallQuestion = intent == AssistantIntent.EVENT_RECALL
                || intent == AssistantIntent.MEMORY_RECALL
                || looksLikeRecentEventRecall(message);
        NpcManager npcManager = plugin.getNpcManager();
        if (npcManager == null) {
            return;
        }
        for (NPC npc : npcManager.getNPCRegistry().values()) {
            if (npc == null || !npc.isChatEnabled() || !AssistantMentionMatcher.isMentioned(message, npc.getName())) {
                continue;
            }

            // A recall question is transport context, not the historical event being requested. Recording it before retrieval
            // would make it the newest/highest-recency event and could cause "who asked that just now?" to answer with the
            // requester instead of the earlier speaker.
            if (recallQuestion) {
                continue;
            }
            AssistantEventMemoryService events = plugin.getAssistantEventMemoryService();
            if (events != null) {
                events.recordObservedPublicChat(
                        source.getUniqueId(), source.getName(), String.valueOf(npc.getId()), npc.getName(), message
                );
            }
        }
    }

    static boolean looksLikeRecentEventRecall(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return containsAny(text,
                "wie vroeg net", "wie vroeg dat", "wie zei net", "wie zei dat", "wat vroeg net", "wat zei net",
                "who just asked", "who asked that", "who just said", "who said that", "what did they just ask",
                "what did they just say"
        );
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }
}
