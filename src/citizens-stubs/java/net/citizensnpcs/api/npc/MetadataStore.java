package net.citizensnpcs.api.npc;

/**
 * Minimal compile-time contract for Citizens NPC metadata used by AIlex.
 */
public interface MetadataStore {

    /**
     * Reads metadata or returns the supplied default.
     *
     * @param key metadata key
     * @param defaultValue default value
     * @param <T> value type
     * @return stored value or the default
     */
    <T> T get(String key, T defaultValue);

    /**
     * Stores persistent metadata.
     *
     * @param key metadata key
     * @param data value to persist
     */
    void setPersistent(String key, Object data);
}
