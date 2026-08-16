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

    /**
     * Runs every frame the debug screen is open.
     *
     * <p>The obvious spelling -- {@code Collections.min}, a {@code stream().reduce} for the sum, and
     * {@code Collections.max} -- walks the deque three times and allocates a stream and its lambda
     * to do it, over as many as 1200 boxed {@link Integer}s. One loop produces the same three
     * numbers with a single traversal and no allocation, unboxing each entry once instead of three
     * times.
     */
    @Override
    public void display(DebugScreenDisplayer debugScreenDisplayer, Level level, LevelChunk levelChunk, LevelChunk levelChunk2) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        long sum = 0;
        int count = 0;
        for (Integer sample : history) {
            int fps = sample;
            if (fps < min) {
                min = fps;
            }
            if (fps > max) {
                max = fps;
            }
            sum += fps;
            count++;
        }
        if (count == 0) {
            // Nothing sampled yet; the original threw from orElseThrow here.
            debugScreenDisplayer.addPriorityLine(client().getFps() + " fps");
            return;
        }
        debugScreenDisplayer.addPriorityLine(String.format(Locale.ROOT,
                "%d fps (%d min %d avg %d max)", client().getFps(), min, sum / count, max));
    }

    @Override
    public boolean isAllowed(boolean bl) {
        return true;
    }
}
