package net.dutymod.fixerupper.common.mixin.perf.compact_identifier_encoding;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
@Mixin(ResourceLocation.class)
public class IdentifierMixin {

    @Shadow
    @Mutable
    @Final
    public static StreamCodec<ByteBuf, ResourceLocation> STREAM_CODEC;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void inject(CallbackInfo ci) {
        STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(ResourceLocation::parse, rl -> (rl.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) ? rl.getPath() : rl.toString());
    }
}