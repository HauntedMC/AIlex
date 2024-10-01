package nl.hauntedmc.ailex.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import nl.hauntedmc.ailex.AIlexPlugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * Utility class for logging messages to the console and broadcasting messages to all players.
 */
public class LoggerUtils {


    /**
     * Logs an info message to the console.
     * @param message - the message to log
     */
    public static void logInfo(String message) {
        log(Level.INFO, message);
    }

    /**
     * Logs a warning message to the console.
     * @param message - the message to log
     */
    public static void logWarning(String message) {
        log(Level.WARNING, message);
    }

    /**
     * Logs an error message to the console.
     * @param message - the message to log
     */
    public static void logError(String message) {
        log(Level.SEVERE, message);
    }

    /**
     * Logs a message to the console with the given level, message, and prefix.
     * @param level - the level of the message
     * @param message - the message to log
     */
    private static void log(Level level, String message) {
        Component component = FormatterUtils.serializer.deserialize(message);
        AIlexPlugin.getPlugin().getLogger().log(level, LegacyComponentSerializer.legacySection().serialize(component));
    }


    /**
     * Broadcasts a message to all players with the given message and prefix.
     * @param message - the message to broadcast
     */
    public static void broadcast(String message) {
        Component component = FormatterUtils.serializer.deserialize(message);
        Bukkit.getServer().sendMessage(component);
    }

    /**
     * Sends a debug message to all players with the given message.
     * @param message - the message to send
     */
    public static void sendDebugMessage(String message) {
        String formattedMessage = FormatterUtils.DEBUG_PREFIX + message;
        Component component = FormatterUtils.serializer.deserialize(formattedMessage);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("ailex.debug")) {
                player.sendMessage(component);
            }
        }
    }
}
