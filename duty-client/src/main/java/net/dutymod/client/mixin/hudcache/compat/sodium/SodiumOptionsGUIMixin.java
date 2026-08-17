package net.dutymod.client.mixin.hudcache.compat.sodium;

import org.spongepowered.asm.mixin.Mixin;

//? if sodium_legacy {
/*import net.dutymod.client.hudcache.Gnetum;
import net.dutymod.client.hudcache.compat.sodium.LegacySodiumPage;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI;
import net.caffeinemc.mods.sodium.client.gui.options.OptionPage;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(value = SodiumOptionsGUI.class, remap = false)
public class SodiumOptionsGUIMixin {
    @Shadow
    @Final
    private List<OptionPage> pages;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void gnetum$init(Screen prevScreen, CallbackInfo ci) {
		Gnetum.platform().elementGatherer().gather();

        this.pages.add(new LegacySodiumPage());
    }
}
*///?} else {
@Mixin(targets = {})
public class SodiumOptionsGUIMixin {}
//? }
