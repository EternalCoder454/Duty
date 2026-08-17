package net.dutymod.fixerupper;

/**
 * Loading state that loader-neutral code needs to read and only the loader can know.
 *
 * <p>{@link #registryEventsFired} was a field on the NeoForge entry point, read by two mixins that
 * are otherwise loader-neutral. That is the ordinary way a loader leaks into shared code: not
 * through an import of the loader's API, but through a flag that only the loader's event bus can
 * set. Moving the flag here and leaving the entry point to set it keeps the dependency pointing one
 * way -- loader code knows about shared code, never the reverse.
 *
 * <p>Fabric has no registry events in this sense; its implementation sets the flag once the
 * equivalent point in its own lifecycle is reached.
 */
public final class FixerUpperState {
    /**
     * Whether the loader has finished firing its registry events.
     *
     * <p>Before this point, registries are still being populated, so caches keyed on registry
     * contents must not be built and resource reloads must not be treated as final.
     *
     * <p>Written once from the loader's entry point and read from mixins on other threads, so it is
     * {@code volatile}: the writing thread is not the one that reads it.
     */
    public static volatile boolean registryEventsFired = false;

    private FixerUpperState() {}
}
