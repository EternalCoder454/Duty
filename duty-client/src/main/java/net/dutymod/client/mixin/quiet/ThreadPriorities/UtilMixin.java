package net.dutymod.client.mixin.quiet.ThreadPriorities;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.dutymod.client.quiet.config.Config;

@Mixin(Util.class)
public abstract class UtilMixin {
    @WrapOperation(method = "lambda$makeIoExecutor$0", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;setName(Ljava/lang/String;)V"))
    private static void setName(Thread instance, String name, Operation<Void> original) {
        original.call(instance, name);
        instance.setPriority(Config.get().ioThreadPriority);
    }
}