package nl.hauntedmc.ailex.assistant.application.command;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantMemoryService;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Player controls for opt-in assistant preference memory. */
public final class AssistantMemoryCommand implements CommandExecutor {

    private final AssistantMemoryService memoryService;

    public AssistantMemoryCommand(AssistantMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("[AIlex] This command can only be used by a player."));
            return true;
        }
        String action = args.length == 0 ? "show" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "on" -> {
                memoryService.setEnabled(player.getUniqueId(), true);
                player.sendMessage(Component.text("[AIlex] Preference memory is now on. No chat transcripts are stored."));
            }
            case "off" -> {
                memoryService.setEnabled(player.getUniqueId(), false);
                player.sendMessage(Component.text("[AIlex] Preference memory is now off."));
            }
            case "forget" -> {
                memoryService.forget(player.getUniqueId());
                player.sendMessage(Component.text("[AIlex] Your saved AI preferences were removed."));
            }
            case "show" -> player.sendMessage(Component.text("[AIlex] Preference memory is "
                    + (memoryService.isEnabled(player.getUniqueId()) ? "on." : "off.")));
            default -> player.sendMessage(Component.text("[AIlex] Usage: /ailexmemory <on|off|show|forget>"));
        }
        return true;
    }
}
