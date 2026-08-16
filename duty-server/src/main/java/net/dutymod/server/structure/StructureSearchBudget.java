package net.dutymod.server.structure;

import net.dutymod.core.DutyConfig;
import net.dutymod.core.DutyLog;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * A watchdog on structure searches, so one bad {@code /locate} cannot pin the server thread.
 *
 * <p>{@code ChunkGenerator.findNearestMapStructure} walks outwards in rings, and every candidate
 * chunk it looks at can force a chunk load to {@code STRUCTURE_STARTS}. When the structure is
 * actually there this terminates quickly. When it is not -- a modded structure with enormous
 * spacing, or one whose biome does not occur anywhere near the player -- the walk runs to the full
 * radius, and the world is frozen for as long as that takes. Treasure maps take the same path from
 * loot generation, so this is not only a command problem.
 *
 * <p>The budget is armed once per search and read on every ring step. Timing out returns "not
 * found", which is what an exhausted search would have returned anyway; the difference is that it
 * takes a bounded amount of time to say so.
 *
 * <h2>Why this is per-thread</h2>
 *
 * <p>The mod this idea came from (Structure Essentials) keeps the deadline in a {@code static}
 * field. Structure searches are not confined to the server thread -- loot generation runs on
 * worker threads, and separate dimensions search independently -- so two overlapping searches
 * there clobber each other's start time, and whichever armed last decides when the other one gives
 * up. A {@link ThreadLocal} costs nothing here (it is read once per ring, not per block) and makes
 * each search's budget its own.
 */
public final class StructureSearchBudget {
    /** Seconds a single structure search may run before it gives up. 0 disables the watchdog. */
    public static final String TIMEOUT_SECONDS = "server.structure_search_timeout";

    /** Per-search state. One instance per thread, reused, so an armed search allocates nothing. */
    private static final class Budget {
        long deadlineNanos;
        long startedNanos;
        boolean armed;
        boolean reported;
        String target = "";
    }

    private static final ThreadLocal<Budget> STATE = ThreadLocal.withInitial(Budget::new);

    static {
        DutyConfig.register(TIMEOUT_SECONDS, 60,
                "Seconds a single structure search may run before it gives up and reports the\n"
                        + "structure as not found. Set to 0 to let searches run as long as they like.\n"
                        + "\n"
                        + "This is a watchdog, not a limit you should expect to hit: a search that\n"
                        + "finds anything finishes in well under a second. It exists for the case\n"
                        + "where the structure is not within range at all, which makes /locate walk\n"
                        + "its entire radius loading chunks, freezing the world until it finishes.\n"
                        + "Treasure maps search the same way, so this also covers loot generation.");
    }

    private StructureSearchBudget() {}

    /** Forces the registration above to run. */
    public static void init() {}

    /** Starts the clock for a search on this thread. Cheap enough to call unconditionally. */
    public static void arm(HolderSet<Structure> targets) {
        int seconds = DutyConfig.getInt(TIMEOUT_SECONDS, 0, 3600);
        Budget budget = STATE.get();
        if (seconds <= 0) {
            budget.armed = false;
            return;
        }
        budget.startedNanos = System.nanoTime();
        budget.deadlineNanos = budget.startedNanos + seconds * 1_000_000_000L;
        budget.armed = true;
        budget.reported = false;
        budget.target = describe(targets);
    }

    /** Ends the search on this thread, whether it found anything or not. */
    public static void disarm() {
        STATE.get().armed = false;
    }

    /**
     * Whether the search running on this thread has outstayed its budget.
     *
     * <p>Returns {@code false} when nothing is armed, so a caller reached by some path that does
     * not go through {@link #arm} is never cut short by a stale deadline.
     */
    public static boolean expired() {
        Budget budget = STATE.get();
        if (!budget.armed || System.nanoTime() < budget.deadlineNanos) {
            return false;
        }
        if (!budget.reported) {
            budget.reported = true;
            long millis = (System.nanoTime() - budget.startedNanos) / 1_000_000L;
            DutyLog.info("Structure search for " + budget.target + " gave up after " + millis
                    + "ms; it would have kept the server thread busy for longer still. Raise "
                    + TIMEOUT_SECONDS + " if this structure really is that far away.");
        }
        return true;
    }

    private static String describe(HolderSet<Structure> targets) {
        for (Holder<Structure> holder : targets) {
            var key = holder.unwrapKey();
            if (key.isPresent()) {
                return key.get().identifier().toString();
            }
        }
        return "an unnamed structure";
    }
}
