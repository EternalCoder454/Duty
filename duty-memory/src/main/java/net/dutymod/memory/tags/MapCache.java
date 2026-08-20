package net.dutymod.memory.tags;

import com.google.common.collect.MapMaker;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public final class MapCache<K, V> {

    private final ConcurrentMap<K, V> map;
    private final Function<K, V> mapFunction;

    public MapCache(Function<K, V> mapFunction, ConcurrentMap<K, V> map) {
        this.mapFunction = mapFunction;
        this.map = map;
    }

    public V getCache(final K k) {
        return map.computeIfAbsent(k, mapFunction);
    }

    public static <K, V> Build<K, V> build(Function<K, V> mapFunction) {
        return new Build<>(mapFunction);
    }


    /**
     * Builds a cache, optionally over a {@link MapMaker} rather than a plain map.
     *
     * <p>Upstream also offered a key equivalence, set by reflecting into {@code MapMaker}'s
     * package-private {@code keyEquivalence} field. Nothing here ever asked for one -- FastTag
     * builds every cache with {@code maker(MapMaker::weakValues)} or with no options at all -- and
     * the code could not have worked if it had: the field is package-private and the reflection
     * never called {@code setAccessible}, so any caller would have got an
     * {@code IllegalAccessException} wrapped in a {@code RuntimeException}. {@code MapMaker} has a
     * public {@code keyEquivalence} method anyway.
     *
     * <p>What it did do was run a {@code getDeclaredField} lookup in a static initializer on every
     * load, where a rename in Guava would throw {@code ExceptionInInitializerError} and take
     * FastTag's interning down with it. So it was a liability that could only ever cost something.
     */
    public static final class Build<K, V> {

        private UnaryOperator<MapMaker> maker;
        private final Function<K, V> mapFunction;

        public Build(Function<K, V> mapFunction) {
            this.mapFunction = mapFunction;
        }

        // Written out rather than generated: upstream used Lombok's @Setter/@Accessors for this,
        // and Duty does not carry Lombok.
        public Build<K, V> maker(UnaryOperator<MapMaker> maker) {
            this.maker = maker;
            return this;
        }

        public MapCache<K, V> build() {
            if (maker == null) {
                return new MapCache<>(mapFunction, new ConcurrentHashMap<>());
            }
            return new MapCache<>(mapFunction, maker.apply(new MapMaker()).makeMap());
        }
    }

}
