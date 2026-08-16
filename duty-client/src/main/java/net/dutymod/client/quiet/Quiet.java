package net.dutymod.client.quiet;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

/**
 * Quiet's keybinds and music control, on NeoForge.
 *
 * <p>Upstream this was a Fabric {@code ModInitializer} that registered keybinds through
 * {@code KeyMappingHelper} and hooked {@code ClientTickEvents}. NeoForge registers keybinds from
 * an event on the mod bus instead, and the debug entry through {@code RegisterDebugEntriesEvent} --
 * {@code DebugScreenEntries.register} is not accessible to mods here.
 *
 * <p>The keybinds are created as plain fields and handed over during registration, rather than
 * self-registering on construction the way the Fabric helper did.
 */
public final class Quiet {
    public static final KeyMapping NARRATOR_KEY = new KeyMapping(
            "key.quiet.narrator_hotkey", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN, KeyMapping.Category.MISC);

    private static final KeyMapping SKIP_MUSIC_KEY = new KeyMapping(
            "key.quiet.skip_music", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN, KeyMapping.Category.MISC);

    private static final KeyMapping TOGGLE_MUSIC_KEY = new KeyMapping(
            "key.quiet.toggle_music", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN, KeyMapping.Category.MISC);

    /**
     * {@return the client}
     *
     * <p>A method, not a {@code static final} field. Upstream held the instance in a field, which
     * works on Fabric where the initializer runs later; here the class is loaded during mod
     * construction, which happens inside {@code Minecraft.<init>} before the singleton is
     * assigned. The field captured null permanently and every mixin reading it threw.
     */
    public static Minecraft client() {
        return Minecraft.getInstance();
    }

    /** Read by the mixin that suppresses the music manager's countdown. */
    public static boolean musicPaused = false;

    private Quiet() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(NARRATOR_KEY);
            event.register(SKIP_MUSIC_KEY);
            event.register(TOGGLE_MUSIC_KEY);
        });
        modBus.addListener(RegisterDebugEntriesEvent.class, event ->
                event.register(Identifier.fromNamespaceAndPath("duty_client", "fps_history"),
                        new DebugEntryFpsHistory()));
    }

    /** Called once per client tick from {@code DutyClient}. */
    public static void clientTick(Minecraft client) {
        if (client.player == null) {
            return;
        }
        if (SKIP_MUSIC_KEY.consumeClick()) {
            client.player.sendOverlayMessage(Component.translatable("msg.quiet.skip_music"));
            client.getMusicManager().stopPlaying();
            client.getMusicManager().startPlaying(client.getSituationalMusic());
            musicPaused = false;
        }
        if (TOGGLE_MUSIC_KEY.consumeClick()) {
            musicPaused = !musicPaused;
            setMusicPaused(client, musicPaused);
            client.player.sendOverlayMessage(Component.translatable(
                    musicPaused ? "msg.quiet.pause_music" : "msg.quiet.resume_music"));
        }
        // Holding the countdown up is what stops the next track from starting while paused.
        if (musicPaused) {
            client.getMusicManager().nextSongDelay++;
        }
    }

    private static void setMusicPaused(Minecraft client, boolean paused) {
        for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry
                : client.getSoundManager().soundEngine.instanceToChannel.entrySet()) {
            if (entry.getKey().getSource() == SoundSource.MUSIC) {
                entry.getValue().execute(paused ? Channel::pause : Channel::unpause);
            }
        }
    }
}
