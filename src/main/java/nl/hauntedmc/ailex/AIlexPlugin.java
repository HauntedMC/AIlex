package nl.hauntedmc.ailex;

import nl.hauntedmc.ailex.command.MainCommand;
import nl.hauntedmc.ailex.config.ConfigHandler;
import nl.hauntedmc.ailex.config.DataHandler;
import nl.hauntedmc.ailex.listener.llm.LLMChatListener;
import nl.hauntedmc.ailex.listener.citizens.NPCDeathListener;
import nl.hauntedmc.ailex.listener.citizens.NPCSpawnListener;
import nl.hauntedmc.ailex.listener.player.PlayerJoinListener;
import nl.hauntedmc.ailex.listener.player.PlayerLeaveListener;
import nl.hauntedmc.ailex.npc.lifecycle.NpcManager;
import nl.hauntedmc.ailex.util.LoggerUtils;
import nl.hauntedmc.ailex.infrastructure.openai.OpenAiResponsesClient;
import nl.hauntedmc.ailex.assistant.application.AssistantService;
import nl.hauntedmc.ailex.assistant.application.command.AssistantMemoryCommand;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantMemoryService;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;

/**
 * Main class of the AIlex plugin
 * This class is responsible for initializing the plugin and registering all commands
 */
public class AIlexPlugin extends JavaPlugin {

    private NpcManager npcManager;
    private OpenAiResponsesClient openAiResponsesClient;
    private AssistantMemoryService assistantMemoryService;
    private AssistantService assistantService;

    /**
     * Called when the plugin is enabled
     * This method initializes the plugin and registers all commands and listeners
     */
    @Override
    public void onEnable() {
        // Save the default config
        saveDefaultConfig();
        saveResource("knowledge/README.md", false);

        // Initialize different parts of the plugin
        ConfigHandler.init(this);
        DataHandler.init(this);
        openAiResponsesClient = new OpenAiResponsesClient(this);
        assistantMemoryService = new AssistantMemoryService(this);
        assistantService = new AssistantService(this);
        npcManager = new NpcManager(this::isNpcEnabled);

        // Register all commands and listeners
        registerCommands();
        registerListeners();

        // Delay NPC loading one tick so all dependent plugins have fully enabled.
        getServer().getScheduler().runTask(this, () -> {
            if (isEnabled() && isNpcEnabled()) {
                npcManager.loadNPCs();
            }
        });

        LoggerUtils.logInfo("AIlex has been enabled");
    }

    /**
     * Called when the plugin is disabled
     * Clean up all resources and remove all NPCs from the world
     */
    @Override
    public void onDisable() {
        if (npcManager != null) {
            // Unload all NPCs
            npcManager.unloadAllNPCs();

            // Clear the NPCRegistry after removing all NPCs
            npcManager.clearNPCRegistry();
        }

        LoggerUtils.logInfo("AIlex has been disabled");
    }

    /**
     * Register all commands that are part of the plugin
     * Here you must register new commands
     */
    private void registerCommands() {
        PluginCommand ailexCommand = getCommand("ailex");
        if (ailexCommand == null) {
            throw new IllegalStateException("The ailex command is missing from plugin.yml");
        }

        MainCommand mainCommand = new MainCommand(this);
        ailexCommand.setExecutor(mainCommand);
        ailexCommand.setTabCompleter(mainCommand);

        PluginCommand memoryCommand = getCommand("ailexmemory");
        if (memoryCommand == null) {
            throw new IllegalStateException("The ailexmemory command is missing from plugin.yml");
        }
        memoryCommand.setExecutor(new AssistantMemoryCommand(assistantMemoryService));
    }

    /**
     * Register all listeners that are used
     */
    private void registerListeners() {
        // Register here all listeners
        LLMChatListener chatListener = new LLMChatListener(this);
        getServer().getPluginManager().registerEvents(chatListener, this);
        chatListener.startProactiveConversationChecks();
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
     * Returns the lifecycle manager for AIlex-owned NPCs.
     * @return the NPC lifecycle manager
     */
    public NpcManager getNpcManager() {
        return npcManager;
    }

    /** Returns whether AIlex may create and manage physical Citizens NPCs. */
    public boolean isNpcEnabled() {
        return getConfig().getBoolean("npc.enabled", true);
    }

    /**
     * Returns the configured OpenAI Responses API client.
     * @return the OpenAI Responses API client
     */
    public OpenAiResponsesClient getOpenAiResponsesClient() {
        return openAiResponsesClient;
    }

    /** Returns the bounded read-only assistant application service. */
    public AssistantService getAssistantService() {
        return assistantService;
    }

    /** Returns player-controlled preference memory. */
    public AssistantMemoryService getAssistantMemoryService() {
        return assistantMemoryService;
    }

    /**
     * Recreate the OpenAI client after configuration changes.
     */
    public void reloadOpenAiResponsesClient() {
        openAiResponsesClient = new OpenAiResponsesClient(this);
        if (assistantService != null) {
            assistantService.reload();
        }
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
