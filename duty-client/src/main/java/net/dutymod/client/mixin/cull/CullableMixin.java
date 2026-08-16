package net.dutymod.client.mixin.cull;

import net.dutymod.client.cull.Cullable;
import net.dutymod.client.cull.EntityCulling;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Attaches culling state to every entity and block entity.
 *
 * <p>Three fields on every object in the world is a real memory cost, which is why they are packed
 * as tightly as they are. Storing the state in a side map keyed by object would be worse on both
 * counts: an extra lookup on the render thread's hot path, and a map that has to be kept in step
 * with entity lifetimes.
 */
@Mixin({ Entity.class, BlockEntity.class })
public class CullableMixin implements Cullable {
    @Unique
    private long duty$visibleUntil = 0;
    @Unique
    private boolean duty$culled = false;
    @Unique
    private boolean duty$outOfCamera = false;

    @Override
    public void duty$setTimeout() {
        duty$visibleUntil = System.currentTimeMillis() + 1000;
    }

    @Override
    public boolean duty$isForcedVisible() {
        return duty$visibleUntil > System.currentTimeMillis();
    }

    @Override
    public void duty$setCulled(boolean value) {
        this.duty$culled = value;
        if (!value) {
            // Becoming visible starts the grace period, so the render thread cannot hide this
            // again before the culling thread has confirmed the new state.
            duty$setTimeout();
        }
    }

    @Override
    public boolean duty$isCulled() {
        return EntityCulling.get().isEnabled() && duty$culled;
    }

    @Override
    public void duty$setOutOfCamera(boolean value) {
        this.duty$outOfCamera = value;
    }

    @Override
    public boolean duty$isOutOfCamera() {
        return EntityCulling.get().isEnabled() && duty$outOfCamera;
    }
}
