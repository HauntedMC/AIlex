package net.citizensnpcs.trait;

import net.citizensnpcs.api.trait.Trait;

/**
 * Minimal compile-time contract for Citizens' SkinTrait used by AIlex.
 */
public class SkinTrait extends Trait {

    /** Creates the compile-time skin trait contract. */
    public SkinTrait() {
        super("skintrait");
    }

    /** @param name player name whose skin Citizens should use */
    public void setSkinName(String name) {
    }

    /**
     * Applies signed skin texture data.
     *
     * @param skinName cache key/name
     * @param signature texture signature
     * @param data encoded texture data
     */
    public void setSkinPersistent(String skinName, String signature, String data) {
    }
}
