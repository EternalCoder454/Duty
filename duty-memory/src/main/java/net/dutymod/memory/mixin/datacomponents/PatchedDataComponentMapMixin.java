package net.dutymod.memory.mixin.datacomponents;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    /**
     * The prototype's hash, worked out once.
     *
     * <p>Vanilla's {@code hashCode} is {@code prototype.hashCode() + patch.hashCode() * 31}, so
     * every call walks the whole prototype map. That map is the item's default component set: it
     * is {@code final}, it is shared by every stack of that item, and it never changes. Hashing it
     * again on each call is pure repetition, and component maps get hashed a lot -- anything that
     * puts an {@link net.minecraft.world.item.ItemStack} in a hash-based collection ends up here.
     *
     * <p>Only the prototype half is cached. The patch is mutable, so its hash stays live.
     */
    @Unique
    private int duty$prototypeHash;

    // Pinned by descriptor like the injections above. The public single-argument constructor
    // delegates to this one, so caching here covers every instance that can exist.
    @Inject(
            method = "<init>(Lnet/minecraft/core/component/DataComponentMap;"
                    + "Lit/unimi/dsi/fastutil/objects/Reference2ObjectMap;Z)V",
            at = @At("RETURN")
    )
    private void duty$cachePrototypeHash(
            DataComponentMap prototype,
            Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch,
            boolean copyOnWrite,
            CallbackInfo ci) {
        this.duty$prototypeHash = prototype.hashCode();
    }

    /**
     * Redirects only the prototype's half of vanilla's hash, leaving the arithmetic and the patch's
     * half exactly as they were. The value produced is identical to vanilla's -- this changes how
     * long it takes to arrive at it, not what it is.
     */
    @Redirect(
            method = "hashCode()I",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/component/DataComponentMap;hashCode()I"
            )
    )
    private int duty$useCachedPrototypeHash(DataComponentMap prototype) {
        return this.duty$prototypeHash;
    }
}
