package net.dutymod.client.culling;

import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Locale;

/**
 * Reports what occlusion culling costs and what it buys, on the F3 screen.
 *
 * <p>{@code CullTask} has always timed its passes and nothing ever read the number, so the most
 * expensive thing Duty does had no visible cost at all. That is a bad position to make decisions
 * from: whether to move this work to the GPU, whether to widen the tracing distance, whether the
 * whole feature earns its thread -- none of those can be answered by reading the code.
 *
 * <p>The line reads {@code Duty culling: 1.8ms, 142 traced, 96 hidden}. The three numbers together
 * are what matter rather than any one:
 *
 * <ul>
 *   <li><b>Time</b> is per pass, and a pass only runs when the camera has moved. Standing still
 *       costs nothing, so this is a worst case rather than a steady state.
 *   <li><b>Traced</b> is how many entities and block entities were actually ray-traced, after the
 *       distance, size and whitelist filters have thrown work away.
 *   <li><b>Hidden</b> is how many of those turned out to be occluded -- the actual saving, since
 *       each one is a render call that did not happen.
 * </ul>
 *
 * <p>A high time with a low hidden count means the tracing is not paying for itself. A low time
 * with a high hidden count means it is.
 */
public final class DebugEntryCulling implements DebugScreenEntry {
    @Override
    public void display(DebugScreenDisplayer displayer, Level level, LevelChunk chunk, LevelChunk serverChunk) {
        CullTask task = EntityCulling.get().task();
        if (task == null) {
            return;
        }
        displayer.addLine(String.format(Locale.ROOT,
                "Duty culling: %.2fms, %d traced, %d hidden",
                task.lastPassMillis, task.lastPassTraced, task.lastPassHidden));
    }

    @Override
    public boolean isAllowed(boolean reducedDebugInfo) {
        // Hidden under reduced debug info, like the rest of the profiling lines.
        return !reducedDebugInfo;
    }
}
