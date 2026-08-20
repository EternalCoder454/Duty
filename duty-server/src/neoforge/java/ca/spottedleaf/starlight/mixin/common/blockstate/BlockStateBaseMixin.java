package ca.spottedleaf.starlight.mixin.common.blockstate;

import ca.spottedleaf.starlight.common.ScalableLuxEntrypoint;
import ca.spottedleaf.starlight.common.blockstate.ExtendedAbstractBlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.common.extensions.IBlockExtension;
import net.neoforged.neoforge.common.extensions.IBlockStateExtension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin extends StateHolder<Block, BlockState> implements ExtendedAbstractBlockState {

    @Shadow
    @Final
    private boolean useShapeForLightOcclusion;

    @Shadow
    @Final
    private boolean canOcclude;

    @Shadow public abstract Block getBlock();

    @Unique
    private boolean scalablelux$isConditionallyFullOpaque;

    @Unique
    private boolean scalablelux$actuallyDynamicLightEmission;

    protected BlockStateBaseMixin(Block owner, Property<?>[] propertyKeys, Comparable<?>[] propertyValues) {
        super(owner, propertyKeys, propertyValues);
    }

    /**
     * Whether a state class or a block class overrides getLightEmission, cached per class.
     *
     * <p>initCache runs once per block state, and the two reflective lookups it used to do ran with
     * it. Class.getMethod is a full method table search that allocates a fresh Method on every
     * call, and a large pack has tens of thousands of block states against maybe a thousand block
     * classes, so almost all of that work was answering the same question again.
     *
     * <p>Safe to cache because the answer is a pure function of the two classes. Nothing else in
     * the expression depends on the class: hasDynamicLightEmission is per state and is still asked
     * per state.
     */
    @Unique
    private static final java.util.Map<Class<?>, Boolean> scalablelux$stateOverridesEmission =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Unique
    private static final java.util.Map<Class<?>, Boolean> scalablelux$blockOverridesEmission =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Initialises our light state for this block.
     */
    @Inject(
            method = "initCache",
            at = @At("RETURN")
    )
    public void initLightAccessState(final CallbackInfo ci) {
        this.scalablelux$isConditionallyFullOpaque = this.canOcclude & this.useShapeForLightOcclusion;
        try {
            this.scalablelux$actuallyDynamicLightEmission =
                    (this instanceof IBlockStateExtension extension && extension.hasDynamicLightEmission())
                            || scalablelux$stateOverridesEmission(this.getClass())
                            || scalablelux$blockOverridesEmission(this.getBlock().getClass());
        } catch (Throwable t) {
            ScalableLuxEntrypoint.LOGGER.error("Failed to analyze class \"{}\" for dynamic lighting, this will impact performance.", this.getClass().toString(), t);
            this.scalablelux$actuallyDynamicLightEmission = true;
        }
    }

    @Unique
    private static boolean scalablelux$stateOverridesEmission(final Class<?> stateClass) {
        return scalablelux$stateOverridesEmission.computeIfAbsent(stateClass, clazz -> {
            try {
                return clazz.getMethod("getLightEmission", BlockGetter.class, BlockPos.class)
                        .getDeclaringClass() != IBlockStateExtension.class;
            } catch (Throwable t) {
                // Same answer the original gave on failure: assume dynamic, which costs performance
                // rather than correctness.
                ScalableLuxEntrypoint.LOGGER.error("Failed to analyze class \"{}\" for dynamic lighting, this will impact performance.", clazz.toString(), t);
                return Boolean.TRUE;
            }
        });
    }

    @Unique
    private static boolean scalablelux$blockOverridesEmission(final Class<?> blockClass) {
        return scalablelux$blockOverridesEmission.computeIfAbsent(blockClass, clazz -> {
            try {
                return clazz.getMethod("getLightEmission", BlockState.class, BlockGetter.class, BlockPos.class)
                        .getDeclaringClass() != IBlockExtension.class;
            } catch (Throwable t) {
                ScalableLuxEntrypoint.LOGGER.error("Failed to analyze class \"{}\" for dynamic lighting, this will impact performance.", clazz.toString(), t);
                return Boolean.TRUE;
            }
        });
    }

    @Override
    public final boolean scalablelux$isConditionallyFullOpaque() {
        return this.scalablelux$isConditionallyFullOpaque;
    }

    @Unique
    @Override
    public boolean scalablelux$actuallyDynamicLightEmission() {
        return this.scalablelux$actuallyDynamicLightEmission;
    }
}
