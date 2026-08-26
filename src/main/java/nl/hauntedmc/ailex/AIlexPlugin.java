package nl.hauntedmc.ailex;

import nl.hauntedmc.ailex.command.MainCommand;
import nl.hauntedmc.ailex.config.ConfigHandler;
import nl.hauntedmc.ailex.config.DataHandler;
import nl.hauntedmc.ailex.listener.llm.LLMChatListener;
import nl.hauntedmc.ailex.listener.llm.AssistantRequestTracer;
import nl.hauntedmc.ailex.listener.citizens.NPCDeathListener;
import nl.hauntedmc.ailex.listener.citizens.NPCSpawnListener;
import nl.hauntedmc.ailex.listener.player.PlayerJoinListener;
import nl.hauntedmc.ailex.listener.player.PlayerLeaveListener;
import nl.hauntedmc.ailex.npc.lifecycle.NpcManager;
import nl.hauntedmc.ailex.util.LoggerUtils;
import nl.hauntedmc.ailex.infrastructure.openai.OpenAiResponsesClient;
import nl.hauntedmc.ailex.assistant.application.AssistantService;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantMemoryService;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Main plugin entrypoint. */
public class AIlexPlugin extends JavaPlugin {

    private NpcManager npcManager;
    private OpenAiResponsesClient openAiResponsesClient;
    private AssistantMemoryService assistantMemoryService;
    private AssistantService assistantService;
    private AssistantRequestTracer assistantRequestTracer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveBuiltInKnowledge();
        // assistant-memory.yml / assistant-long-term-memory.yml are legacy migration inputs in 1.5.0.
        // Existing files are imported automatically by Memory V2; new installs use SQLite directly.
        saveResource("assistant-short-term-memory.yml", false);

        ConfigHandler.init(this);
        DataHandler.init(this);
        assistantRequestTracer = new AssistantRequestTracer();
        openAiResponsesClient = new OpenAiResponsesClient(this);
        assistantMemoryService = new AssistantMemoryService(this);
        assistantService = new AssistantService(this);
        npcManager = new NpcManager(this::isNpcEnabled);

        registerCommands();
        registerListeners();

        getServer().getScheduler().runTask(this, () -> {
            if (isEnabled() && isNpcEnabled()) {
                npcManager.loadNPCs();
            }
        });

        LoggerUtils.logInfo("AIlex has been enabled");
    }

    @Override
    public void onDisable() {
        if (npcManager != null) {
            npcManager.unloadAllNPCs();
            npcManager.clearNPCRegistry();
        }
        if (assistantMemoryService != null) {
            assistantMemoryService.close();
        }
        LoggerUtils.logInfo("AIlex has been disabled");
    }

    private void registerCommands() {
        PluginCommand ailexCommand = getCommand("ailex");
        if (ailexCommand == null) {
            throw new IllegalStateException("The ailex command is missing from plugin.yml");
        }
        MainCommand mainCommand = new MainCommand(this);
        ailexCommand.setExecutor(mainCommand);
        ailexCommand.setTabCompleter(mainCommand);
    }

    private void saveBuiltInKnowledge() {
        try (InputStream resource = getResource("knowledge/index.txt")) {
            if (resource == null) {
                throw new IllegalStateException("The bundled knowledge manifest is missing");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
                reader.lines().map(String::trim).filter(file -> file.endsWith(".md"))
                        .forEach(file -> saveResource("knowledge/" + file, false));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load the bundled knowledge manifest", exception);
        }
    }

    private void registerListeners() {
        LLMChatListener chatListener = new LLMChatListener(this);
        getServer().getPluginManager().registerEvents(chatListener, this);
        chatListener.startProactiveConversationChecks();
        getServer().getPluginManager().registerEvents(new NPCDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new NPCSpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerLeaveListener(this), this);
        registerPacketEventsListeners();
    }

    private void registerPacketEventsListeners() {
        // PacketEvents listeners are registered here when needed.
    }

    public NpcManager getNpcManager() {
        return npcManager;
    }

    public boolean isNpcEnabled() {
        return getConfig().getBoolean("npc.enabled", true);
    }

    public OpenAiResponsesClient getOpenAiResponsesClient() {
        return openAiResponsesClient;
    }

    public AssistantService getAssistantService() {
        return assistantService;
    }

    public AssistantMemoryService getAssistantMemoryService() {
        return assistantMemoryService;
    }

    public AssistantRequestTracer getAssistantRequestTracer() {
        return assistantRequestTracer;
    }

    public void reloadOpenAiResponsesClient() {
        openAiResponsesClient = new OpenAiResponsesClient(this);
        if (assistantService != null) {
            assistantService.reload();
        }
    }

    public static AIlexPlugin getPlugin() {
        return getPlugin(AIlexPlugin.class);
    }
}
