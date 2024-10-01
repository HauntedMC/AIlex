package nl.hauntedmc.ailex;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;

import org.bukkit.plugin.java.JavaPlugin;

import org.jetbrains.annotations.NotNull;

/**
 * Plugin bootstrap for the AIlex plugin
 */
public class AIlexPluginBootstrap implements PluginBootstrap {

    /**
     * Called when the plugin is bootstrapped
     * @param context the server provided context
     */
    @Override
    public void bootstrap(@NotNull BootstrapContext context) {

    }

    /**
     * Called when the plugin is created
     * Here parameters can be passed to the plugin which is otherwise not possible
     * @param context the server provided context
     * @return the plugin instance
     */
    @Override
    public @NotNull JavaPlugin createPlugin(@NotNull PluginProviderContext context) {
        return new AIlexPlugin();
    }

}
