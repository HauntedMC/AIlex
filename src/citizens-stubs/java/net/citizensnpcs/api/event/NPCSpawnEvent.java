package net.citizensnpcs.api.event;

import net.citizensnpcs.api.npc.NPC;

import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** Minimal compile-time contract for Citizens NPC spawn events used by AIlex. */
public class NPCSpawnEvent extends NPCEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;
    private final Location location;
    private final SpawnReason reason;

    /**
     * Creates a spawn event contract matching Citizens' public constructor.
     *
     * @param npc involved NPC
     * @param location spawn location
     * @param reason spawn reason
     */
    public NPCSpawnEvent(NPC npc, Location location, SpawnReason reason) {
        super(npc);
        this.location = location;
        this.reason = reason;
    }

    /** @return spawned location */
    public Location getLocation() {
        return location.clone();
    }

    /** @return Citizens spawn reason */
    public SpawnReason getReason() {
        return reason;
    }

    /** @return whether the event is cancelled */
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /** @param cancelled whether the event is cancelled */
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /** @return Bukkit handler list */
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /** @return Bukkit handler list */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
