package net.citizensnpcs.api;

import net.citizensnpcs.api.npc.NPCRegistry;

/**
 * Compile-time contract for the Citizens API. The real class is supplied by the Citizens server plugin.
 */
public final class CitizensAPI {

    private CitizensAPI() {
    }

    /**
     * Returns the default Citizens NPC registry.
     *
     * @return the default registry
     */
    public static NPCRegistry getNPCRegistry() {
        throw new IllegalStateException("Citizens compile stub must not be used at runtime.");
    }
}
