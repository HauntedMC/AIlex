package nl.hauntedmc.ailex.assistant.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.assistant.application.routing.AssistantIntentClassifier;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantEventMemoryService;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.ExplicitPlayerMemoryService;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.lifecycle.NpcManager;

import org.bukkit.entity.Player;

/**
 * Cheap trusted observation boundary for public chat addressed to an AIlex NPC.
 *
 * <p>The chat payload remains untrusted model input, but Bukkit/Paper is authoritative for the fact that a specific player
 * sent that payload at this time. Capturing that distinction lets event recall answer "who said that?" without treating the
 * player's words as trusted server facts.</p>
 */
public final class AssistantChatObservationService {

    private final AIlexPlugin plugin;
    private final ExplicitPlayerMemoryService explicitMemory;

    public AssistantChatObservationService(AIlexPlugin plugin) {
        this.plugin = plugin;
        this.explicitMemory = new ExplicitPlayerMemoryService(plugin.getAssistantMemoryService());
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
        boolean recallQuestion = intent == AssistantIntent.EVENT_RECALL || intent == AssistantIntent.MEMORY_RECALL;
        NpcManager npcManager = plugin.getNpcManager();
        if (npcManager == null) {
            return;
        }
        for (NPC npc : npcManager.getNPCRegistry().values()) {
            if (npc == null || !npc.isChatEnabled() || !AssistantMentionMatcher.isMentioned(message, npc.getName())) {
                continue;
            }
            // Explicit player-owned memory is written before AssistantService.prepare(), so the normal context planner can
            // immediately retrieve the accepted value and the generated acknowledgement cannot drift from stored state.
            explicitMemory.observe(source.getUniqueId(), source.getName(), message);

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
}
