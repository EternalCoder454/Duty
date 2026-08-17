package net.dutymod.client.mixin.hudcache;

import org.spongepowered.asm.mixin.Mixin;

//? >=1.21.10 {
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
//? }

//? >=1.21.10 {
@Mixin(GuiRenderer.class)
public interface GuiRendererAccessor {
    @Accessor @Mutable
    void setRenderState(GuiRenderState state);
    @Accessor
    GuiRenderState getRenderState();
}
//? } else {
/*@Mixin(targets = "")
public interface GuiRendererAccessor {}
*///? }
