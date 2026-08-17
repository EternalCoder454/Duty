package net.dutymod.framework.event;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A callback list other mods can register into.
 *
 * <p>This is the Fabric-style array-backed event, and it is here for one reason: a mod that exposes
 * an API needs a place for other mods to hook, and that place should not be a mod-loader event bus
 * (which ties the API to a loader) or a library brought in for this alone.
 *
 * <p>The shape is deliberate. Registered callbacks are collected into a plain array and folded into
 * a <em>single</em> instance of the callback interface by the {@code combiner} given at
 * construction. Firing the event is then one virtual call on that instance -- not a loop over a
 * list, not an iterator, not a stream. That matters because these fire on gameplay paths: Liteminer
 * asks {@code ALLOW_BLOCK} once per candidate block in a vein, which is thousands of calls for one
 * mining action.
 *
 * <p>The combiner also decides what "several callbacks" means, which differs per event and cannot
 * be generic: one event stops at the first non-{@code PASS} result, another runs all of them and
 * ignores results. Writing that per event is the point rather than a limitation.
 *
 * <p><b>Registration is not thread-safe and does not need to be.</b> Events are registered during
 * mod setup, from one thread, and read for the rest of the run. The invoker field is
 * {@code volatile} so a callback registered late is visible to threads already firing the event.
 *
 * @param <T> the callback interface
 */
public final class DutyEvent<T> {
    private final Class<T> type;
    private final Function<T[], T> combiner;
    private final List<T> callbacks = new ArrayList<>();

    private volatile T invoker;

    private DutyEvent(Class<T> type, Function<T[], T> combiner) {
        this.type = type;
        this.combiner = combiner;
        rebuild();
    }

    /**
     * Creates an event.
     *
     * @param type     the callback interface, needed to build a correctly typed array
     * @param combiner folds the registered callbacks into one; see the class note on why this is
     *                 supplied per event rather than chosen here
     */
    public static <T> DutyEvent<T> create(Class<T> type, Function<T[], T> combiner) {
        return new DutyEvent<>(type, combiner);
    }

    /** Adds a callback. Call during setup. */
    public void register(T callback) {
        if (callback == null) {
            throw new NullPointerException("Cannot register a null callback to a " + type.getSimpleName() + " event");
        }
        callbacks.add(callback);
        rebuild();
    }

    /**
     * {@return the combined callback}
     *
     * <p>Safe to hold onto only for the duration of a single fire: registering replaces it.
     */
    public T invoker() {
        return invoker;
    }

    @SuppressWarnings("unchecked")
    private void rebuild() {
        invoker = combiner.apply(callbacks.toArray((T[]) Array.newInstance(type, callbacks.size())));
    }
}
