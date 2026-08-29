package net.citizensnpcs.api.event;

import net.citizensnpcs.api.npc.NPC;

import org.bukkit.event.HandlerList;

/** Minimal compile-time contract for Citizens NPC death events used by AIlex. */
public class NPCDeathEvent extends NPCEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    /**
     * Creates a death event contract for tests or compile-time use.
     *
     * @param npc involved NPC
     */
    public NPCDeathEvent(NPC npc) {
        super(npc);
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
