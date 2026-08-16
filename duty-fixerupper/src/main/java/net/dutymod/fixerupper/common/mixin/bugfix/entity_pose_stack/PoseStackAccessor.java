package net.dutymod.fixerupper.common.mixin.bugfix.entity_pose_stack;

import com.mojang.blaze3d.vertex.PoseStack;
import net.dutymod.fixerupper.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PoseStack.class)
@ClientOnlyMixin
public interface PoseStackAccessor {
    @Accessor("lastIndex")
    int mfix$getLastIndex();
}
