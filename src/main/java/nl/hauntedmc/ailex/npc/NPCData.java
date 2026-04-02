package nl.hauntedmc.ailex.npc;

import org.bukkit.Location;

/**
 * This class represents the data of an NPC.
 */
public class NPCData {

    private final int id;
    private String name;
    private Location spawnLocation;
    private final String npcClass;
    private NPCProperties properties;

    /**
     * Constructor for the NPCData
     * @param id The id of the NPC
     * @param name The name of the NPC
     * @param spawnLocation The spawn location of the NPC
     * @param npcClass The class of the NPC
     */
    public NPCData(int id, String name, Location spawnLocation, String npcClass) {
        this(id, name, spawnLocation, npcClass, NPCProperties.defaultValues());
    }

    /**
     * Constructor for the NPCData with explicit properties.
     * @param id The id of the NPC
     * @param name The name of the NPC
     * @param spawnLocation The spawn location of the NPC
     * @param npcClass The class of the NPC
     * @param properties Runtime and display properties of the NPC
     */
    public NPCData(int id, String name, Location spawnLocation, String npcClass, NPCProperties properties) {
        this.id = id;
        this.name = name;
        this.spawnLocation = spawnLocation;
        this.npcClass = npcClass;
        this.properties = properties == null ? NPCProperties.defaultValues() : properties;
    }

    /**
     * Get the name of the NPC
     * @return The name of the NPC
     */
    public String getName() {
        return name;
    }

    /**
     * Get the spawn location of the NPC
     * @return The spawn location of the NPC
     */
    public Location getSpawnLocation() {
        return spawnLocation;
    }

    /**
     * Get the class of the NPC
     * @return The class of the NPC
     */
    public String getNpcClass() {
        return npcClass;
    }

    /**
     * Get the id of the NPC
     * @return The id of the NPC
     */
    public int getId() {
        return id;
    }

    /**
     * Get the properties of the NPC.
     * @return The properties of the NPC
     */
    public NPCProperties getProperties() {
        return properties;
    }

    /**
     * Set the name of the NPC
     * @param name The new name of the NPC
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Set the spawn location of the NPC
     * @param location The new spawn location of the NPC
     */
    public void setSpawnLocation(Location location) {
        spawnLocation = location;
    }

    /**
     * Set the properties of the NPC.
     * @param properties The new properties for the NPC
     */
    public void setProperties(NPCProperties properties) {
        this.properties = properties == null ? NPCProperties.defaultValues() : properties;
    }

    /**
     * Checks if the NPCData is valid
     * @return true if the NPCData is valid, false otherwise
     */
    public boolean isValid() {
        boolean valid = true;
        
        if (id < 0) {
            valid = false;
        }
        if (name == null || name.isEmpty()) {
            valid = false;
        }
        if (spawnLocation == null) {
            valid = false;
        }
        if (npcClass == null || npcClass.isEmpty()) {
            valid = false;
        }
        if (properties == null || !properties.isValid()) {
            valid = false;
        }

        return valid;
    }
}
