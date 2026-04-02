package nl.hauntedmc.ailex.config;

import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.npc.NPCData;
import nl.hauntedmc.ailex.npc.NPCProperties;
import nl.hauntedmc.ailex.testutil.ConfigTestSupport;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataHandlerTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        ConfigTestSupport.reset();
    }

    @Test
    void shouldSaveAndLoadNpcData() throws Exception {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        File pluginDataFolder = tempDir.toFile();
        File dataFile = new File(pluginDataFolder, "data.yml");

        when(plugin.getDataFolder()).thenReturn(pluginDataFolder);
        doAnswer(invocation -> {
            if (!dataFile.exists()) {
                boolean ignored = dataFile.createNewFile();
            }
            return null;
        }).when(plugin).saveResource("data.yml", false);

        DataHandler.init(plugin);

        NPCData npcData = new NPCData(
                5,
                "UnitNPC",
                new Location(null, 10, 20, 30),
                "nl.hauntedmc.ailex.npc.impl.AilexNPC",
                new NPCProperties(
                        "<gray>[Bot]",
                        "<green>◆",
                        -321,
                        false,
                        false,
                        false,
                        false,
                        true,
                        "system prompt",
                        "template {player_name} {chat_message}"
                )
        );
        DataHandler.saveNPC(npcData);

        Map<Integer, NPCData> loaded = DataHandler.loadNPCs();

        assertEquals(1, loaded.size());
        NPCData loadedNpc = loaded.get(5);
        assertNotNull(loadedNpc);
        assertEquals("UnitNPC", loadedNpc.getName());
        assertNotNull(loadedNpc.getSpawnLocation());
        assertEquals("nl.hauntedmc.ailex.npc.impl.AilexNPC", loadedNpc.getNpcClass());
        assertEquals("<gray>[Bot]", loadedNpc.getProperties().getPrefix());
        assertEquals("<green>◆", loadedNpc.getProperties().getTabPrefix());
        assertEquals(-321, loadedNpc.getProperties().getTabListOrder());
        assertEquals(false, loadedNpc.getProperties().isDamageable());
        assertEquals(false, loadedNpc.getProperties().isRespawnOnDeath());
        assertEquals(false, loadedNpc.getProperties().isChatEnabled());
        assertEquals(false, loadedNpc.getProperties().isListedInTab());
        assertEquals(true, loadedNpc.getProperties().isAlwaysUseNameHologram());
        assertEquals("system prompt", loadedNpc.getProperties().getSystemPrompt());
        assertEquals("template {player_name} {chat_message}",
                loadedNpc.getProperties().getUserPromptTemplate());
    }

    @Test
    void shouldRemoveNpcDataEntry() throws Exception {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        File pluginDataFolder = tempDir.toFile();
        File dataFile = new File(pluginDataFolder, "data.yml");

        when(plugin.getDataFolder()).thenReturn(pluginDataFolder);
        doAnswer(invocation -> {
            if (!dataFile.exists()) {
                boolean ignored = dataFile.createNewFile();
            }
            return null;
        }).when(plugin).saveResource("data.yml", false);

        DataHandler.init(plugin);

        NPCData npcData = new NPCData(9, "ToRemove", new Location(null, 1, 2, 3), "npc.class");
        DataHandler.saveNPC(npcData);
        DataHandler.removeNPC(9);

        Map<Integer, NPCData> loaded = DataHandler.loadNPCs();
        assertNull(loaded.get(9));
    }

    @Test
    void shouldReturnEmptyNpcMapWhenDataFileHasNoNpcSection() throws Exception {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        File pluginDataFolder = tempDir.toFile();
        File dataFile = new File(pluginDataFolder, "data.yml");

        when(plugin.getDataFolder()).thenReturn(pluginDataFolder);
        doAnswer(invocation -> {
            if (!dataFile.exists()) {
                boolean ignored = dataFile.createNewFile();
            }
            return null;
        }).when(plugin).saveResource("data.yml", false);

        DataHandler.init(plugin);
        Map<Integer, NPCData> loaded = DataHandler.loadNPCs();
        assertTrue(loaded.isEmpty());
    }

    @Test
    void shouldLoadLegacyNpcEntryWithConfiguredDefaultProperties() throws Exception {
        ConfigTestSupport.initWith(Map.of(
                "npc.defaults.entity.prefix", "<gray>[LegacyDefault]",
                "npc.defaults.entity.tabPrefix", "<green>■",
                "npc.defaults.entity.tabListOrder", -5555,
                "npc.defaults.entity.damageable", false,
                "npc.defaults.entity.respawnOnDeath", false,
                "npc.defaults.entity.chatEnabled", false,
                "npc.defaults.entity.listedInTab", false,
                "npc.defaults.entity.alwaysUseNameHologram", true,
                "npc.defaults.entity.prompts.systemPrompt", "default system prompt",
                "npc.defaults.entity.prompts.userPromptTemplate", "default template {npc_name}"
        ));

        AIlexPlugin plugin = mock(AIlexPlugin.class);
        File pluginDataFolder = tempDir.toFile();
        File dataFile = new File(pluginDataFolder, "data.yml");

        when(plugin.getDataFolder()).thenReturn(pluginDataFolder);
        doAnswer(invocation -> {
            if (!dataFile.exists()) {
                boolean ignored = dataFile.createNewFile();
            }
            return null;
        }).when(plugin).saveResource("data.yml", false);

        DataHandler.init(plugin);

        YamlConfiguration legacyYaml = new YamlConfiguration();
        legacyYaml.set("npcs.2.name", "LegacyNPC");
        legacyYaml.set("npcs.2.spawn-location", new Location(null, 1, 2, 3));
        legacyYaml.set("npcs.2.class", "legacy.class");
        legacyYaml.save(dataFile);
        DataHandler.init(plugin);

        Map<Integer, NPCData> loaded = DataHandler.loadNPCs();
        NPCData loadedNpc = loaded.get(2);
        assertNotNull(loadedNpc);
        assertEquals("LegacyNPC", loadedNpc.getName());
        assertEquals("<gray>[LegacyDefault]", loadedNpc.getProperties().getPrefix());
        assertEquals("<green>■", loadedNpc.getProperties().getTabPrefix());
        assertEquals(-5555, loadedNpc.getProperties().getTabListOrder());
        assertEquals(false, loadedNpc.getProperties().isDamageable());
        assertEquals(false, loadedNpc.getProperties().isRespawnOnDeath());
        assertEquals(false, loadedNpc.getProperties().isChatEnabled());
        assertEquals(false, loadedNpc.getProperties().isListedInTab());
        assertEquals(true, loadedNpc.getProperties().isAlwaysUseNameHologram());
        assertEquals("default system prompt", loadedNpc.getProperties().getSystemPrompt());
        assertEquals("default template {npc_name}", loadedNpc.getProperties().getUserPromptTemplate());
    }
}
