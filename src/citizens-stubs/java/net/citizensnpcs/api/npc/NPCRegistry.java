package net.citizensnpcs.api.npc;

import org.bukkit.entity.EntityType;

/**
 * Minimal compile-time contract for the Citizens NPC registry used by AIlex.
 */
public interface NPCRegistry extends Iterable<NPC> {

    /**
     * Creates an unspawned Citizens NPC.
     *
     * @param type Bukkit entity type
     * @param name NPC name
     * @return created NPC
     */
    NPC createNPC(EntityType type, String name);

    /**
     * Deregisters an NPC.
     *
     * @param npc NPC to remove
     */
    void deregister(NPC npc);

    /** Persists the registry to its Citizens store. */
    void saveToStore();
}
