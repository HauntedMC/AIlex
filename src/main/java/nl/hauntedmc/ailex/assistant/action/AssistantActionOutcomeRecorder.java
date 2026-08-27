package nl.hauntedmc.ailex.assistant.action;

import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantEventMemoryService;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantExperienceMemoryService;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.ExperienceType;

import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Converts deterministic action-validator outcomes into audit events and procedural experience. The model does not get to
 * label its own action successful; only the execution boundary can produce these records.
 */
public final class AssistantActionOutcomeRecorder {

    private final AssistantEventMemoryService eventMemory;
    private final AssistantExperienceMemoryService experienceMemory;

    public AssistantActionOutcomeRecorder(
            AssistantEventMemoryService eventMemory,
            AssistantExperienceMemoryService experienceMemory
    ) {
        this.eventMemory = eventMemory;
        this.experienceMemory = experienceMemory;
    }

    /**
     * Records the result returned by the deterministic action boundary.
     *
     * <p>The audit event is scoped to the requester/NPC and may in turn support a procedural lesson. The original player
     * message is deliberately not persisted here; it is used only by the action validator before this method is called.
     * Rejected proposals are useful experience too, but they never become evidence that an action occurred.</p>
     *
     * @param requester player whose explicit request was validated
     * @param npcId stable NPC memory identity
     * @param playerMessage original request; retained in the signature for outcome-call-site clarity, never persisted
     * @param result deterministic validation/execution result
     */
    public void record(
            Player requester,
            String npcId,
            String playerMessage,
            AssistantActionService.ActionResult result
    ) {
        if (requester == null || result == null) {
            return;
        }
        String executed = result.executed().stream().map(Enum::name).sorted().toList().toString();
        String rejected = result.rejected().stream().map(Enum::name).sorted().toList().toString();
        String summary = "action_outcome=" + result.outcome() + " executed=" + executed + " rejected=" + rejected;
        Set<String> evidence = new HashSet<>();
        if (eventMemory != null) {
            var event = eventMemory.recordCustomEvent(
                    "assistant.action",
                    requester.getUniqueId(),
                    npcId,
                    summary,
                    result.executed().isEmpty() ? 0.55D : 0.80D,
                    Duration.ofDays(14),
                    Set.of("assistant-action", "verified-outcome")
            );
            if (event != null) {
                evidence.add("memory." + event.id());
            }
        }
        if (experienceMemory == null) {
            return;
        }
        ExperienceType type = result.executed().isEmpty()
                ? ExperienceType.RETRIEVAL_FAILURE
                : ExperienceType.SUCCESSFUL_TOOL_PATH;
        String lesson = result.executed().isEmpty()
                ? "A physical action proposal was rejected by deterministic validation; require an explicit matching player "
                        + "request and compatible live NPC/world state before proposing it again."
                : "This explicit physical-action request passed deterministic validation. Re-check requester wording and live "
                        + "state every time; never assume a previous action remains appropriate.";
        experienceMemory.recordVerifiedOutcome(
                npcId,
                AssistantIntent.LIVE_STATE,
                type,
                "action-" + (result.executed().isEmpty() ? "rejected" : "executed"),
                lesson,
                result.outcome(),
                Set.copyOf(evidence)
        );
    }
}
