package net.dutymod.client.mixin.culling;

import net.dutymod.client.culling.Cullable;
import net.dutymod.client.culling.CullingHelper;
import net.dutymod.client.culling.EntityCulling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.AngerLevel;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the client-side tick of entities that cannot be seen.
 *
 * <p>Not rendering a hidden mob saves the draw; not ticking it saves the animation, particle and
 * sound work behind the draw, which in a crowded farm is the larger half.
 *
 * <p>Skipping a tick outright would make entities teleport when they reappear, so {@link
 * #duty$basicTick} still advances the parts that must not drift: position history, tick count, and
 * living-entity movement. What is dropped is the expensive remainder.
 *
 * <p>The exemptions are all cases where a skipped tick is observable even though the entity is not:
 * the player and camera entity obviously, anything riding or being ridden (its rider may well be
 * visible), and minecarts, whose movement is driven entirely client-side and would desync.
 */
@Mixin(ClientLevel.class)
public class ClientLevelMixin {

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void duty$cullTick(Entity entity, CallbackInfo ci) {
        EntityCulling culling = EntityCulling.get();
        if (!culling.isEnabled() || !culling.isTickCulling() || culling.isSkipEntityCulling()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (entity == client.player || entity == client.getCameraEntity()
                || entity.isPassenger() || entity.isVehicle()
                || entity instanceof AbstractMinecart
                || CullingHelper.ignoresCulling(entity)) {
            return;
        }
        if (culling.getEntityWhitelist().contains(entity.getType())) {
            return;
        }

        if (entity instanceof Cullable cullable) {
            if (cullable.duty$isCulled() || cullable.duty$isOutOfCamera()) {
                duty$basicTick(entity, client);
                ci.cancel();
                return;
            }
            // Assume out of view until the renderer proves otherwise this frame. Entities that
            // never reach the extract phase are exactly the ones off screen.
            cullable.duty$setOutOfCamera(true);
        }
    }

    /** The minimum an entity needs so it does not visibly jump when it comes back into view. */
    private void duty$basicTick(Entity entity, Minecraft client) {
        entity.setOldPosAndRot();
        entity.tickCount++;
        if (entity instanceof LivingEntity living) {
            living.aiStep();
            if (living.hurtTime > 0) {
                living.hurtTime--;
            }
        }
        // The warden's heartbeat is generated client-side inside the tick we just skipped, and it
        // is meant to be heard through walls -- precisely when the warden is culled.
        if (entity instanceof Warden warden && client.level != null
                && !warden.isSilent() && warden.tickCount % duty$heartbeatDelay(warden) == 0) {
            client.level.playLocalSound(warden.getX(), warden.getY(), warden.getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, warden.getSoundSource(), 5.0F, warden.getVoicePitch(), false);
        }
    }

    /** Copy of the private vanilla calculation, to avoid widening access for one expression. */
    private int duty$heartbeatDelay(Warden warden) {
        float anger = warden.getClientAngerLevel() / (float) AngerLevel.ANGRY.getMinimumAnger();
        return 40 - Mth.floor(Mth.clamp(anger, 0.0F, 1.0F) * 30.0F);
    }
}
