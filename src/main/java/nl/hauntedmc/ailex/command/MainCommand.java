package nl.hauntedmc.ailex.command;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.ai.action.ActionContext;
import nl.hauntedmc.ailex.ai.action.Actionable;
import nl.hauntedmc.ailex.ai.movement.behaviour.MovementBehaviour;
import nl.hauntedmc.ailex.application.registry.BuiltinTypeRegistry;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryKind;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryRecord;
import nl.hauntedmc.ailex.config.ConfigHandler;
import nl.hauntedmc.ailex.infrastructure.openai.OpenAiResponsesClient;
import nl.hauntedmc.ailex.listener.llm.AssistantRequestTracer;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.NPCData;
import nl.hauntedmc.ailex.util.LoggerUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Main administrative command for NPC management and assistant diagnostics. */
public class MainCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "ailex.admin";
    private final AIlexPlugin plugin;
    private final Map<String, Class<? extends MovementBehaviour>> behaviourMap;
    private final Map<String, Class<? extends Actionable>> actionMap;
    private final Map<String, Class<? extends NPC>> npcTypeMap;

    public MainCommand(AIlexPlugin plugin) {
        this.plugin = plugin;
        behaviourMap = BuiltinTypeRegistry.getBehaviourMap();
        actionMap = BuiltinTypeRegistry.getActionMap();
        npcTypeMap = BuiltinTypeRegistry.getNPCTypeMap();
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("[AIlex] This command can only be used by a player."));
            return true;
        }
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            sendCommandMessage(player, "You do not have permission to manage AIlex.");
            return true;
        }
        if (args.length == 0) {
            sendCommandMessage(player, "Usage: /ailex <subcommand>");
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if ("reload".equals(subcommand) && args.length == 1) {
            ConfigHandler.getInstance().reload();
            plugin.reloadOpenAiResponsesClient();
            LoggerUtils.logInfo("AIlex configuration reloaded.");
            sendCommandMessage(player, "AIlex configuration reloaded.");
            return true;
        }
        if ("ai".equals(subcommand)) {
            handleAssistantCommand(player, args);
            return true;
        }
        if ("trace".equals(subcommand)) {
            handleTraceCommand(player, args);
            return true;
        }
        if ("memory".equals(subcommand)) {
            handleMemoryCommand(player, args);
            return true;
        }

        if (args.length < 2) {
            sendCommandMessage(player, "Usage: /ailex <subcommand>");
            return true;
        }
        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            sendCommandMessage(player, "Invalid ID.");
            return true;
        }

        switch (subcommand) {
            case "action" -> handleAction(player, id, args);
            case "cancelaction" -> handleCancelAction(player, id);
            case "create" -> handleCreate(player, id, args);
            case "currentaction" -> handleCurrentAction(player, id);
            case "remove" -> handleRemove(player, id);
            case "save" -> handleSave(player, id);
            case "set" -> handleSet(player, id, args);
            default -> sendCommandMessage(player, "Unknown command.");
        }
        return true;
    }

    private void handleAssistantCommand(Player player, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";
        if ("usage".equals(action)) {
            OpenAiResponsesClient client = plugin.getOpenAiResponsesClient();
            sendCommandMessage(player, client == null ? "OpenAI client is unavailable." : "OpenAI " + client.usageStatus());
            return;
        }
        if (plugin.getAssistantService() == null) {
            sendCommandMessage(player, "Assistant engine is unavailable.");
            return;
        }
        switch (action) {
            case "status" -> {
                StringBuilder status = new StringBuilder("Assistant ").append(plugin.getAssistantService().status());
                AssistantRequestTracer tracer = plugin.getAssistantRequestTracer();
                if (tracer != null) {
                    status.append(", active_requests=").append(tracer.activeCount());
                }
                OpenAiResponsesClient client = plugin.getOpenAiResponsesClient();
                if (client != null) {
                    status.append("; OpenAI ").append(client.usageStatus());
                }
                sendCommandMessage(player, status.toString());
            }
            case "rebuild-index" -> {
                plugin.getAssistantService().reload();
                sendCommandMessage(player, "Assistant knowledge and memory indexes reloaded.");
            }
            default -> sendCommandMessage(player, "Usage: /ailex ai <status|usage|rebuild-index>");
        }
    }

    private void handleTraceCommand(Player player, String[] args) {
        AssistantRequestTracer tracer = plugin.getAssistantRequestTracer();
        if (tracer == null) {
            sendCommandMessage(player, "Assistant request tracer is unavailable.");
            return;
        }
        if (args.length >= 2 && !"recent".equalsIgnoreCase(args[1])) {
            sendCommandMessage(player, "Usage: /ailex trace recent [player] [limit]");
            return;
        }
        String requester = args.length >= 3 ? args[2] : "";
        int limit = args.length >= 4 ? parseBounded(args[3], 10, 1, 30) : 10;
        List<AssistantRequestTracer.TraceSnapshot> matches = tracer.recent(256).stream()
                .filter(trace -> requester.isBlank() || trace.requester().equalsIgnoreCase(requester))
                .sorted(Comparator.comparingLong(AssistantRequestTracer.TraceSnapshot::latencyMillis).reversed())
                .limit(limit)
                .toList();
        if (matches.isEmpty()) {
            sendCommandMessage(player, requester.isBlank() ? "No recent assistant traces." : "No recent traces for " + requester + '.');
            return;
        }
        sendCommandMessage(player, "Recent assistant traces (" + matches.size() + "):");
        for (AssistantRequestTracer.TraceSnapshot trace : matches) {
            String id = trace.requestId().toString().substring(0, 8);
            String detail = trace.detail().isBlank() ? "" : " detail=" + trace.detail();
            sendCommandMessage(player, id + " " + trace.requester() + "→" + trace.npc()
                    + " " + trace.kind() + " " + trace.state().name().toLowerCase(Locale.ROOT)
                    + " " + trace.latencyMillis() + "ms" + detail);
        }
    }

    private void handleMemoryCommand(Player player, String[] args) {
        if (plugin.getAssistantMemoryService() == null) {
            sendCommandMessage(player, "Assistant Memory V2 is unavailable.");
            return;
        }
        List<MemoryRecord> records = plugin.getAssistantMemoryService().activeSnapshot();
        Map<MemoryKind, Long> byKind = new EnumMap<>(MemoryKind.class);
        records.forEach(record -> byKind.merge(record.kind(), 1L, Long::sum));
        String counts = Arrays.stream(MemoryKind.values())
                .map(kind -> kind.name().toLowerCase(Locale.ROOT) + '=' + byKind.getOrDefault(kind, 0L))
                .collect(Collectors.joining(", "));
        sendCommandMessage(player, "Memory V2 active_records=" + records.size() + " (" + counts + ")");
        if (args.length >= 2 && "recent".equalsIgnoreCase(args[1])) {
            records.stream().limit(8).forEach(record -> sendCommandMessage(
                    player,
                    record.scope().name().toLowerCase(Locale.ROOT) + '/' + record.kind().name().toLowerCase(Locale.ROOT)
                            + " key=" + record.key() + " confidence=" + String.format(Locale.ROOT, "%.2f", record.confidence())
                            + " salience=" + String.format(Locale.ROOT, "%.2f", record.salience())
                            + " source=" + record.sourceType()
            ));
        }
    }

    private void handleAction(Player player, int id, String[] args) {
        if (args.length < 3) {
            sendCommandMessage(player, "Usage: /ailex action <id> <move>");
            return;
        }
        NPC npc = npc(id, player);
        if (npc == null) {
            return;
        }
        Class<? extends Actionable> actionClass = actionMap.get(args[2].toLowerCase(Locale.ROOT));
        if (actionClass == null) {
            sendCommandMessage(player, "Unknown action.");
            return;
        }
        try {
            ActionContext context = new ActionContext.Builder()
                    .setTargetEntity(player).setTargetLocation(player.getLocation()).setPriority(1).build();
            Actionable action = actionClass.getDeclaredConstructor(ActionContext.class).newInstance(context);
            npc.queueAction(action);
            sendCommandMessage(player, "NPC " + id + " is doing action " + action.getFriendlyName() + ".");
        } catch (Exception exception) {
            sendCommandMessage(player, "Failed to start action: " + exception.getMessage());
        }
    }

    private void handleCancelAction(Player player, int id) {
        NPC npc = npc(id, player);
        if (npc == null) {
            return;
        }
        Actionable current = npc.getCurrentAction();
        if (current == null) {
            sendCommandMessage(player, "NPC " + id + " is currently idle.");
            return;
        }
        sendCommandMessage(player, "NPC " + id + " canceled action: " + current.getFriendlyName());
        npc.cancelCurrentAction();
    }

    private void handleCreate(Player player, int id, String[] args) {
        if (args.length != 4) {
            sendCommandMessage(player, "Usage: /ailex create <id> <type> <name>");
            return;
        }
        String type = args[2].toLowerCase(Locale.ROOT);
        Class<? extends NPC> npcClass = npcTypeMap.get(type);
        if (npcClass == null) {
            sendCommandMessage(player, "Unknown NPC type.");
            return;
        }
        NPCData data = new NPCData(
                id, args[3], player.getLocation(), npcClass.getName(), ConfigHandler.getInstance().getDefaultNPCProperties()
        );
        try {
            plugin.getNpcManager().createNPC(npcClass, data);
            sendCommandMessage(player, "NPC " + id + " of type " + type + " created at your location.");
        } catch (IllegalArgumentException exception) {
            sendCommandMessage(player, "Failed to create NPC: " + exception.getMessage());
        }
    }

    private void handleCurrentAction(Player player, int id) {
        NPC npc = npc(id, player);
        if (npc == null) {
            return;
        }
        Actionable current = npc.getCurrentAction();
        sendCommandMessage(player, current == null
                ? "NPC " + id + " is currently idle."
                : "NPC " + id + " is executing action: " + current.getFriendlyName() + ".");
    }

    private void handleRemove(Player player, int id) {
        try {
            plugin.getNpcManager().removeNPC(id);
            sendCommandMessage(player, "NPC " + id + " has been removed.");
        } catch (IllegalArgumentException exception) {
            sendCommandMessage(player, "Failed to remove NPC: " + exception.getMessage());
        }
    }

    private void handleSave(Player player, int id) {
        try {
            plugin.getNpcManager().saveNPC(id);
            sendCommandMessage(player, "NPC " + id + " has been saved.");
        } catch (IllegalArgumentException exception) {
            sendCommandMessage(player, "Failed to save NPC: " + exception.getMessage());
        }
    }

    private void handleSet(Player player, int id, String[] args) {
        if (args.length < 4) {
            sendCommandMessage(player, "Usage: /ailex set <id> <movebehaviour> <value>");
            return;
        }
        NPC npc = npc(id, player);
        if (npc == null) {
            return;
        }
        if (!"movebehaviour".equalsIgnoreCase(args[2])) {
            sendCommandMessage(player, "Unknown setting.");
            return;
        }
        Class<? extends MovementBehaviour> behaviourClass = behaviourMap.get(args[3].toLowerCase(Locale.ROOT));
        if (behaviourClass == null) {
            sendCommandMessage(player, "Unknown behaviour.");
            return;
        }
        try {
            MovementBehaviour behaviour = behaviourClass.getDeclaredConstructor().newInstance();
            npc.setMovementBehaviour(behaviour);
            sendCommandMessage(player, "Set movement behaviour of NPC " + id + " to " + args[3].toLowerCase(Locale.ROOT) + ".");
        } catch (Exception exception) {
            sendCommandMessage(player, "Failed to set behaviour: " + exception.getMessage());
        }
    }

    private NPC npc(int id, Player player) {
        NPC npc = plugin.getNpcManager().getNPCRegistry().get(id);
        if (npc == null) {
            sendCommandMessage(player, "NPC " + id + " does not exist.");
        }
        return npc;
    }

    private int parseBounded(String value, int fallback, int minimum, int maximum) {
        try {
            return Math.clamp(Integer.parseInt(value), minimum, maximum);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void sendCommandMessage(Player player, String message) {
        player.sendMessage(Component.text("[AIlex] " + message));
    }

    @Override
    public @NotNull List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        List<String> subcommands = List.of(
                "action", "ai", "cancelaction", "create", "currentaction", "memory", "remove", "reload", "save", "set", "trace"
        );
        if (args.length == 0) {
            return new ArrayList<>(subcommands);
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            if (requiresNpcId(subcommand)) {
                return suggestNpcIds("");
            }
            return filterByPrefix(subcommands, args[0]);
        }
        if ("ai".equals(subcommand)) {
            return args.length == 2 ? filterByPrefix(List.of("status", "usage", "rebuild-index"), args[1]) : List.of();
        }
        if ("trace".equals(subcommand)) {
            return args.length == 2 ? filterByPrefix(List.of("recent"), args[1]) : List.of();
        }
        if ("memory".equals(subcommand)) {
            return args.length == 2 ? filterByPrefix(List.of("status", "recent"), args[1]) : List.of();
        }
        return switch (subcommand) {
            case "action" -> args.length == 2 ? suggestNpcIds(args[1])
                    : args.length == 3 ? filterByPrefix(new ArrayList<>(actionMap.keySet()), args[2]) : List.of();
            case "cancelaction", "currentaction", "remove", "save" ->
                    args.length == 2 ? suggestNpcIds(args[1]) : List.of();
            case "create" -> args.length == 2 ? suggestNpcIds(args[1])
                    : args.length == 3 ? filterByPrefix(new ArrayList<>(npcTypeMap.keySet()), args[2]) : List.of();
            case "set" -> args.length == 2 ? suggestNpcIds(args[1])
                    : args.length == 3 ? filterByPrefix(List.of("movebehaviour"), args[2])
                    : args.length == 4 && "movebehaviour".equalsIgnoreCase(args[2])
                    ? filterByPrefix(new ArrayList<>(behaviourMap.keySet()), args[3]) : List.of();
            default -> List.of();
        };
    }

    private boolean requiresNpcId(String subcommand) {
        return List.of("action", "cancelaction", "create", "currentaction", "remove", "save", "set").contains(subcommand);
    }

    private List<String> suggestNpcIds(String prefix) {
        if (plugin.getNpcManager() == null) {
            return List.of();
        }
        return filterByPrefix(
                plugin.getNpcManager().getNPCRegistry().keySet().stream().map(String::valueOf).collect(Collectors.toList()),
                prefix
        );
    }

    private List<String> filterByPrefix(Collection<String> values, String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }
}
