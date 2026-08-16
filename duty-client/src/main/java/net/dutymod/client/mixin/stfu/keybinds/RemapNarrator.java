package net.dutymod.client.mixin.stfu.keybinds;

import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import net.dutymod.client.stfu.Stfu;

@Mixin(KeyboardHandler.class)
public abstract class RemapNarrator {
    @ModifyConstant(method = "keyPress", constant = @Constant(intValue = GLFW.GLFW_KEY_B))
    private int shutNarrator(int key) {
        return Stfu.NARRATOR_KEY.key.getValue() == -1? -2 : Stfu.NARRATOR_KEY.key.getValue();
    }
}