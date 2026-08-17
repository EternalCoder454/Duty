package net.dutymod.fixerupper.neoforge.init;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.dutymod.fixerupper.FixerUpper;
import net.dutymod.fixerupper.FixerUpperClient;
import net.dutymod.fixerupper.screen.FixerUpperConfigScreen;
import org.jspecify.annotations.Nullable;

public class FixerUpperClientForge {
    private static FixerUpperClient commonMod;

    public FixerUpperClientForge(ModContainer modContainer, IEventBus modBus) {
        commonMod = new FixerUpperClient();
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onRenderOverlay);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (mc, screen) -> new FixerUpperConfigScreen(screen));
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        if(false) {
            event.enqueueWork(() -> {
                ModLoader.addLoadingIssue(ModLoadingIssue.warning("duty.connectedness_dynresoruces"));
            });
        }
    }

    private static final ResourceLocation MODERNFIX_VERSION = ResourceLocation.fromNamespaceAndPath(FixerUpper.MODID, "version");

    private void onRenderOverlay(RegisterDebugEntriesEvent event) {
        event.register(MODERNFIX_VERSION, new DebugScreenEntry() {
            @Override
            public void display(DebugScreenDisplayer displayer, @Nullable Level level, @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
                if (commonMod.brandingString != null) {
                    displayer.addToGroup(MODERNFIX_VERSION, commonMod.brandingString);
                }
            }
        });
        event.includeInProfile(MODERNFIX_VERSION, DebugScreenProfile.DEFAULT, DebugScreenEntryStatus.IN_OVERLAY);
    }

    @SubscribeEvent
    public void onDisconnect(LevelEvent.Unload event) {
        if(event.getLevel().isClientSide()) {
            DebugScreenOverlay overlay = Minecraft.getInstance().getDebugOverlay();
            Minecraft.getInstance().schedule(overlay::clearChunkCache);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartedEvent event) {
        commonMod.onServerStarted(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderTickEnd(RenderFrameEvent.Post event) {
        commonMod.onRenderTickEnd();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRecipes(RecipesReceivedEvent e) {
        commonMod.onRecipesUpdated();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTags(TagsUpdatedEvent e) {
        commonMod.onTagsUpdated();
    }
}
