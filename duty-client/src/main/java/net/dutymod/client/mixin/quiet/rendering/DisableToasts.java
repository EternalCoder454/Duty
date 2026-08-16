package net.dutymod.client.mixin.quiet.rendering;

import net.dutymod.client.quiet.config.Config;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses advancement and recipe unlock toasts.
 *
 * <p>Backs {@code client.advancement_toasts} and {@code client.recipe_toasts}. Both were registered
 * and read into {@link Config} during the Quiet port, but this mixin was never brought across, so
 * both options were inert.
 *
 * <p>Upstream also redirects a read of {@code ClientPacketListener.seenInsecureChatWarning} inside
 * {@code handleLogin}, to suppress the insecure-chat warning. That field does not exist in 26.1.2 --
 * verified with {@code javap -p net.minecraft.client.multiplayer.ClientPacketListener} -- and with
 * {@code defaultRequire = 1} an unresolvable redirect fails the whole config, so it is omitted. It
 * was also ungated by any option, which is not a change worth carrying silently.
 */
@Mixin(ClientPacketListener.class)
public abstract class DisableToasts {
    @Redirect(
            method = "handleRecipeBookAdd",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundRecipeBookAddPacket$Entry;notification()Z"
            )
    )
    private boolean duty$disableRecipeToasts(ClientboundRecipeBookAddPacket.Entry entry) {
        return Config.get().recipeToasts && entry.notification();
    }

    /**
     * Cancelling the packet drops the advancement update entirely, not just its toast, so the
     * advancement screen stops reflecting progress while this is off. That is upstream's behaviour
     * and the option defaults to on; the config comment says so rather than leaving it to be
     * discovered.
     */
    @Inject(method = "handleUpdateAdvancementsPacket", at = @At("HEAD"), cancellable = true)
    private void duty$disableAdvancementToasts(ClientboundUpdateAdvancementsPacket packet, CallbackInfo ci) {
        if (!Config.get().advancementToasts) {
            ci.cancel();
        }
    }
}
