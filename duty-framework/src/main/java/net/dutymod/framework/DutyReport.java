package net.dutymod.framework;

import net.dutymod.framework.platform.DutyPlatform;
import net.dutymod.framework.platform.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Turns Duty's measurements into a list of things worth looking at.
 *
 * <h2>Why a table was not enough</h2>
 *
 * <p>{@link DutyMetrics} answers "what did this cost". Reading its table still means knowing that a
 * worst case forty times the mean is a spike rather than a slow path, that a total is meaningless
 * without the window it was collected over, and that a timer with no samples means the session never
 * exercised it rather than that it is free. Every one of those was a judgement someone had to make
 * by eye, and the judgements are mechanical.
 *
 * <p>So this applies them. It reads the same numbers and emits findings: short statements about what
 * looks wrong and what it would mean, ranked so the top of the list is the thing to look at first.
 * The numbers stay in the report underneath, because a finding you cannot check is just an opinion.
 *
 * <h2>What it deliberately does not do</h2>
 *
 * <p>It does not conclude. A spike finding says a pass was forty times its median and names the
 * candidate count; it does not say the cause, because the data does not contain the cause. Every
 * threshold below is stated in the finding text so a reader can disagree with it.
 */
public final class DutyReport {

    /** A pass this much slower than its median is a spike rather than a slow path. */
    private static final double SPIKE_RATIO = 8.0d;

    /** Below this, a spike is not worth anyone's attention however bad the ratio looks. */
    private static final double SPIKE_FLOOR_MILLIS = 5.0d;

    /** A timer occupying this share of the measurement window leads the report. */
    private static final double DOMINANT_SHARE = 0.02d;

    /** A single call costing this much is a stall someone would feel. */
    private static final double STALL_MILLIS = 100.0d;

    /**
     * Below this many samples, a distribution is not a distribution.
     *
     * <p>One sample has median == p99 == worst, which trips "consistently slow" every time and
     * says it about a thing that happened once. Reported structure.search as a systemic problem
     * on the strength of a single /locate before this existed.
     */
    private static final long MIN_SAMPLES_FOR_SHAPE = 20L;

    private static final List<Contributor> CONTRIBUTORS = new CopyOnWriteArrayList<>();

    public static final String ON_WORLD_CLOSE = "framework.report_on_world_close";

    private static final java.util.concurrent.atomic.AtomicBoolean HOOK_INSTALLED =
            new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Registers the options this class owns and arranges for a final report on the way out.
     *
     * <p>The shutdown hook is the crash net. A Minecraft crash is an exception that unwinds and
     * exits normally, so hooks still run -- which is the difference between a session ending in a
     * crash report with no measurements and one that leaves the numbers describing the moments
     * before it went wrong. It cannot help with a JVM abort or a kill, and nothing can.
     */
    static void install() {
        if (!HOOK_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        DutyConfig.register(ON_WORLD_CLOSE, true,
                "Write logs/duty-report.txt automatically whenever a world closes, and once more\n"
                        + "when the game exits. Without this, a session that ends in a crash takes\n"
                        + "its measurements with it.");
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (DutyConfig.get(ON_WORLD_CLOSE)
                        && (DutyMetrics.enabled() || DutyGc.count() > 0L)) {
                    writeToFile("the game exited");
                }
            }, "Duty final report"));
        } catch (Throwable t) {
            DutyLog.warn("Could not install the final-report hook: " + t);
        }
    }

    private DutyReport() {}

    /** How much a finding matters. Ordering here is the ordering in the report. */
    public enum Severity {
        /** Something is wrong or being wasted. */
        PROBLEM,
        /** Worth a look, may be fine. */
        NOTICE,
        /** Context that helps read the rest. */
        INFO
    }

    /** One thing worth looking at. */
    public record Finding(Severity severity, String title, String detail) {}

    /** A module's own analysis, added to the generic findings below. */
    @FunctionalInterface
    public interface Contributor {
        void contribute(List<Finding> findings);
    }

    /**
     * Registers a module's analysis.
     *
     * <p>The generic rules here only see timers and counters, so anything needing to know what a
     * number <em>means</em> -- that traced-versus-hidden is a hit rate, that a watchdog firing is
     * bad -- belongs in a contributor owned by the module that knows.
     */
    public static void contributor(Contributor contributor) {
        CONTRIBUTORS.add(contributor);
    }

    /** {@return the findings, most important first} */
    public static List<Finding> findings() {
        List<Finding> out = new ArrayList<>();

        if (!DutyMetrics.enabled()) {
            // Said carefully, because the previous wording ("nothing below was collected") sat
            // directly above findings that had plainly collected something. Counters, heap and GC
            // do not depend on measurement being on; only the timings do.
            // Off does not mean empty. Turning measurement off keeps whatever it already
            // collected, so a session that measured and then stopped has a full table of
            // timings sitting under a finding that used to announce there were none. Saying
            // "nothing below reports how long anything took" above four populated timers is
            // how a report teaches people to stop reading it.
            boolean anyTimings = false;
            for (DutyMetrics.Timer timer : DutyMetrics.timers()) {
                if (timer.count() > 0L) {
                    anyTimings = true;
                    break;
                }
            }
            if (anyTimings) {
                out.add(new Finding(Severity.INFO, "Timings stopped before this report",
                        "Measurement is off now, but it was on earlier, so the timings below are "
                                + "real and cover only the part of the session it was on for. The "
                                + "window figure is what they should be read against."));
            } else {
                out.add(new Finding(Severity.INFO, "Timings are off",
                        "No timer is running and none has collected anything, so nothing below "
                                + "reports how long anything took. Counters, heap and garbage "
                                + "collection are recorded regardless and any findings about them "
                                + "are real. For timings, run /duty metrics on, play for a few "
                                + "minutes, then ask again."));
            }
        }

        long windowNanos = DutyMetrics.windowNanos();
        double windowMillis = windowNanos / 1.0e6;

        // timers() is already sorted by total, so the first is the only one that can be "busiest".
        // Emitting the share finding per timer said "busiest thing measured" three times in a row,
        // which is both wrong and the kind of thing that makes a report stop being read.
        List<DutyMetrics.Timer> ranked = DutyMetrics.timers();
        DutyMetrics.Timer busiest = ranked.isEmpty() ? null : ranked.get(0);

        // A share is only meaningful if the window plausibly contains the work. A window shorter
        // than the total means measurement was restarted, or the totals came from several threads
        // at once -- either way "% of one core" would be nonsense, and it printed 18831% before
        // this guard existed.
        boolean windowUsable = windowMillis >= 1000.0d;

        for (DutyMetrics.Timer timer : ranked) {
            long calls = timer.count();
            double totalMillis = timer.totalNanos() / 1.0e6;
            double worstMillis = timer.maxNanos() / 1.0e6;
            double medianMillis = timer.percentileMillis(0.50d);
            double p99Millis = timer.percentileMillis(0.99d);

            // A single call nobody would miss is not interesting however large the ratio.
            if (calls >= MIN_SAMPLES_FOR_SHAPE && worstMillis >= SPIKE_FLOOR_MILLIS
                    && medianMillis > 0 && worstMillis / medianMillis >= SPIKE_RATIO) {
                out.add(new Finding(Severity.NOTICE,
                        timer.name() + " spikes",
                        String.format(Locale.ROOT,
                                "worst %.1fms against a %.3fms median (%.0fx). The 99th percentile "
                                        + "is %.3fms, so this is a few bad passes rather than a "
                                        + "consistently slow path -- look for what made those "
                                        + "passes different, not at the code they all run.",
                                worstMillis, medianMillis, worstMillis / medianMillis, p99Millis)));
            } else if (calls >= MIN_SAMPLES_FOR_SHAPE && worstMillis >= SPIKE_FLOOR_MILLIS
                    && medianMillis > 0 && p99Millis / medianMillis < 2.0d
                    && medianMillis >= SPIKE_FLOOR_MILLIS) {
                out.add(new Finding(Severity.PROBLEM,
                        timer.name() + " is consistently slow",
                        String.format(Locale.ROOT,
                                "median %.3fms and 99th percentile %.3fms are close together, so "
                                        + "nearly every call costs this. That is the code itself, "
                                        + "not an outlier.",
                                medianMillis, p99Millis)));
            }

            if (timer == busiest && windowUsable && totalMillis / windowMillis >= DOMINANT_SHARE) {
                out.add(new Finding(Severity.INFO,
                        timer.name() + " is the busiest thing measured",
                        String.format(Locale.ROOT,
                                "%.1fms over a %.0fs window, %.1f%% of one core, across %d calls "
                                        + "at %.3fms each. Necessary work can look like this; it "
                                        + "is only a problem if the per-call figure is high.",
                                totalMillis, windowMillis / 1000.0d,
                                100.0d * totalMillis / windowMillis, calls, totalMillis / calls)));
            }

            if (calls <= 5 && worstMillis >= STALL_MILLIS) {
                out.add(new Finding(Severity.PROBLEM,
                        timer.name() + " stalled",
                        String.format(Locale.ROOT,
                                "%d call(s), worst %.0fms. Something waited that long. Rare enough "
                                        + "not to show in an average, long enough to be felt.",
                                calls, worstMillis)));
            }
        }

        appendHeapFinding(out);
        appendCounterOnlyFindings(out);

        for (Contributor contributor : CONTRIBUTORS) {
            try {
                contributor.contribute(out);
            } catch (Throwable t) {
                DutyLog.warn("A report contributor failed: " + t);
            }
        }

        if (out.isEmpty()) {
            out.add(new Finding(Severity.INFO, "Nothing stands out",
                    "No timer is spiking, consistently slow, or stalling. Either things are "
                            + "healthy or the session did not exercise them -- check the call "
                            + "counts below before concluding the first."));
        }

        out.sort(Comparator.comparingInt(f -> f.severity().ordinal()));
        return out;
    }

    /**
     * Flags heap pressure, which colours everything else in the report.
     *
     * <p>Worth stating outright because a heap near its ceiling makes every timer look worse: GC
     * pauses land inside whatever happened to be running and show up as spikes in unrelated
     * measurements. Somebody reading a spike finding should know whether to believe it.
     */
    private static void appendHeapFinding(List<Finding> out) {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        if (max <= 0 || max == Long.MAX_VALUE) {
            return;
        }
        long used = runtime.totalMemory() - runtime.freeMemory();
        double share = (double) used / max;
        long liveSet = DutyGc.liveSetBytes();

        if (share >= 0.85d) {
            // "Used" counts garbage the collector has not got to yet, so on its own a high figure
            // is not evidence of anything -- a heap sitting at 90% with a small live set is a
            // collector doing its job lazily, which is what it is supposed to do. The live set is
            // what says whether the ceiling is actually too low, so the advice waits for it.
            String verdict;
            if (liveSet < 0L) {
                verdict = "No major collection has happened yet, so how much of this is live is "
                        + "not known. Ignore the percentage until one has.";
            } else {
                double liveShare = (double) liveSet / max;
                verdict = String.format(Locale.ROOT,
                        "Of that, %d MiB (%.0f%% of the maximum) was still reachable after the "
                                + "last major collection -- that is the real requirement. ",
                        liveSet >> 20, liveShare * 100.0d)
                        + (liveShare >= 0.60d
                                ? "That is high enough that the heap genuinely is too small; "
                                        + "raising the maximum is the fix."
                                : "The heap is big enough. The rest is garbage awaiting "
                                        + "collection, so raising the maximum would only make "
                                        + "collections rarer and larger, not fix a stall.");
            }
            out.add(new Finding(Severity.PROBLEM, "Heap is nearly full",
                    String.format(Locale.ROOT,
                            "%d MiB used of %d MiB (%.0f%%). Treat every spike below with "
                                    + "suspicion: at this level a collection pause lands inside "
                                    + "whatever was running and is measured as that thing being "
                                    + "slow. %s",
                            used >> 20, max >> 20, share * 100.0d, verdict)));
        }

        appendGcFinding(out, max, liveSet);
    }

    /**
     * Reports what the collector actually did.
     *
     * <p>This is the finding that stops the rest of the report lying. A stop-the-world pause is
     * attributed by every timer to whichever call it interrupted, so without a GC line a 45ms
     * culling spike and a 45ms GC pause are the same observation wearing different labels.
     */
    /**
     * {@return a byte count at a readable scale}
     *
     * <p>Allocation rates span three or four orders of magnitude between an idle menu and a world
     * being generated, so a fixed unit is unreadable at one end or the other.
     */
    private static String bytes(long value) {
        if (value < 1024L) {
            return value + " B";
        }
        if (value < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KiB", value / 1024.0d);
        }
        if (value < 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MiB", value / (1024.0d * 1024.0d));
        }
        return String.format(Locale.ROOT, "%.2f GiB", value / (1024.0d * 1024.0d * 1024.0d));
    }

    private static void appendGcFinding(List<Finding> out, long max, long liveSet) {
        long collections = DutyGc.count();
        if (collections == 0L) {
            return;
        }
        long worst = DutyGc.worstPauseMillis();
        long longPauses = DutyGc.longPauses();
        boolean concurrent = DutyGc.mostlyConcurrent();

        String collectors = String.join(", ", DutyGc.collectors());
        String base = String.format(Locale.ROOT,
                "%d collection(s), %d major, %dms total, worst %dms (%s). Collector: %s.",
                collections, DutyGc.majorCount(), DutyGc.totalPauseMillis(), worst,
                DutyGc.worstCause().isEmpty() ? "cause unknown" : DutyGc.worstCause(),
                collectors.isEmpty() ? "unknown" : collectors);

        // The rate belongs next to the pauses, because on its own each is misleading. Pauses say
        // whether the collector is coping; the rate says how much it is being asked to do, and a
        // module whose purpose is to allocate less should be read on the second.
        final double allocationRate = DutyGc.allocationBytesPerSecond();
        if (allocationRate >= 0.0d) {
            base = base + String.format(Locale.ROOT, " Allocating %s/s (%s total since startup).",
                    bytes((long) allocationRate), bytes(DutyGc.allocatedBytes()));
        }

        if (concurrent) {
            // Reporting a concurrent cycle as a stall is the one mistake this section could make
            // that would send somebody tuning the wrong thing.
            out.add(new Finding(Severity.INFO, "Garbage collection (mostly concurrent)",
                    base + " This collector does most of its work while the game runs, so these "
                            + "durations are not freezes and should not be read as pauses."));
            return;
        }

        if (worst >= 100L) {
            String advice = liveSet >= 0L && (double) liveSet / max < 0.60d
                    ? "The live set is comfortably inside the heap, so this is the collector's "
                            + "pause behaviour rather than a shortage of memory. A mostly "
                            + "concurrent collector removes pauses of this length without needing "
                            + "a larger heap."
                    : "Long enough to be seen as the window freezing.";
            out.add(new Finding(Severity.PROBLEM, "Garbage collection is pausing the game",
                    base + " " + longPauses + " pause(s) were 100ms or longer. " + advice));
        } else {
            out.add(new Finding(Severity.INFO, "Garbage collection looks healthy",
                    base + " Nothing here is long enough to be visible as a freeze."));
        }
    }

    /**
     * Explains a counter that moved while its timer did not.
     *
     * <p>Counters always count; timers only run while measurement is on. So a session that enables
     * measurement partway through leaves pairs like {@code server.save.count} at five with
     * {@code server.save.write} absent entirely -- which reads as a bug and is not one. Saying so
     * costs a line and saves the reader working it out.
     */
    private static void appendCounterOnlyFindings(List<Finding> out) {
        List<String> orphans = new ArrayList<>();
        for (DutyMetrics.Counter counter : DutyMetrics.counters()) {
            String base = counter.name();
            int dot = base.lastIndexOf('.');
            if (dot <= 0) {
                continue;
            }
            String group = base.substring(0, dot);
            boolean anyTimer = false;
            for (DutyMetrics.Timer timer : DutyMetrics.timers()) {
                if (timer.name().startsWith(group + ".")) {
                    anyTimer = true;
                    break;
                }
            }
            if (!anyTimer) {
                orphans.add(base);
            }
        }
        if (orphans.isEmpty()) {
            return;
        }
        out.add(new Finding(Severity.INFO, "Some counters have no timings",
                "Counters run always; timers only while measurement is on, so anything that "
                        + "happened before it was switched on is counted but not timed. Not a "
                        + "fault: " + String.join(", ", orphans)));
    }

    /** {@return the whole report: findings, then the numbers behind them, then the environment} */
    public static String generate() {
        StringBuilder out = new StringBuilder(2048);
        out.append("================ Duty report ================\n");

        List<Finding> findings = findings();
        out.append("\n-- What to look at ------------------------\n");
        int n = 0;
        for (Finding finding : findings) {
            out.append(String.format(Locale.ROOT, "%n%d. [%s] %s%n", ++n,
                    finding.severity(), finding.title()));
            for (String line : wrap(finding.detail(), 76)) {
                out.append("   ").append(line).append('\n');
            }
        }

        out.append("\n-- Numbers --------------------------------\n\n");
        out.append(DutyMetrics.report()).append('\n');

        List<DutyMetrics.Timer> timers = DutyMetrics.timers();
        if (!timers.isEmpty()) {
            out.append(String.format(Locale.ROOT, "%n  %-34s %9s %9s %9s%n",
                    "timer", "median", "p99", "worst"));
            for (DutyMetrics.Timer timer : timers) {
                out.append(String.format(Locale.ROOT, "  %-34s %7.3fms %7.3fms %7.3fms%n",
                        timer.name(), timer.percentileMillis(0.50d),
                        timer.percentileMillis(0.99d), timer.maxNanos() / 1.0e6));
            }
        }

        out.append("\n-- Environment ----------------------------\n");
        appendEnvironment(out);

        out.append("\n-- Installed mods -------------------------\n");
        appendMods(out);

        return out.toString();
    }

    /**
     * Lists every installed mod and its version.
     *
     * <p>The most useful section here, and the reason it exists: a crash caused by a mod being the
     * wrong version is indistinguishable from a crash caused by a bug until somebody lists what is
     * actually loaded. An hour went into exactly that -- a stock Iris sitting beside the fork built
     * to replace it -- and one line of this section would have shown it at a glance.
     *
     * <p>Duty's own modules are pulled to the top, because "which build am I running" is the first
     * question about any of them.
     */
    private static void appendMods(StringBuilder out) {
        java.util.Map<String, String> mods;
        try {
            mods = Platform.get().installedMods();
        } catch (Throwable t) {
            out.append("  unavailable (").append(t.getClass().getSimpleName()).append(")\n");
            return;
        }
        if (mods.isEmpty()) {
            out.append("  none reported -- asked before the loader had a list\n");
            return;
        }

        List<String> ours = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (java.util.Map.Entry<String, String> entry : mods.entrySet()) {
            String line = String.format(Locale.ROOT, "  %-38s %s", entry.getKey(), entry.getValue());
            (entry.getKey().startsWith("duty") ? ours : others).add(line);
        }

        out.append("  ").append(mods.size()).append(" mod(s)\n\n");
        for (String line : ours) {
            out.append(line).append('\n');
        }
        if (!ours.isEmpty() && !others.isEmpty()) {
            out.append('\n');
        }
        for (String line : others) {
            out.append(line).append('\n');
        }
    }

    private static void appendEnvironment(StringBuilder out) {
        try {
            DutyPlatform platform = Platform.get();
            out.append("  loader        ").append(platform.loader()).append('\n');
            out.append("  minecraft     ").append(platform.minecraftVersion()).append('\n');
        } catch (Throwable t) {
            out.append("  platform      unavailable (").append(t.getClass().getSimpleName()).append(")\n");
        }
        out.append("  java          ").append(System.getProperty("java.version")).append('\n');
        out.append("  cpus          ").append(Runtime.getRuntime().availableProcessors()).append('\n');

        Runtime runtime = Runtime.getRuntime();
        long usedMib = (runtime.totalMemory() - runtime.freeMemory()) >> 20;
        out.append("  heap          ").append(usedMib).append(" MiB used of ")
                .append(runtime.maxMemory() >> 20).append(" MiB max\n");

        long window = DutyMetrics.windowNanos();
        out.append("  measuring     ").append(DutyMetrics.enabled() ? "on" : "off");
        if (window > 0) {
            out.append(String.format(Locale.ROOT, ", window %.0fs", window / 1.0e9));
        }
        out.append('\n');

        // Only the options that differ from their default, because a full dump of every key is
        // noise and the interesting thing is always what someone changed.
        List<String> changed = new ArrayList<>();
        for (DutyConfig.Option option : DutyConfig.options()) {
            String value = DutyConfig.rawOrDefault(option.key());
            if (!value.trim().equals(option.defaultValue().trim())) {
                changed.add(option.key() + "=" + value.trim()
                        + "  (default " + option.defaultValue().trim() + ")");
            }
        }
        if (changed.isEmpty()) {
            out.append("  config        all defaults\n");
        } else {
            out.append("  config        ").append(changed.size()).append(" option(s) changed:\n");
            for (String line : changed) {
                out.append("                  ").append(line).append('\n');
            }
        }
    }

    /** Writes the report next to the log and {@return the path}, or null if it could not be written. */
    public static Path writeToFile() {
        return writeToFile("asked for");
    }

    /**
     * Writes the report, recording what prompted it.
     *
     * <p>One file, always the freshest: an autosave, a world closing, the JVM exiting and
     * {@code /duty report} all land here. The alternative -- a file per trigger -- means working
     * out which of four files is the interesting one at exactly the moment something has gone
     * wrong. The trigger is written into the report instead, which answers the same question
     * without the filing.
     *
     * <p>Written to a temporary file and moved into place, because the whole point of the autosave
     * is to survive a crash and a crash during the write would otherwise leave a truncated report
     * where the previous good one used to be.
     */
    public static synchronized Path writeToFile(String trigger) {
        Path path = Paths.get("logs").resolve("duty-report.txt").toAbsolutePath();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String body = "Written because: " + trigger + "\n" + generate();
            Path temp = path.resolveSibling("duty-report.txt.tmp");
            Files.write(temp, body.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return path;
        } catch (IOException e) {
            DutyLog.warn("Could not write " + path + ": " + e);
            return null;
        }
    }

    /**
     * Writes a report when a world closes, if configured to.
     *
     * <p>Default on: the interesting session is the one that just happened, and asking somebody to
     * remember to run a command before leaving is asking them to lose the data.
     */
    public static void onWorldClose() {
        if (!DutyConfig.get(ON_WORLD_CLOSE)) {
            return;
        }
        if (!DutyMetrics.enabled() && DutyGc.count() == 0L) {
            return;
        }
        Path written = writeToFile("a world closed");
        if (written != null) {
            DutyLog.info("Wrote " + written);
        }
    }

    /** Greedy wrap, so a long finding stays readable in chat and in a log line. */
    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder(width);
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }
}
