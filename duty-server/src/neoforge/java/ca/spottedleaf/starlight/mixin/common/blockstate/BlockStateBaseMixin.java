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
     * Initialises our light state for this block.
     */
    @Inject(
            method = "initCache",
            at = @At("RETURN")
    )
    public void initLightAccessState(final CallbackInfo ci) {
        this.scalablelux$isConditionallyFullOpaque = this.canOcclude & this.useShapeForLightOcclusion;
        try {
            if ((this instanceof IBlockStateExtension extension && extension.hasDynamicLightEmission()) ||
                    this.getClass().getMethod("getLightEmission", BlockGetter.class, BlockPos.class).getDeclaringClass() != IBlockStateExtension.class ||
                    this.getBlock().getClass().getMethod("getLightEmission", BlockState.class, BlockGetter.class, BlockPos.class).getDeclaringClass() != IBlockExtension.class) {
                this.scalablelux$actuallyDynamicLightEmission = true;
            }
        } catch (Throwable t) {
            ScalableLuxEntrypoint.LOGGER.error("Failed to analyze class \"{}\" for dynamic lighting, this will impact performance.", this.getClass().toString(), t);
            this.scalablelux$actuallyDynamicLightEmission = true;
        }
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
