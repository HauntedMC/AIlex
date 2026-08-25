package nl.hauntedmc.ailex.command;

import net.kyori.adventure.text.Component;

import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.ai.action.ActionContext;
import nl.hauntedmc.ailex.ai.action.Actionable;
import nl.hauntedmc.ailex.config.ConfigHandler;
import nl.hauntedmc.ailex.ai.movement.behaviour.MovementBehaviour;
import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.NPCData;
import nl.hauntedmc.ailex.util.LoggerUtils;
import nl.hauntedmc.ailex.util.ReflectionUtils;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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
public class MainCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "ailex.admin";
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
     * Executes the command with the given sender and arguments.
     *
     * @param sender the command sender
     * @param command the command being executed
     * @param label the command label used by the sender
     * @param args the supplied command arguments
     * @return whether the command was handled
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (sender instanceof Player player) {
            if (!player.hasPermission(ADMIN_PERMISSION)) {
                sendCommandMessage(player, "You do not have permission to manage AIlex.");
                return true;
            }

            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("reload")) {
                    ConfigHandler.getInstance().reload();
                    plugin.reloadChatGPTClient();
                    LoggerUtils.logInfo("AIlex configuration reloaded.");
                    sendCommandMessage(player, "AIlex configuration reloaded.");
                    return true;
                }
            }

            if (args.length >= 2) {
                int id;

                try {
                    id = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sendCommandMessage(player, "Invalid ID.");
                    return true;
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
                                        sendCommandMessage(player, "NPC " + id + " is doing action "
                                                + action.getFriendlyName() + ".");
                                    } catch (Exception e) {
                                        sendCommandMessage(player, "Failed to start action: " + e.getMessage());
                                    }
                                } else {
                                    sendCommandMessage(player, "Unknown action.");
                                }
                            } else {
                                sendCommandMessage(player, "NPC " + id + " does not exist.");
                            }
                        } else {
                            sendCommandMessage(player, "Usage: /ailex action <id> <move>");
                        }
                        return true;

                    case "cancelaction":
                        if (plugin.getNPCHandler().getNPCRegistry().containsKey(id)) {
                            Actionable currentAction = plugin.getNPCHandler().getNPCRegistry().get(id).getCurrentAction();
                            if (currentAction != null) {
                                sendCommandMessage(player, "NPC " + id + " canceled action: "
                                        + currentAction.getFriendlyName());
                                plugin.getNPCHandler().getNPCRegistry().get(id).cancelCurrentAction();
                            } else {
                                sendCommandMessage(player, "NPC " + id + " is currently idle.");
                            }
                        } else {
                            sendCommandMessage(player, "NPC " + id + " does not exist.");
                        }
                        return true;

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
                                    sendCommandMessage(player, "NPC " + id + " of type " + type
                                            + " created at your location.");
                                }
                                catch (IllegalArgumentException e) {
                                    sendCommandMessage(player, "Failed to create NPC: " + e.getMessage());
                                }
                            } else {
                                sendCommandMessage(player, "Unknown NPC type.");
                            }
                        } else {
                            sendCommandMessage(player, "Usage: /ailex create <id> <type> <name>");
                        }
                        return true;

                    case "currentaction":
                        if (plugin.getNPCHandler().getNPCRegistry().containsKey(id)) {
                            Actionable currentAction = plugin.getNPCHandler().getNPCRegistry().get(id).getCurrentAction();
                            if (currentAction != null) {
                                sendCommandMessage(player, "NPC " + id + " is executing action: "
                                        + currentAction.getFriendlyName() + ".");
                            } else {
                                sendCommandMessage(player, "NPC " + id + " is currently idle.");
                            }
                        } else {
                            sendCommandMessage(player, "NPC " + id + " does not exist.");
                        }
                        return true;

                    case "remove":
                        try {
                            plugin.getNPCHandler().removeNPC(id);
                            sendCommandMessage(player, "NPC " + id + " has been removed.");
                        }
                        catch (IllegalArgumentException e) {
                            sendCommandMessage(player, "Failed to remove NPC: " + e.getMessage());
                        }
                        return true;

                    case "save":
                        try {
                            plugin.getNPCHandler().saveNPC(id);
                            sendCommandMessage(player, "NPC " + id + " has been saved.");
                        }
                        catch (IllegalArgumentException e) {
                            sendCommandMessage(player, "Failed to save NPC: " + e.getMessage());
                        }
                        return true;

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
                                                sendCommandMessage(player, "Set movement behaviour of NPC " + id
                                                        + " to " + option + ".");
                                            } catch (Exception e) {
                                                sendCommandMessage(player, "Failed to set behaviour: " + e.getMessage());
                                            }
                                        } else {
                                            sendCommandMessage(player, "Unknown behaviour.");
                                        }
                                        break;
                                    default:
                                        sendCommandMessage(player, "Unknown setting.");
                                }
                            } else {
                                sendCommandMessage(player, "NPC " + id + " does not exist.");
                            }
                        } else {
                            sendCommandMessage(player, "Usage: /ailex set <id> <movebehaviour> <>");
                        }
                        return true;

                    default:
                        sendCommandMessage(player, "Unknown command.");
                }
            } else {
                sendCommandMessage(player, "Usage: /ailex <subcommand>");
            }
        } else {
            sender.sendMessage(Component.text("[AIlex] This command can only be used by a player."));
        }

        return true;
    }

    private void sendCommandMessage(Player player, String message) {
        player.sendMessage(Component.text("[AIlex] " + message));
    }

    /**
     * Suggests possible completions for the command based on the arguments provided.
     * TODO: Also get smart tips
     * @param sender the command sender
     * @param command the command being completed
     * @param alias the command label used by the sender
     * @param args the arguments of the command including repeated spaces
     * @return a collection of possible completions for the command
     */
    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
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
