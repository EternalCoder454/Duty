package ca.spottedleaf.starlight.mixin.common.lightengine;

import ca.spottedleaf.starlight.common.light.StarLightLightingProvider;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLightEngine.class)
public abstract class LevelLightEngineMixin {

    // @Final @Mutable, which upstream does not need and this build does.
    //
    // Both fields are private final in vanilla, and construct() below assigns them. The JVM only
    // lets a final field be written from its declaring class's own <init>; a mixin handler is a
    // separate method, so without this the game dies with IllegalAccessError partway through
    // creating a world -- at runtime, with a green build behind it.
    //
    // Upstream is a Fabric mod and never had to say this: loom's access widener drops final across
    // the whole game. @Mutable is Mixin's own way of asking for the same thing on one field, and is
    // what SerializableChunkDataMixin already does for lightCorrect.
    @Shadow
    @Final
    @Mutable
    @Nullable
    private LightEngine<?, ?> blockEngine;

    @Shadow
    @Final
    @Mutable
    @Nullable
    private LightEngine<?, ?> skyEngine;

    /**
     *
     * TODO since this is a constructor inject, check on update for new constructors
     */
    @Inject(
            method = "<init>*", at = @At("TAIL")
    )
    public void construct(final CallbackInfo ci) {
        if (this instanceof StarLightLightingProvider) {
            // intentionally destroy mods hooking into old light engine state
            this.blockEngine = null;
            this.skyEngine = null;
        }
    }

}
