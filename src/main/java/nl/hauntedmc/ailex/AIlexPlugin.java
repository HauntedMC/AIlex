package nl.hauntedmc.ailex;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;

import nl.hauntedmc.ailex.command.MainCommand;
import nl.hauntedmc.ailex.config.ConfigHandler;
import nl.hauntedmc.ailex.config.DataHandler;
import nl.hauntedmc.ailex.listener.llm.LLMChatListener;
import nl.hauntedmc.ailex.listener.citizens.NPCDeathListener;
import nl.hauntedmc.ailex.listener.citizens.NPCSpawnListener;
import nl.hauntedmc.ailex.listener.player.PlayerJoinListener;
import nl.hauntedmc.ailex.listener.player.PlayerLeaveListener;
import nl.hauntedmc.ailex.npc.NPCHandler;
import nl.hauntedmc.ailex.util.LoggerUtils;
import nl.hauntedmc.ailex.ai.llm.ChatGPTClient;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import org.jetbrains.annotations.NotNull;

/**
 * Main class of the AIlex plugin
 * This class is responsible for initializing the plugin and registering all commands
 */
public class AIlexPlugin extends JavaPlugin {

    private NPCHandler npcHandler;
    private ChatGPTClient chatGPTClient;
    @NotNull private LifecycleEventManager<Plugin> manager = this.getLifecycleManager();

    /**
     * Called when the plugin is loaded
     * Warning: No heavy operations should be done here, see onEnable for that
     */
    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().reEncodeByDefault(false)
                .checkForUpdates(true);
        PacketEvents.getAPI().load();
    }


    /**
     * Called when the plugin is enabled
     * This method initializes the plugin and registers all commands and listeners
     */
    @Override
    public void onEnable() {
        // Save the default config
        saveDefaultConfig();

        // Initialize different parts of the plugin
        ConfigHandler.init(this);
        DataHandler.init(this);
        chatGPTClient = new ChatGPTClient(this);

        // Register all commands and listeners
        registerCommands();
        registerListeners();

        // Initialize PacketEvents
        PacketEvents.getAPI().init();

        // Load all NPCs from the database
        npcHandler = new NPCHandler();
        npcHandler.loadNPCs();

        LoggerUtils.logInfo("AIlex has been enabled");
    }

    /**
     * Called when the plugin is disabled
     * Clean up all resources and remove all NPCs from the world
     */
    @Override
    public void onDisable() {
        // Unload all NPCs
        npcHandler.unloadAllNPCs();

        // Clear the NPCRegistry after removing all NPCs
        npcHandler.clearNPCRegistry();

        // Terminate PacketEvents
        PacketEvents.getAPI().terminate();

        LoggerUtils.logInfo("AIlex has been disabled");
    }

    /**
     * Register all commands that are part of the plugin
     * Here you must register new commands
     */
    private void registerCommands() {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            // Register here all commands that are part of the plugin
            commands.register("ailex", "Main command for AIlex", new MainCommand(this));
        });
    }

    /**
     * Register all listeners that are used
     */
    private void registerListeners() {
        // Register here all listeners
        getServer().getPluginManager().registerEvents(new LLMChatListener(this), this);
        getServer().getPluginManager().registerEvents(new NPCDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new NPCSpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerLeaveListener(this), this);

        registerPacketEventsListeners();
    }

    /**
     * Register all PacketEvents listeners
     */
    private void registerPacketEventsListeners() {
        // Register PacketEvents listeners here
        //PacketEvents.getAPI().getEventManager().registerListener(new PacketTestListener());
    }

    /**
     * Get the NPC handler
     * @return The NPC handler
     */
    public NPCHandler getNPCHandler() {
        return npcHandler;
    }

    /**
     * Get the ChatGPTClient
     * @return The ChatGPTClient
     */
    public ChatGPTClient getChatGPTClient() {
        return chatGPTClient;
    }

    /**
     * Get the AIlex plugin
     * Note: If possible pass this instance to other classes instead of using this method
     * @return The AIlex plugin
     */
    public static AIlexPlugin getPlugin() {
        return getPlugin(AIlexPlugin.class);
    }
}
