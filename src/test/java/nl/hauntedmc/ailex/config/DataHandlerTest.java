package nl.hauntedmc.ailex.config;

import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.npc.NPCData;
import org.bukkit.Location;
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

        NPCData npcData = new NPCData(5, "UnitNPC", new Location(null, 10, 20, 30), "nl.hauntedmc.ailex.npc.impl.AilexNPC");
        DataHandler.saveNPC(npcData);

        Map<Integer, NPCData> loaded = DataHandler.loadNPCs();

        assertEquals(1, loaded.size());
        NPCData loadedNpc = loaded.get(5);
        assertNotNull(loadedNpc);
        assertEquals("UnitNPC", loadedNpc.getName());
        assertNotNull(loadedNpc.getSpawnLocation());
        assertEquals("nl.hauntedmc.ailex.npc.impl.AilexNPC", loadedNpc.getNpcClass());
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
}
