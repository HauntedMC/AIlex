package nl.hauntedmc.ailex.util;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.ailex.AIlexPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoggerUtilsTest {

    @Test
    void logInfoShouldWriteToPluginLogger() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);

        try (MockedStatic<AIlexPlugin> mockedPlugin = org.mockito.Mockito.mockStatic(AIlexPlugin.class)) {
            mockedPlugin.when(AIlexPlugin::getPlugin).thenReturn(plugin);
            LoggerUtils.logInfo("<green>hello</green>");
        }

        verify(logger).log(eq(Level.INFO), any(String.class));
    }

    @Test
    void broadcastShouldDelegateToBukkitServer() {
        Server server = mock(Server.class);
        try (MockedStatic<Bukkit> mockedBukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            mockedBukkit.when(Bukkit::getServer).thenReturn(server);
            LoggerUtils.broadcast("<yellow>hello</yellow>");
        }

        verify(server).sendMessage(any(Component.class));
    }

    @Test
    void sendDebugMessageShouldTargetOnlyPermittedPlayers() {
        Player allowed = mock(Player.class);
        Player denied = mock(Player.class);
        when(allowed.hasPermission("ailex.debug")).thenReturn(true);
        when(denied.hasPermission("ailex.debug")).thenReturn(false);

        try (MockedStatic<Bukkit> mockedBukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            mockedBukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.copyOf(List.of(allowed, denied)));
            LoggerUtils.sendDebugMessage("check");
        }

        verify(allowed).sendMessage(any(Component.class));
        org.mockito.Mockito.verify(denied, org.mockito.Mockito.never()).sendMessage(any(Component.class));
    }
}
