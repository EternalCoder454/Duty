package net.dutymod.memory.mixin.datacomponents;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(PatchedDataComponentMap.class)
public class PatchedDataComponentMapMixin {
    @Shadow
    private Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch;

    @Shadow
    private boolean copyOnWrite;

    // Every target here is pinned by full descriptor. applyPatch, set and remove are all
    // overloaded in 26.1.2 (applyPatch also takes (DataComponentType, Optional); set also takes
    // TypedDataComponent), and an unqualified name silently resolves to whichever overload mixin
    // finds first. When that guess is wrong the injector fails at target-class load, which takes
    // down whatever mod happened to trigger the load rather than reporting itself.
    @Inject(
            method = {
                    "applyPatch(Lnet/minecraft/core/component/DataComponentPatch;)V",
                    "restorePatch(Lnet/minecraft/core/component/DataComponentPatch;)V"
            },
            at = @At("RETURN")
    )
    private void saveMemoryIfEmpty(CallbackInfo ci) {
        if (patch.isEmpty()) {
            // Use a singleton empty map to reduce memory overhead from empty maps, use copyOnWrite to ensure we never
            // try to modify this map
            this.patch = Reference2ObjectMaps.emptyMap();
            this.copyOnWrite = true;
        }
    }

    // Mixin seems to require an injection into a non-void method to take CallbackInfoReturnable rather than
    // CallbackInfo, so we need a separate method to inject into set and remove.
    @Inject(
            method = {
                    "set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;",
                    "set(Lnet/minecraft/core/component/TypedDataComponent;)Ljava/lang/Object;",
                    "remove(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"
            },
            at = @At("RETURN")
    )
    private void saveMemoryIfEmptyWithReturn(CallbackInfoReturnable<?> ci) {
        saveMemoryIfEmpty(ci);
    }
}
