package net.dutymod.memory.blockstate;

/** Implemented by {@code StateHolder} via mixin, so the state can be given its shared table. */
public interface FastMapStateHolder<S> {
    void duty$setStateMap(FastMap<S> stateMap, int tableIndex);
}
