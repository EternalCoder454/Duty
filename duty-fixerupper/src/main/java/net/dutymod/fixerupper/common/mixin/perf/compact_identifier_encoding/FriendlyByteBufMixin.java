package net.dutymod.fixerupper.common.mixin.perf.compact_identifier_encoding;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drops the redundant "minecraft:" namespace when an {@link net.minecraft.resources.ResourceLocation}
 * goes over the wire. Vanilla sends it in full every time, and the great majority of identifiers
 * in a packet stream are vanilla ones.
 *
 * <p><b>This changes the network protocol.</b> Both ends have to agree: a client with this on
 * talking to a server without it will mis-read every identifier it receives. That is fine when
 * the same pack runs on both sides, and broken when joining a server that does not have Duty.
 *
 * <p>Off by default for that reason. Turn it on only where you control both ends.
 */
@Mixin(FriendlyByteBuf.class)
public class FriendlyByteBufMixin {

    @Inject(method = "writeIdentifier", at = @At("HEAD"), cancellable = true)
    public void writeResourceLocation(ResourceLocation identifier, CallbackInfoReturnable<FriendlyByteBuf> cbr) {
        if (identifier.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
            this.writeUtf(identifier.getPath());
            cbr.setReturnValue((FriendlyByteBuf) (Object) this);
        }
    }

    @Shadow
    public FriendlyByteBuf writeUtf(String value) {
        return null;
    }
}