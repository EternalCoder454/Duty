package net.dutymod.client.mixin.stfu.RemoveOverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Overlay;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    // Resolved for 26.1: Stonecutter's "< 26.2" branch. Gui.overlay() is the 26.2 shape;
    // 26.1 still reads the field on Minecraft directly.
    @Redirect(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;overlay:Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 2, opcode = Opcodes.GETFIELD))
    private Overlay overlay(Minecraft instance){
        return null;
    }
}
