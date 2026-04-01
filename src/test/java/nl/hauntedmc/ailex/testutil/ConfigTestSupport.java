package nl.hauntedmc.ailex.testutil;

import nl.hauntedmc.ailex.config.ConfigHandler;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ConfigTestSupport {

    private ConfigTestSupport() {
    }

    public static FileConfiguration initWith(Map<String, Object> values) {
        reset();
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration configuration = new YamlConfiguration();
        values.forEach(configuration::set);
        when(plugin.getConfig()).thenReturn(configuration);
        ConfigHandler.init(plugin);
        return configuration;
    }

    public static void reset() {
        try {
            Field instanceField = ConfigHandler.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to reset ConfigHandler singleton for tests", exception);
        }
    }
}
