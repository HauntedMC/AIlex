package nl.hauntedmc.ailex.config;

import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.npc.NPCData;
import nl.hauntedmc.ailex.npc.NPCProperties;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSaveAndLoadCurrentNpcDataSchema() throws Exception {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        File pluginDataFolder = tempDir.toFile();
        File dataFile = new File(pluginDataFolder, "data.yml");

        when(plugin.getDataFolder()).thenReturn(pluginDataFolder);
        createDataFileOnFirstUse(plugin, dataFile);
        DataHandler.init(plugin);

        NPCData npcData = new NPCData(
                5,
                "UnitNPC",
                new Location(null, 10, 20, 30),
                "nl.hauntedmc.ailex.npc.impl.AilexNPC",
                new NPCProperties("<gray>[Bot]", "<green>◆", -321, false, false, false, false, true,
                        "system prompt", "template {player_name} {chat_message}")
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
        assertEquals("template {player_name} {chat_message}", loadedNpc.getProperties().getUserPromptTemplate());
    }

    @Test
    void shouldRemoveNpcDataEntry() throws Exception {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        File pluginDataFolder = tempDir.toFile();
        File dataFile = new File(pluginDataFolder, "data.yml");

        when(plugin.getDataFolder()).thenReturn(pluginDataFolder);
        createDataFileOnFirstUse(plugin, dataFile);
        DataHandler.init(plugin);

        DataHandler.saveNPC(new NPCData(9, "ToRemove", new Location(null, 1, 2, 3), "npc.class"));
        DataHandler.removeNPC(9);

        assertNull(DataHandler.loadNPCs().get(9));
    }

    @Test
    void shouldReturnEmptyNpcMapWhenDataFileHasNoNpcSection() throws Exception {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        File pluginDataFolder = tempDir.toFile();
        File dataFile = new File(pluginDataFolder, "data.yml");

        when(plugin.getDataFolder()).thenReturn(pluginDataFolder);
        createDataFileOnFirstUse(plugin, dataFile);
        DataHandler.init(plugin);

        assertTrue(DataHandler.loadNPCs().isEmpty());
    }

    private void createDataFileOnFirstUse(AIlexPlugin plugin, File dataFile) throws Exception {
        doAnswer(invocation -> {
            if (!dataFile.exists()) {
                boolean ignored = dataFile.createNewFile();
            }
            return null;
        }).when(plugin).saveResource("data.yml", false);
    }
}
