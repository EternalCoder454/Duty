package net.dutymod.framework;

import net.dutymod.framework.platform.DutyPlatform;
import net.dutymod.framework.platform.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            out.add(new Finding(Severity.INFO, "Measurement is off",
                    "Nothing below was collected. Run /duty metrics on, play for a few minutes, "
                            + "then ask again."));
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

        return out.toString();
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
        Path path = Paths.get("logs").resolve("duty-report.txt").toAbsolutePath();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, generate().getBytes(StandardCharsets.UTF_8));
            return path;
        } catch (IOException e) {
            DutyLog.warn("Could not write " + path + ": " + e);
            return null;
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
