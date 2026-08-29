package net.citizensnpcs.api.trait;

/**
 * Minimal compile-time base type matching Citizens' trait type erasure.
 */
public abstract class Trait {

    /**
     * Creates a compile-time trait contract.
     *
     * @param name trait name
     */
    protected Trait(String name) {
    }
}
