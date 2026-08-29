package net.citizensnpcs.api.event;

import org.bukkit.event.Event;

/** Minimal compile-time base for Citizens events. */
public abstract class CitizensEvent extends Event {

    /** Creates a synchronous Citizens event contract. */
    protected CitizensEvent() {
    }

    /**
     * Creates a Citizens event contract.
     *
     * @param async whether the event is asynchronous
     */
    protected CitizensEvent(boolean async) {
        super(async);
    }
}
