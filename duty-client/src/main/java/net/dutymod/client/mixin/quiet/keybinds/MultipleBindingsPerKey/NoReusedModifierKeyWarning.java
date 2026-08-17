package net.dutymod.client.mixin.quiet.keybinds.MultipleBindingsPerKey;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyBindsList.KeyEntry.class)
public class NoReusedModifierKeyWarning {
    @WrapOperation(method = "refreshEntry", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isUnbound()Z"))
    private boolean shutReusedModifierKeys(KeyMapping instance, Operation<Boolean> original) {
        return original.call(instance) || instance.getCategory().equals(KeyMapping.Category.CREATIVE);
    }

    @WrapOperation(method = "refreshEntry", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;same(Lnet/minecraft/client/KeyMapping;)Z"))
    private boolean shutReusedModifierKeys(KeyMapping instance, KeyMapping other, Operation<Boolean> original) {
        return original.call(instance, other) && !other.getCategory().equals(KeyMapping.Category.CREATIVE)
                && !(instance.getCategory().equals(other.getCategory()) && instance.isDefault() && other.isDefault());
    }
}
