package net.dutymod.fixerupper.neoforge.init;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.apache.commons.lang3.tuple.Pair;
import net.dutymod.fixerupper.FixerUpper;
import net.dutymod.fixerupper.core.FixerUpperMixinPlugin;
import net.dutymod.fixerupper.duck.suspend_integrated_server_during_load.IDeferrableIntegratedServer;
import net.dutymod.fixerupper.neoforge.packet.ClientLoadFinishedPayload;

import java.util.List;

@Mod(FixerUpper.MODID)
public class FixerUpperForge {
    private static FixerUpper commonMod;
    public static boolean launchDone = false;
    public static boolean registryEventsFired = false;

    public FixerUpperForge(ModContainer modContainer, IEventBus modBus) {
        commonMod = new FixerUpper();
        // Register ourselves for server and other game events we are interested in
        NeoForge.EVENT_BUS.register(this);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::registerItems);
        modBus.addListener(this::registerNetworkChannel);
        if(FMLEnvironment.getDist() == Dist.CLIENT) {
            NeoForge.EVENT_BUS.register(new FixerUpperClientForge(modContainer, modBus));
        }
    }

    private void registerItems(RegisterEvent event) {
        if(Boolean.getBoolean("duty.largeRegistryTest")) {
            event.register(Registries.ITEM, helper -> {
                Item.Properties props = new Item.Properties();
                for(int i = 0; i < 1000000; i++) {
                    helper.register(Identifier.fromNamespaceAndPath("duty_fixerupper", "item_" + i), new Item(props));
                }
            });
        }
    }

    // FixerUpper warned here when FerriteCore was missing. Duty: Memory contains FerriteCore
    // and declares itself incompatible with it, so recommending it would tell the user to
    // install a mod that refuses to load alongside Duty. No warnings left, hence the empty list.
    private static final List<Pair<List<String>, String>> MOD_WARNINGS = ImmutableList.of();

    public void commonSetup(FMLCommonSetupEvent event) {
        // Resolve the configured recipe types now that the registry is populated. Without this the
        // whitelist stays empty and the recipe index quietly never applies -- the mixin would still
        // load, still report as applied, and fall through to vanilla for every lookup.
        if(FixerUpperMixinPlugin.instance.isOptionEnabled("perf.fast_recipes.RecipeMapMixin")) {
            event.enqueueWork(net.dutymod.fixerupper.fastrecipes.FastRecipes::resolveIndexedTypes);
        }

        if(FixerUpperMixinPlugin.instance.isOptionEnabled("feature.warn_missing_perf_mods.Warnings")) {
            event.enqueueWork(() -> {
                boolean atLeastOneWarning = false;
                for(Pair<List<String>, String> warning : MOD_WARNINGS) {
                    boolean isPresent = !FMLLoader.getCurrent().isProduction() || warning.getLeft().stream().anyMatch(name -> ModList.get().isLoaded(name));
                    if(!isPresent) {
                        atLeastOneWarning = true;
                        ModLoader.addLoadingIssue(ModLoadingIssue.warning(warning.getRight()));
                    }
                }
                if(atLeastOneWarning)
                    ModLoader.addLoadingIssue(ModLoadingIssue.warning("duty.perf_mod_warning"));
            });
        }
        event.enqueueWork(FixerUpper::runAuditIfRequested);
    }

    private void registerNetworkChannel(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1").executesOn(HandlerThread.MAIN).optional();

        registrar.playToClient(
                ClientLoadFinishedPayload.TYPE,
                ClientLoadFinishedPayload.STREAM_CODEC,
                (payload, ctx) -> {
                    ctx.enqueueWork(() -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.hasSingleplayerServer()) {
                            ((IDeferrableIntegratedServer)mc.getSingleplayerServer()).duty$markClientLoadFinished();
                        }
                    });
                });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerDead(ServerStoppedEvent event) {
        commonMod.onServerDead(event.getServer());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerStarted(ServerStartedEvent event) {
        commonMod.onServerStarted();
    }
}
