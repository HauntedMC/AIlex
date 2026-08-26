package nl.hauntedmc.ailex.assistant.action;

import nl.hauntedmc.ailex.ai.action.ActionContext;
import nl.hauntedmc.ailex.ai.action.move.FollowPlayerAction;
import nl.hauntedmc.ailex.ai.action.move.MoveHereAction;
import nl.hauntedmc.ailex.npc.NPC;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic plan→validate→execute boundary for the small embodied capability surface. The model can only propose;
 * this service re-validates configuration, explicit player intent, target identity and NPC state before queueing action.
 */
public final class AssistantActionService {

    private static final Set<AssistantActionType> DEFAULT_ALLOWED = Set.of(
            AssistantActionType.FOLLOW_REQUESTER,
            AssistantActionType.COME_HERE,
            AssistantActionType.STOP_MOVING
    );

    private final JavaPlugin plugin;

    public AssistantActionService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public ActionResult validateAndExecute(
            Player requester,
            NPC npc,
            String playerMessage,
            List<AssistantActionProposal> proposals
    ) {
        if (!enabled() || requester == null || npc == null || !npc.isSpawned()
                || proposals == null || proposals.isEmpty()) {
            return new ActionResult(List.of(), rejectedTypes(proposals), "actions-unavailable");
        }
        List<AssistantActionType> executed = new ArrayList<>();
        List<AssistantActionType> rejected = new ArrayList<>();
        for (AssistantActionProposal proposal : proposals.stream().limit(2).toList()) {
            if (proposal == null || !allowed(proposal.type()) || !explicitlyRequested(playerMessage, proposal.type())) {
                if (proposal != null) {
                    rejected.add(proposal.type());
                }
                continue;
            }
            if (execute(requester, npc, proposal.type())) {
                executed.add(proposal.type());
            } else {
                rejected.add(proposal.type());
            }
        }
        return new ActionResult(
                List.copyOf(executed), List.copyOf(rejected), executed.isEmpty() ? "no-approved-action" : "executed"
        );
    }

    public boolean enabled() {
        FileConfiguration config = plugin == null ? null : plugin.getConfig();
        return config != null && config.getBoolean("openai.assistant.actions.enabled", true);
    }

    private boolean allowed(AssistantActionType type) {
        if (type == null || !DEFAULT_ALLOWED.contains(type)) {
            return false;
        }
        FileConfiguration config = plugin.getConfig();
        List<String> configured = config.getStringList("openai.assistant.actions.allowed");
        if (configured.isEmpty()) {
            return DEFAULT_ALLOWED.contains(type);
        }
        return configured.stream().map(value -> value.toUpperCase(Locale.ROOT).replace('-', '_'))
                .anyMatch(type.name()::equals);
    }

    private boolean explicitlyRequested(String message, AssistantActionType type) {
        String text = normalize(message);
        return switch (type) {
            case FOLLOW_REQUESTER -> containsAny(text,
                    "volg mij", "volg me", "loop met me mee", "follow me", "come with me", "walk with me");
            case COME_HERE -> containsAny(text,
                    "kom hier", "kom naar mij", "kom naar me", "come here", "come to me", "walk over here");
            case STOP_MOVING -> containsAny(text,
                    "stop", "stop met lopen", "blijf hier", "blijf staan", "halt", "stop moving", "stay here");
        };
    }

    private boolean execute(Player requester, NPC npc, AssistantActionType type) {
        switch (type) {
            case STOP_MOVING -> {
                npc.clearActionQueue();
                npc.cancelCurrentAction();
                return true;
            }
            case FOLLOW_REQUESTER -> {
                if (requester.getWorld() == null || npc.getEntity() == null
                        || !requester.getWorld().equals(npc.getEntity().getWorld())) {
                    return false;
                }
                ActionContext context = new ActionContext.Builder()
                        .setTargetEntity(requester)
                        .setPriority(10)
                        .build();
                npc.queueAction(new FollowPlayerAction(context));
                return true;
            }
            case COME_HERE -> {
                if (requester.getLocation() == null || requester.getWorld() == null || npc.getEntity() == null
                        || !requester.getWorld().equals(npc.getEntity().getWorld())) {
                    return false;
                }
                ActionContext context = new ActionContext.Builder()
                        .setTargetLocation(requester.getLocation().clone())
                        .setPriority(10)
                        .build();
                npc.queueAction(new MoveHereAction(context));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private List<AssistantActionType> rejectedTypes(List<AssistantActionProposal> proposals) {
        if (proposals == null) {
            return List.of();
        }
        return proposals.stream().filter(java.util.Objects::nonNull).map(AssistantActionProposal::type).toList();
    }

    private boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    public record ActionResult(
            List<AssistantActionType> executed,
            List<AssistantActionType> rejected,
            String outcome
    ) {
        public ActionResult {
            executed = executed == null ? List.of() : List.copyOf(executed);
            rejected = rejected == null ? List.of() : List.copyOf(rejected);
            outcome = outcome == null ? "" : outcome.trim();
        }
    }
}
