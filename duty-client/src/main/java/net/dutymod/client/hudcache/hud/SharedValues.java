package net.dutymod.client.hudcache.hud;

//? fabric && <=1.21.10 {
/*import net.dutymod.client.mixin.hudcache.GuiAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
//$ import_delta_tracker
import net.minecraft.client.DeltaTracker;

public class SharedValues {
    public static GuiGraphicsExtractor guiGraphics;
    public static DeltaTracker deltaTracker;

    public static Gui gui() {
        return Minecraft.getInstance().gui;
    }

    public static GuiAccessor guiAccessor() {
        return (GuiAccessor) gui();
    }

    public static GuiGraphicsExtractor guiGraphics() {
        return guiGraphics;
    }

    public static DeltaTracker deltaTracker() {
        return deltaTracker;
    }
}
*///? }