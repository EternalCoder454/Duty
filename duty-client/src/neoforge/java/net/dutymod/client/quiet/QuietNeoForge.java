package net.dutymod.client.quiet;

import net.dutymod.client.culling.DebugEntryCulling;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * Quiet's registration against NeoForge's mod event bus.
 *
 * <p>Everything Quiet actually does -- the key mappings, the music state, the per-tick check -- is
 * loader-neutral and lives in {@link Quiet}. Only handing the key bindings and the debug entry to
 * the loader needs to know which loader this is, and that is the whole of this class. The Fabric
 * equivalent registers the same three mappings through KeyBindingHelper.
 */
public final class QuietNeoForge {
    private QuietNeoForge() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(Quiet.NARRATOR_KEY);
            event.register(Quiet.SKIP_MUSIC_KEY);
            event.register(Quiet.TOGGLE_MUSIC_KEY);
        });
        modBus.addListener(RegisterDebugEntriesEvent.class, event ->
                event.register(Identifier.fromNamespaceAndPath("duty_client", "fps_history"),
                        new DebugEntryFpsHistory()));

        modBus.addListener(RegisterDebugEntriesEvent.class, event ->
                event.register(Identifier.fromNamespaceAndPath("duty_client", "culling"),
                        new DebugEntryCulling()));
    }
}
