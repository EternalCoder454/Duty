package net.dutymod.client.mixin.hudcache;

//$ import_delta_tracker
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

//? <=1.20.4
//import net.minecraft.world.entity.Entity;

@Mixin(Gui.class)
public interface GuiAccessor {
    //? fabric && <=1.21.10 {
    /*//? <=1.20.4 {
    /^@Accessor
    float getScopeScale();
    @Accessor
    void setScopeScale(float f);
    @Accessor("PUMPKIN_BLUR_LOCATION")
    Identifier getPumpkinBlurLocation();
    @Accessor("POWDER_SNOW_OUTLINE_LOCATION")
    Identifier getPowderSnowLocation();
    @Accessor
    int getTitleTime();
    @Accessor
    int getOverlayMessageTime();
    @Accessor
    boolean getAnimateOverlayMessageColor();
    @Accessor
    Component getOverlayMessageString();
    @Accessor
    Component getTitle();
    @Accessor
    Component getSubtitle();
    @Accessor
    int getTickCount();
    @Accessor
    int getTitleFadeInTime();
    @Accessor
    int getTitleStayTime();
    @Accessor
    int getTitleFadeOutTime();
    @Accessor
    int getScreenWidth();
    @Accessor
    int getScreenHeight();
    @Invoker
    void invokeDisplayScoreboardSidebar(GuiGraphicsExtractor guiGraphics, Objective objective);
    @Invoker
    void invokeRenderVignette(GuiGraphicsExtractor guiGraphics, Entity entity);
    @Invoker
    void invokeRenderSpyglassOverlay(GuiGraphicsExtractor guiGraphics, float f);
    @Invoker
    void invokeRenderTextureOverlay(GuiGraphicsExtractor guiGraphics, Identifier resourceLocation, float f);
    @Invoker
    void invokeRenderPortalOverlay(GuiGraphicsExtractor guiGraphics, float f);
    @Invoker
    void invokeRenderHotbar(float f, GuiGraphicsExtractor guiGraphics);
    @Invoker
    void invokeRenderCrosshair(GuiGraphicsExtractor guiGraphics);
    @Invoker
    void invokeRenderPlayerHealth(GuiGraphicsExtractor guiGraphics);
    @Invoker
    void invokeRenderVehicleHealth(GuiGraphicsExtractor guiGraphics);
    @Invoker
    void invokeRenderEffects(GuiGraphicsExtractor guiGraphics);
    @Invoker
    void invokeDrawBackdrop(GuiGraphicsExtractor guiGraphics, Font font, int i, int j, int k);
    @Invoker
    void invokeRenderSavingIndicator(GuiGraphicsExtractor guiGraphics);
    ^///? } else {
    @Invoker
    void invokeRenderCameraOverlays(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker);
    @Invoker
    void invokeRenderCrosshair(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker);
    @Invoker
    void invokeRenderHotbarAndDecorations(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker);
    @Invoker
    void invokeRenderExperienceLevel(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker);
    @Invoker
    void invokeRenderEffects(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker);
    @Invoker
    void invokeRenderSleepOverlay(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker);
    @Invoker
    void invokeRenderDemoOverlay(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker);
    @Invoker
    void invokeRenderScoreboardSidebar(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker);
    @Invoker
    void invokeRenderOverlayMessage(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker);
    @Invoker
    void invokeRenderTitle(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker);
    @Invoker
    void invokeRenderChat(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker);
    @Invoker
    void invokeRenderTabList(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker);
    @Invoker
    void invokeRenderSavingIndicator(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker);
    //? }
    *///? }
    //? <=1.21.4 {
    /*@Accessor("subtitleOverlay")
    SubtitleOverlay gnetum$getSubtitleOverlay();
    *///? }
}
