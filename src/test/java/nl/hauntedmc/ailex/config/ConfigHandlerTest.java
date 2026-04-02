package nl.hauntedmc.ailex.config;

import nl.hauntedmc.ailex.npc.NPCProperties;
import nl.hauntedmc.ailex.util.LoggerUtils;
import nl.hauntedmc.ailex.testutil.ConfigTestSupport;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigHandlerTest {

    @AfterEach
    void tearDown() {
        ConfigTestSupport.reset();
    }

    @Test
    void getInstanceShouldFailWhenNotInitialized() {
        assertThrows(IllegalStateException.class, ConfigHandler::getInstance);
    }

    @Test
    void shouldInitializeAndExposeConfiguration() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration configuration = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(configuration);

        ConfigHandler.init(plugin);

        assertEquals(configuration, ConfigHandler.getInstance().getConfig());
    }

    @Test
    void reloadShouldRefreshPluginConfigAndLog() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration configuration = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(configuration);
        ConfigHandler.init(plugin);

        try (MockedStatic<LoggerUtils> mockedLogger = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            ConfigHandler.getInstance().reload();
            verify(plugin, times(1)).reloadConfig();
            mockedLogger.verify(() -> LoggerUtils.logInfo("Configuration reloaded."), times(1));
        }
    }

    @Test
    void getDefaultNpcPropertiesShouldReadValuesFromConfig() {
        ConfigTestSupport.initWith(Map.of(
                "npc.defaults.entity.prefix", "<gray>[Bot]",
                "npc.defaults.entity.tabPrefix", "<green>◆",
                "npc.defaults.entity.tabListOrder", -2222,
                "npc.defaults.entity.damageable", false,
                "npc.defaults.entity.respawnOnDeath", false,
                "npc.defaults.entity.chatEnabled", false,
                "npc.defaults.entity.listedInTab", false,
                "npc.defaults.entity.alwaysUseNameHologram", true
        ));

        NPCProperties properties = ConfigHandler.getInstance().getDefaultNPCProperties();
        assertEquals("<gray>[Bot]", properties.getPrefix());
        assertEquals("<green>◆", properties.getTabPrefix());
        assertEquals(-2222, properties.getTabListOrder());
        assertEquals(false, properties.isDamageable());
        assertEquals(false, properties.isRespawnOnDeath());
        assertEquals(false, properties.isChatEnabled());
        assertEquals(false, properties.isListedInTab());
        assertEquals(true, properties.isAlwaysUseNameHologram());
    }
}
