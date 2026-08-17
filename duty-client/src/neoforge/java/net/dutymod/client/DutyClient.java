package net.dutymod.client;

import net.dutymod.client.culling.EntityCulling;
import net.dutymod.client.bakedentities.compat.ModCompat;
import net.dutymod.client.bakedentities.registry.Registry;
import net.dutymod.client.batching.Batching;
import net.dutymod.client.quiet.Quiet;
import net.dutymod.client.quiet.QuietNeoForge;
import net.dutymod.framework.DutyLog;
import net.dutymod.framework.screen.DutyConfigScreens;
import net.neoforged.fml.ModContainer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Duty: Client.
 *
 * <p>Three bodies of work, all client-only:
 *
 * <ul>
 *   <li>occlusion culling of entities and block entities (from EntityCulling)
 *   <li>particle rendering (from Particle Core)
 *   <li>baking block entities into the chunk mesh (from OptimisedBlockEntities)
 * </ul>
 *
 * <p>The last two of those complement rather than duplicate the first: block entities that get
 * baked into the chunk mesh never reach the renderer the culler filters, and whatever stays
 * dynamic is still culled as before.
 *
 * <p>The culling thread starts lazily on the first tick rather than here, because it needs the
 * registries to resolve its whitelists and those are not populated at construction time.
 */
@Mod(value = DutyClient.MOD_ID, dist = Dist.CLIENT)
public class DutyClient {
    public static final String MOD_ID = "duty_client";

    public DutyClient(IEventBus modBus, ModContainer container) {
        ClientOptions.init();
        DutyConfigScreens.register(container);

        // Block entity groups have to be registered before anything asks which ones are baked.
        Registry.init();

        QuietNeoForge.register(modBus);

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            EntityCulling.get().clientTick();
            Quiet.clientTick(net.minecraft.client.Minecraft.getInstance());
        });
        // Mod compatibility is resolved once the full mod list is known.
        modBus.addListener(FMLLoadCompleteEvent.class, event -> ModCompat.init());

        // Batching's sign text cache has to be dropped on resource reload, or it keeps
        // handing out glyphs from a resource pack that is no longer loaded. Upstream does this
        // from its own @Mod class; Duty is one mod, so it happens here.
        // Mod bus, not the game bus: AddClientReloadListenersEvent is an IModBusEvent, and
        // registering it on NeoForge.EVENT_BUS throws at mod construction.
        modBus.addListener(net.neoforged.neoforge.client.event.AddClientReloadListenersEvent.class,
                event -> {
                    if (Batching.config != null && Batching.config.experimental_sign_text_buffering) {
                        event.addListener(
                                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "sign_text_cache"),
                                (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                                        manager -> Batching.signTextCache.onResourceManagerReload(manager));
                    }
                    // Glyph metrics are re-derived on reload, so cached string widths stop being
                    // true. This event covers all three ways that happens: a resource pack change,
                    // a language change, and toggling forced unicode.
                    event.addListener(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "text_width_cache"),
                            (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                                    manager -> net.dutymod.client.text.TextWidths.cache().clear());
                });

        DutyLog.info("Duty: Client reporting for duty.");
    }
}
