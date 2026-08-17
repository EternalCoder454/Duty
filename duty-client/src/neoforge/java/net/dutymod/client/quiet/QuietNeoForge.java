package net.dutymod.client.quiet;

import net.dutymod.client.culling.DebugEntryCulling;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * Duty: Client's registration against NeoForge's mod event bus.
 *
 * <p>Everything these entries and keybinds actually do is loader-neutral and lives elsewhere; only
 * handing them to the loader needs to know which loader this is.
 *
 * <h2>Registering a debug entry takes two calls, not one</h2>
 *
 * <p>{@code register} puts the entry in the registry. It does <em>not</em> make it appear:
 * {@code DebugScreenEntries} keeps a per-profile map of which entries are shown, and an entry in no
 * profile is never displayed. Both of Duty's entries were registered and never included, so neither
 * ever rendered and the omission looked exactly like the feature not working.
 *
 * <p>{@code IN_OVERLAY} rather than {@code ALWAYS_ON}: these belong on F3 with the rest of the
 * profiling lines, not on screen permanently.
 */
public final class QuietNeoForge {
    private static final Identifier FPS_HISTORY =
            Identifier.fromNamespaceAndPath("duty_client", "fps_history");

    private static final Identifier CULLING =
            Identifier.fromNamespaceAndPath("duty_client", "culling");

    private QuietNeoForge() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(Quiet.NARRATOR_KEY);
            event.register(Quiet.SKIP_MUSIC_KEY);
            event.register(Quiet.TOGGLE_MUSIC_KEY);
        });

        modBus.addListener(RegisterDebugEntriesEvent.class, event -> {
            event.register(FPS_HISTORY, new DebugEntryFpsHistory());
            event.register(CULLING, new DebugEntryCulling());

            // Without this the entries exist and are never shown. Both profiles, because a player
            // who has switched F3 to the performance profile is exactly the one who wants these.
            for (DebugScreenProfile profile : DebugScreenProfile.values()) {
                event.includeInProfile(FPS_HISTORY, profile, DebugScreenEntryStatus.IN_OVERLAY);
                event.includeInProfile(CULLING, profile, DebugScreenEntryStatus.IN_OVERLAY);
            }
        });
    }
}
