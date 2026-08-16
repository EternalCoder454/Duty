package net.dutymod.fixerupper.spark;

/**
 * Stub for ModernFix's spark launch profiler.
 *
 * <p>Upstream this drives the spark profiler around startup and world join, writing a sampler
 * report to disk. It compiles against spark's internal API -- not its public one -- which is
 * published only as a CurseForge artifact pinned by numeric file id, and those ids no longer
 * resolve. Rather than pin a guess or edit the half-dozen call sites woven through
 * {@code ModernFixClient} and the platform hooks, the class keeps its shape and does nothing.
 *
 * <p>Nothing is lost for normal use: this only ever produced profiling data for someone
 * diagnosing Duty's own startup, and spark profiles startup perfectly well on its own.
 *
 * <p>To restore it: add spark as a {@code compileOnly} dependency with a current file id and
 * take the real implementation from ModernFix upstream.
 */
public final class SparkLaunchProfiler {
    private SparkLaunchProfiler() {}

    public static void start(String id) {
        // no-op
    }

    public static void stop(String id) {
        // no-op
    }
}
