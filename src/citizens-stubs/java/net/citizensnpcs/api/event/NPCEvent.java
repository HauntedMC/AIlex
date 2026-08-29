package net.citizensnpcs.api.event;

import net.citizensnpcs.api.npc.NPC;

/** Minimal compile-time base for Citizens NPC events. */
public abstract class NPCEvent extends CitizensEvent {

    private final NPC npc;

    /**
     * Creates an NPC event contract.
     *
     * @param npc involved Citizens NPC
     */
    protected NPCEvent(NPC npc) {
        this.npc = npc;
    }

    /**
     * Returns the Citizens NPC involved in this event.
     *
     * @return involved NPC
     */
    public NPC getNPC() {
        return npc;
    }
}
