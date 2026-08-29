package net.citizensnpcs.api.npc;

import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.event.SpawnReason;
import net.citizensnpcs.api.trait.Trait;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.UUID;

/**
 * Minimal binary-signature-compatible Citizens NPC contract used to compile AIlex.
 */
public interface NPC {

    /** @return the NPC metadata store */
    MetadataStore data();

    /**
     * Despawns this NPC.
     *
     * @param reason despawn reason
     * @return whether the NPC despawned
     */
    boolean despawn(DespawnReason reason);

    /** @return the current Bukkit entity, or null while despawned */
    Entity getEntity();

    /**
     * Gets or creates a Citizens trait.
     *
     * @param trait trait class
     * @param <T> trait type
     * @return trait instance
     */
    <T extends Trait> T getOrAddTrait(Class<T> trait);

    /** @return globally unique Citizens NPC id */
    UUID getUniqueId();

    /** @return whether a Bukkit entity currently exists */
    boolean isSpawned();

    /** @param use whether to always use a name hologram */
    void setAlwaysUseNameHologram(boolean use);

    /** @param flyable whether the NPC is flyable */
    void setFlyable(boolean flyable);

    /** @param name NPC name */
    void setName(String name);

    /** @param isProtected whether Citizens should protect the NPC */
    void setProtected(boolean isProtected);

    /** @param use whether to use vanilla Minecraft AI */
    void setUseMinecraftAI(boolean use);

    /**
     * Attempts to spawn the NPC.
     *
     * @param location target location
     * @param reason spawn reason
     * @return whether the NPC spawned
     */
    boolean spawn(Location location, SpawnReason reason);
}
