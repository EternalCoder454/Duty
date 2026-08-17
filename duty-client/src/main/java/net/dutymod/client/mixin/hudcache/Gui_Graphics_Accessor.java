package net.dutymod.client.mixin.hudcache;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? >=1.21.10 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.gen.Invoker;

//?}
@Mixin(GuiGraphicsExtractor.class)
public interface Gui_Graphics_Accessor // Named with _ because we use stonecutter to rename GuiGraphicsExtractor to GuiGraphicsExtractor for 26.1+; this line must not be renamed as the class name needs to be identical as the file name
{
	//? >=1.21.10 {
	@Accessor
	GuiRenderState getGuiRenderState();
	@Invoker
	//? <26 {
			/*(value = "submitBlit")
	*///? }
	void invokeInnerBlit(RenderPipeline pipeline,
	                     GpuTextureView textureView,
	                     GpuSampler sampler,
	                     int x0,
	                     int y0,
	                     int x1,
	                     int y1,
	                     float u0,
	                     float u1,
	                     float v0,
	                     float v1,
	                     int color);
	//?}
}
