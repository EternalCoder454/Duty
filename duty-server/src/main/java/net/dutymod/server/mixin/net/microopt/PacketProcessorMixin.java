package net.dutymod.server.mixin.net.microopt;

import net.minecraft.network.PacketProcessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Queue;

/**
 * Optimizes {@link PacketProcessor#processQueuedPackets()}.
 * <p>
 * Vanilla drains the pending packet queue with {@code while (!queue.isEmpty()) queue.poll().handle();},
 * performing two traversals of the ConcurrentLinkedQueue head per packet. Rewriting the loop to the
 * canonical {@code while ((e = queue.poll()) != null)} pattern halves the queue operations on the
 * main-thread packet processing hot path.
 * <p>
 * Implemented as a pair of redirects with a carrier field instead of a full method replacement, so it
 * stays independent of the queue's element type (which differs between vanilla and loader-patched
 * runtimes).
 *
 * <p>Upstream re-read its config inside the redirect so the optimisation could be toggled without a
 * restart. This drains on the main thread once per queued packet, so Duty gates the whole mixin in
 * {@code NetMixinPlugin} instead: when the option is off the mixin never applies and the loop is
 * bit-for-bit vanilla, which is both cheaper and a more honest "off".
 */
@Mixin(PacketProcessor.class)
public class PacketProcessorMixin {
    /**
     * Carries the element polled in {@link #duty$pollInsteadOfIsEmpty} over to {@link #duty$reusePolled}.
     * PacketProcessor only ever drains the queue from its owning thread, so no synchronization is needed.
     */
    @Unique
    @Nullable
    private Object duty$polled;

    @Redirect(method = "processQueuedPackets", at = @At(value = "INVOKE", target = "Ljava/util/Queue;isEmpty()Z"))
    private boolean duty$pollInsteadOfIsEmpty(Queue<Object> queue) {
        Object polled = queue.poll();
        this.duty$polled = polled;
        return polled == null;
    }

    @Redirect(method = "processQueuedPackets", at = @At(value = "INVOKE", target = "Ljava/util/Queue;poll()Ljava/lang/Object;"))
    private Object duty$reusePolled(Queue<Object> queue) {
        Object polled = this.duty$polled;
        if (polled != null) {
            this.duty$polled = null;
            return polled;
        }
        return queue.poll();
    }
}
