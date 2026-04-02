package nl.hauntedmc.ailex.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.ai.action.ActionContext;
import nl.hauntedmc.ailex.ai.action.Actionable;
import nl.hauntedmc.ailex.config.ConfigHandler;
import nl.hauntedmc.ailex.ai.movement.behaviour.MovementBehaviour;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.NPCData;
import nl.hauntedmc.ailex.util.LoggerUtils;
import nl.hauntedmc.ailex.util.ReflectionUtils;

import org.bukkit.entity.Player;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Main command for the AIlex plugin.
 * This command is used to create, destroy, and interact with AIlex NPCs.
 * The command has the following subcommands:
 * - create: creates a new AIlex NPC at the player's location
 * - destroy: destroys an existing AIlex NPC
 * - action: performs an action on an existing AIlex NPC
 * - set: sets a property of an existing AIlex NPC
 * - save: saves an existing AIlex NPC to the data file
 * - reload: reloads the AIlex configuration
 * - currentaction: gets the current action of an existing AIlex NPC
 * - cancelaction: cancels the current action of an existing AIlex NPC
 */
public class MainCommand implements BasicCommand {

    private final AIlexPlugin plugin;
    private final Map<String, Class<? extends MovementBehaviour>> behaviourMap;
    private final Map<String, Class<? extends Actionable>> actionMap;
    private final Map<String, Class<? extends NPC>> npcTypeMap;

    /**
     * Constructor for the MainCommand class
     * @param plugin The AIlex plugin
     */
    public MainCommand(AIlexPlugin plugin) {
        this.plugin = plugin;
        behaviourMap = ReflectionUtils.getBehaviourMap();
        actionMap = ReflectionUtils.getActionMap();
        npcTypeMap = ReflectionUtils.getNPCTypeMap();
    }

    /**
     * Executes the command with the given commandSourceStack and arguments.
     * @param commandSourceStack the commandSourceStack of the command
     * @param args the arguments of the command ignoring repeated spaces
     */
    @Override
    public void execute(@NotNull CommandSourceStack commandSourceStack, @NotNull String[] args) {
        if (commandSourceStack.getSender() instanceof Player player) {

            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("reload")) {
                    ConfigHandler.getInstance().reload();
                    plugin.reloadChatGPTClient();
                    LoggerUtils.logInfo("AIlex configuration reloaded.");
                    LoggerUtils.sendDebugMessage("AIlex configuration reloaded.");
                    return;
                }
            }

            if (args.length >= 2) {
                int id;

                try {
                    id = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    LoggerUtils.sendDebugMessage("Invalid ID.");
                    return;
                }

                switch (args[0].toLowerCase()) {
                    case "action":
                        if (args.length >= 3) {
                            String actionType = args[2].toLowerCase();
                            if (plugin.getNPCHandler().getNPCRegistry().containsKey(id)) {
                                Class<? extends Actionable> actionClass = actionMap.get(actionType);
                                if (actionClass != null) {
                                    try {
                                        ActionContext actionContext = new ActionContext.Builder().setTargetEntity(player).setTargetLocation(player.getLocation()).setPriority(1).build();
                                        Actionable action = actionClass.getDeclaredConstructor(ActionContext.class).newInstance(actionContext);
                                        plugin.getNPCHandler().getNPCRegistry().get(id).queueAction(action);
                                        LoggerUtils.sendDebugMessage("NPC " + id + " is doing action " + action.getFriendlyName() + " with context " + action.getActionContext().toString());
                                    } catch (Exception e) {
                                        LoggerUtils.sendDebugMessage("Failed to set behaviour: " + e.getMessage());
                                    }
                                } else {
                                    LoggerUtils.sendDebugMessage("Unknown action.");
                                }
                            } else {
                                LoggerUtils.sendDebugMessage("NPC " + id + " does not exist.");
                            }
                        } else {
                            LoggerUtils.sendDebugMessage("Usage: /ailex action <id> <move>");
                        }
                        return;

                    case "cancelaction":
                        if (plugin.getNPCHandler().getNPCRegistry().containsKey(id)) {
                            Actionable currentAction = plugin.getNPCHandler().getNPCRegistry().get(id).getCurrentAction();
                            if (currentAction != null) {
                                LoggerUtils.sendDebugMessage("NPC " + id + " canceled action: " + currentAction.getFriendlyName());
                                plugin.getNPCHandler().getNPCRegistry().get(id).cancelCurrentAction();
                            } else {
                                LoggerUtils.sendDebugMessage("NPC " + id + " is currently idle.");
                            }
                        } else {
                            LoggerUtils.sendDebugMessage("NPC " + id + " does not exist.");
                        }
                        return;

                    case "create":
                        if (args.length == 4) {
                            String type = args[2].toLowerCase();
                            String name = args[3];
                            Class<? extends NPC> npcClass = npcTypeMap.get(type);
                            if (npcClass != null) {
                                NPCData npcData = new NPCData(
                                        id,
                                        name,
                                        player.getLocation(),
                                        npcClass.getName(),
                                        ConfigHandler.getInstance().getDefaultNPCProperties()
                                );
                                try {
                                    plugin.getNPCHandler().createNPC(npcClass, npcData);
                                }
                                catch (IllegalArgumentException e) {
                                    LoggerUtils.sendDebugMessage("Failed to create NPC: " + e.getMessage());
                                }
                                LoggerUtils.sendDebugMessage("NPC " + id + " of type " + type + " created at your location.");
                            } else {
                                LoggerUtils.sendDebugMessage("Unknown NPC type.");
                            }
                        } else {
                            LoggerUtils.sendDebugMessage("Usage: /ailex create <id> <type> <name>");
                        }
                        return;

                    case "currentaction":
                        if (plugin.getNPCHandler().getNPCRegistry().containsKey(id)) {
                            Actionable currentAction = plugin.getNPCHandler().getNPCRegistry().get(id).getCurrentAction();
                            if (currentAction != null) {
                                LoggerUtils.sendDebugMessage("NPC " + id + " is executing action: " + currentAction.getFriendlyName() + " with context " + currentAction.getActionContext().toString());
                            } else {
                                LoggerUtils.sendDebugMessage("NPC " + id + " is currently idle.");
                            }
                        } else {
                            LoggerUtils.sendDebugMessage("NPC " + id + " does not exist.");
                        }
                        return;

                    case "remove":
                        try {
                            plugin.getNPCHandler().removeNPC(id);
                            LoggerUtils.sendDebugMessage("NPC " + id + " has been removed.");
                        }
                        catch (IllegalArgumentException e) {
                            LoggerUtils.sendDebugMessage("Failed to remove NPC: " + e.getMessage());
                        }
                        return;

                    case "save":
                        try {
                            plugin.getNPCHandler().saveNPC(id);
                            LoggerUtils.sendDebugMessage("NPC " + id + " has been saved.");
                        }
                        catch (IllegalArgumentException e) {
                            LoggerUtils.sendDebugMessage("Failed to save NPC: " + e.getMessage());
                        }
                        return;

                    case "set":
                        if (args.length >= 4) {
                            String settingType = args[2].toLowerCase();
                            String option = args[3].toLowerCase();
                            if (plugin.getNPCHandler().getNPCRegistry().containsKey(id)) {
                                switch (settingType) {
                                    case "movebehaviour":
                                        Class<? extends MovementBehaviour> behaviourClass = behaviourMap.get(option);
                                        if (behaviourClass != null) {
                                            try {
                                                MovementBehaviour behaviour = behaviourClass.getDeclaredConstructor().newInstance();
                                                plugin.getNPCHandler().getNPCRegistry().get(id).setMovementBehaviour(behaviour);
                                                LoggerUtils.sendDebugMessage("Set movement behaviour of NPC " + id + " to " + option + ".");
                                            } catch (Exception e) {
                                                LoggerUtils.sendDebugMessage("Failed to set behaviour: " + e.getMessage());
                                            }
                                        } else {
                                            LoggerUtils.sendDebugMessage("Unknown behaviour.");
                                        }
                                        break;
                                    default:
                                        LoggerUtils.sendDebugMessage("Unknown setting.");
                                }
                            } else {
                                LoggerUtils.sendDebugMessage("NPC " + id + " does not exist.");
                            }
                        } else {
                            LoggerUtils.sendDebugMessage("Usage: /ailex set <id> <movebehaviour> <>");
                        }
                        return;

                    default:
                        LoggerUtils.sendDebugMessage("Unknown command.");
                }
            } else {
                LoggerUtils.sendDebugMessage("Usage: /ailex <subcommand>");
            }
        }
    }

    /**
     * Suggests possible completions for the command based on the arguments provided.
     * TODO: Also get smart tips
     * @param commandSourceStack the commandSourceStack of the command
     * @param args the arguments of the command including repeated spaces
     * @return a collection of possible completions for the command
     */
    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack commandSourceStack, @NotNull String[] args) {
        final List<String> subcommands = Arrays.asList( "action",
                                                        "cancelaction",
                                                        "create",
                                                        "currentaction",
                                                        "remove",
                                                        "reload",
                                                        "save",
                                                        "set");
        final List<String> actions = new ArrayList<>(actionMap.keySet());
        final List<String> settings = Arrays.asList("movebehaviour");
        final List<String> behaviours = new ArrayList<>(behaviourMap.keySet());
        final List<String> npcTypes = new ArrayList<>(npcTypeMap.keySet());

        // Return subcommands if no arguments are provided
        if (args.length == 0) {
            return new ArrayList<>(subcommands);
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 1) {
            if (subcommands.contains(subcommand) && !subcommand.equals("reload")) {
                return suggestNpcIds("");
            }
            return filterByPrefix(subcommands, args[0]);
        }

        switch (subcommand) {
            case "action":
                if (args.length == 2) {
                    return suggestNpcIds(args[1]);
                }
                if (args.length == 3) {
                    return filterByPrefix(actions, args[2]);
                }
                return List.of();

            case "cancelaction":
            case "currentaction":
            case "remove":
            case "save":
                if (args.length == 2) {
                    return suggestNpcIds(args[1]);
                }
                return List.of();

            case "create":
                if (args.length == 2) {
                    return suggestNpcIds(args[1]);
                }
                if (args.length == 3) {
                    return filterByPrefix(npcTypes, args[2]);
                }
                return List.of();

            case "set":
                if (args.length == 2) {
                    return suggestNpcIds(args[1]);
                }
                if (args.length == 3) {
                    return filterByPrefix(settings, args[2]);
                }
                if (args.length == 4 && "movebehaviour".equalsIgnoreCase(args[2])) {
                    return filterByPrefix(behaviours, args[3]);
                }
                return List.of();

            default:
                return List.of();
        }
    }

    private List<String> suggestNpcIds(String prefix) {
        return filterByPrefix(
                plugin.getNPCHandler().getNPCRegistry().keySet().stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList()),
                prefix
        );
    }

    private List<String> filterByPrefix(Collection<String> values, String prefix) {
        String valuePrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(valuePrefix))
                .collect(Collectors.toList());
    }
}
