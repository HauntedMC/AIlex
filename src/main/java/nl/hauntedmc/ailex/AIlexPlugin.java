package nl.hauntedmc.ailex;

import nl.hauntedmc.ailex.assistant.adapter.paper.AssistantChatListener;
import nl.hauntedmc.ailex.assistant.application.AssistantService;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantEventMemoryService;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantMemoryService;
import nl.hauntedmc.ailex.assistant.runtime.AssistantRequestTracer;
import nl.hauntedmc.ailex.command.MainCommand;
import nl.hauntedmc.ailex.config.ConfigHandler;
import nl.hauntedmc.ailex.config.DataHandler;
import nl.hauntedmc.ailex.infrastructure.openai.OpenAiResponsesClient;
import nl.hauntedmc.ailex.listener.citizens.NPCDeathListener;
import nl.hauntedmc.ailex.listener.citizens.NPCSpawnListener;
import nl.hauntedmc.ailex.listener.player.PlayerJoinListener;
import nl.hauntedmc.ailex.listener.player.PlayerLeaveListener;
import nl.hauntedmc.ailex.npc.lifecycle.NpcManager;
import nl.hauntedmc.ailex.util.LoggerUtils;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Main plugin entrypoint and composition root. */
public class AIlexPlugin extends JavaPlugin {

    private NpcManager npcManager;
    private OpenAiResponsesClient openAiResponsesClient;
    private AssistantMemoryService assistantMemoryService;
    private AssistantEventMemoryService assistantEventMemoryService;
    private AssistantService assistantService;
    private AssistantRequestTracer assistantRequestTracer;
    private AssistantChatListener assistantChatListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveBuiltInKnowledge();
        // Memory V2 creates assistant-memory.db itself. Existing 1.4 YAML files in the data folder are
        // migration inputs only and are intentionally never recreated or overwritten by 1.5.

        ConfigHandler.init(this);
        DataHandler.init(this);
        assistantRequestTracer = new AssistantRequestTracer(
                () -> getConfig().getBoolean("openai.assistant.observability.enabled", true),
                () -> getConfig().getBoolean("openai.assistant.observability.include_requester_name", true)
        );
        openAiResponsesClient = new OpenAiResponsesClient(this);
        assistantMemoryService = new AssistantMemoryService(this);
        assistantEventMemoryService = new AssistantEventMemoryService(this);
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
        if (assistantChatListener != null) {
            assistantChatListener.close();
            assistantChatListener = null;
        }
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
        assistantChatListener = new AssistantChatListener(this);
        getServer().getPluginManager().registerEvents(assistantChatListener, this);
        assistantChatListener.startProactiveConversationChecks();
        if (assistantEventMemoryService != null) {
            getServer().getPluginManager().registerEvents(assistantEventMemoryService, this);
        }
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

    /** Public integration surface for meaningful custom HauntedMC events. */
    public AssistantEventMemoryService getAssistantEventMemoryService() {
        return assistantEventMemoryService;
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
