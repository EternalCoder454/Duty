package net.dutymod.client.quiet;

import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Locale;

import static net.dutymod.client.quiet.Quiet.client;

/** Records a rolling FPS history for the F3 screen. Fed by {@link Quiet#clientTick}. */
public class DebugEntryFpsHistory implements DebugScreenEntry {
    private static final ArrayDeque<Integer> history = new ArrayDeque<>(1200);

    static {
        // Sampled from DutyClient's tick listener instead of a Fabric lifecycle event.
    }

    @Override
    public void display(DebugScreenDisplayer debugScreenDisplayer, Level level, LevelChunk levelChunk, LevelChunk levelChunk2) {
        debugScreenDisplayer.addPriorityLine(
                String.format(Locale.ROOT, "%d fps (%d min %d avg %d max)", client().getFps(), Collections.min(history), history.stream().reduce(Integer::sum).orElseThrow() / history.size(), Collections.max(history))
        );
    }

    @Override
    public boolean isAllowed(boolean bl) {
        return true;
    }
}
