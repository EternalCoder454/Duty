package net.dutymod.client.mixin.hudcache;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.dutymod.client.hudcache.Constants;
import net.dutymod.client.hudcache.Gnetum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? >=1.21.10 {
// We require additional buffer flushing before switching framebuffers. To avoid client side stall when awaiting
// fence completion on glClientWaitSync, we increase the buffer count here.
@Mixin(value = net.minecraft.client.renderer.MappableRingBuffer.class, priority = 2000)
public class MappableRingBufferMixin {
    @ModifyExpressionValue(method = { "<init>", "close" } , at = @At(value = "CONSTANT", args = "intValue=3"), require = 0, expect = 0)
    private int gnetum$increaseBufferSize(int original) {
        // We must generate a size that is the largest we might need because we cannot resize afterward
        return original + 6;
    }

    @ModifyExpressionValue(method = "rotate" , at = @At(value = "CONSTANT", args = "intValue=3"), require = 0, expect = 0)
    private int gnetum$rotate$increaseBufferSize(int original) {
        if (!Gnetum.config.isEnabled()) {
            return original;
        }
        var element = Gnetum.getElement(Constants.DEBUG_OVERLAY);
        if (element != null && !element.isUncached()) {
            // F3 caching requires two additional flushes
            return original + 6;
        }
        return original + 3;
    }
}
//? } else {
/*@Mixin(targets = {})
public class MappableRingBufferMixin {}
*///? }
