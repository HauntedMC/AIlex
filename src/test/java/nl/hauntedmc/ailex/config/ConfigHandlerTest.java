package nl.hauntedmc.ailex.config;

import nl.hauntedmc.ailex.util.LoggerUtils;
import nl.hauntedmc.ailex.testutil.ConfigTestSupport;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

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
}
