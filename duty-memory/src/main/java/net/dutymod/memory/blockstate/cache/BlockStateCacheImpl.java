package net.dutymod.memory.blockstate.cache;

import com.google.common.base.Suppliers;
import it.unimi.dsi.fastutil.booleans.BooleanArrays;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.dutymod.memory.mixin.blockstatecache.ArrayVSAccess;
import net.dutymod.memory.mixin.blockstatecache.SliceShapeAccess;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class BlockStateCacheImpl {
    /**
     * How much sharing actually happened.
     *
     * <p>This module's whole claim is "the same shape is stored once instead of thousands of
     * times", and until now that claim was untestable from inside the game. These count the times
     * a state's shape or face-sturdy array was replaced by one that already existed -- every
     * increment is an object that did not stay on the heap.
     */
    private static final net.dutymod.framework.DutyMetrics.Counter SHAPES_SHARED =
            net.dutymod.framework.DutyMetrics.counter("memory.blockstate.shapes_shared");
    private static final net.dutymod.framework.DutyMetrics.Counter FACE_STURDY_SHARED =
            net.dutymod.framework.DutyMetrics.counter("memory.blockstate.face_sturdy_shared");

    public static final Map<ArrayVSAccess, ArrayVSAccess> CACHE_COLLIDE = new Object2ObjectOpenCustomHashMap<>(
            ArrayVoxelShapeHash.INSTANCE
    );
    public static final Map<boolean[], boolean[]> CACHE_FACE_STURDY = new Object2ObjectOpenCustomHashMap<>(
            BooleanArrays.HASH_STRATEGY
    );

    // Get the cache from a blockstate. Mixin does not handle private inner classes too well, so method handles and
    // manual remapping it is.
    //
    // Upstream resolves the field name through a per-loader hook, because it also builds against
    // intermediary and SRG names. Duty targets one version on one loader with Mojang mappings, so
    // the name is simply "cache" -- verified against 26.1.2 with
    //   javap -p net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase
    // The lookup stays behind Suppliers.memoize so it happens on first use rather than at class
    // init: this class is touched during block state construction, and reflecting on Minecraft
    // classes that early is how class-loading cycles start.
    private static final Supplier<Function<BlockStateBase, BlockStateCacheAccess>> GET_CACHE = Suppliers.memoize(() -> {
        try {
            final Field cacheField = BlockStateBase.class.getDeclaredField("cache");
            cacheField.setAccessible(true);
            MethodHandle getter = MethodHandles.lookup().unreflectGetter(cacheField);
            return state -> {
                try {
                    return (BlockStateCacheAccess) getter.invoke(state);
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
            };
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    });
    // Is set to the previous cache used by a state before updating the cache. If the new cache has shapes equivalent to
    // the ones in the old cache, we don't need to go through the map since the old one already had deduplicated shapes
    private static final ThreadLocal<BlockStateCacheAccess> LAST_CACHE = new ThreadLocal<>();

    // Calls before the cache for <code>state</code> is (re-)populated
    public static void deduplicateCachePre(BlockStateBase state) {
        LAST_CACHE.set(GET_CACHE.get().apply(state));
    }

    // Calls after the cache for <code>state</code> is (re-)populated
    public static void deduplicateCachePost(BlockStateBase state) {
        BlockStateCacheAccess newCache = GET_CACHE.get().apply(state);
        if (newCache != null) {
            final BlockStateCacheAccess oldCache = LAST_CACHE.get();
            deduplicateCollisionShape(newCache, oldCache);
            deduplicateFaceSturdyArray(newCache, oldCache);
            LAST_CACHE.remove();
        }
    }

    private static void deduplicateCollisionShape(
            BlockStateCacheAccess newCache, @Nullable BlockStateCacheAccess oldCache
    ) {
        VoxelShape dedupedCollisionShape;
        if (oldCache != null && VoxelShapeHash.INSTANCE.equals(
                oldCache.getCollisionShape(), newCache.getCollisionShape()
        )) {
            dedupedCollisionShape = oldCache.getCollisionShape();
        } else {
            dedupedCollisionShape = newCache.getCollisionShape();
            if (dedupedCollisionShape instanceof ArrayVSAccess access) {
                dedupedCollisionShape = (VoxelShape) CACHE_COLLIDE.computeIfAbsent(access, Function.identity());
            }
        }
        // A different instance coming back means this state's shape is now shared with one that
        // already existed, which is the entire point of the module and the one number that says so.
        // Counters always count, so this works without turning measurement on.
        if (dedupedCollisionShape != newCache.getCollisionShape()) {
            SHAPES_SHARED.increment();
        }
        replaceInternals(dedupedCollisionShape, newCache.getCollisionShape());
        newCache.setCollisionShape(dedupedCollisionShape);
    }

    private static void deduplicateFaceSturdyArray(
            BlockStateCacheAccess newCache, @Nullable BlockStateCacheAccess oldCache
    ) {
        boolean[] dedupedFaceSturdy;
        if(oldCache != null && Arrays.equals(oldCache.getFaceSturdy(), newCache.getFaceSturdy())) {
            dedupedFaceSturdy = oldCache.getFaceSturdy();
        } else {
            dedupedFaceSturdy = CACHE_FACE_STURDY.computeIfAbsent(newCache.getFaceSturdy(), Function.identity());
        }
        if (dedupedFaceSturdy != newCache.getFaceSturdy()) {
            FACE_STURDY_SHARED.increment();
        }
        newCache.setFaceSturdy(dedupedFaceSturdy);
    }

    private static void replaceInternals(VoxelShape toKeep, VoxelShape toReplace) {
        if (toKeep instanceof ArrayVoxelShape keepArray && toReplace instanceof ArrayVoxelShape replaceArray) {
            replaceInternals(keepArray, replaceArray);
        }
    }

    public static void replaceInternals(ArrayVoxelShape toKeep, ArrayVoxelShape toReplace) {
        if (toKeep == toReplace) {
            return;
        }
        // Mods have a tendency to keep their shapes in a custom cache, in addition to the blockstate cache. So removing
        // duplicate shapes from the cache only fixes part of the problem. The proper fix would be to deduplicate the
        // mod caches as well (or convince people to get rid of the larger ones), but that's not feasible. So: Accept
        // that we can't do anything about shallow size and replace the internals with those used in the cache. This is
        // not theoretically 100% safe since VSs can technically be modified after they are created, but handing out VSs
        // that will be modified is unsafe in any case since a lot of vanilla code relies on VSs being immutable.
        ArrayVSAccess toReplaceAccess = (ArrayVSAccess) toReplace;
        ArrayVSAccess toKeepAccess = (ArrayVSAccess) toKeep;
        toReplaceAccess.setXPoints(toKeepAccess.getXPoints());
        toReplaceAccess.setYPoints(toKeepAccess.getYPoints());
        toReplaceAccess.setZPoints(toKeepAccess.getZPoints());
        toReplaceAccess.setFaces(toKeepAccess.getFaces());
        toReplaceAccess.setShape(toKeepAccess.getShape());
    }

    @Nullable
    private static VoxelShape getRenderShape(@Nullable VoxelShape[] projected) {
        if (projected != null) {
            for (VoxelShape side : projected) {
                if (side instanceof SliceShapeAccess slice) {
                    return slice.getDelegate();
                }
            }
        }
        return null;
    }
}
