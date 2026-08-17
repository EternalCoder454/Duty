package net.dutymod.client.hudcache.versioned;

//? >=1.21.10 {
import net.dutymod.client.mixin.hudcache.Gui_Graphics_Accessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.*;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

import java.util.LinkedList;
import java.util.Queue;

public class StatefulHudHandler {
	public static final Queue<ScreenArea> deferredSubmissions = new LinkedList<>();
	public static final GuiRenderState alternativeGuiRenderState = new GuiRenderState();
	public static final GuiGraphicsExtractor alternativeGuiGraphicsExtractor = new GuiGraphicsExtractor(Minecraft.getInstance(), alternativeGuiRenderState, 0, 0);

	public static void submitLater(ScreenArea state) {
		deferredSubmissions.add(state);
	}

	public static void dropDeferredSubmission() {
		deferredSubmissions.clear();
	}

	public static void performDeferredSubmission(GuiGraphicsExtractor guiGraphics) {
		var state = ((Gui_Graphics_Accessor)guiGraphics).getGuiRenderState();
		for (var submission : deferredSubmissions) {
			//TODO: optimize ?
			switch (submission) {
				case GuiElementRenderState gui -> state.addGuiElement(gui);
				case GuiTextRenderState text -> state.addText(text);
				case PictureInPictureRenderState pip -> state.addPicturesInPictureState(pip);
				case GuiItemRenderState item -> state.addItem(item);
				case null ->
						throw new NullPointerException("Submission");
				default ->
						throw new IllegalStateException("Unknown submission type " + submission.getClass().getName());
			}
		}
		deferredSubmissions.clear();
	}
}
//?}
