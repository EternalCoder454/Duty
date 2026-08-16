package net.dutymod.memory.enums;

import net.dutymod.core.DutyConfig;
import net.dutymod.core.DutyLog;
import net.dutymod.memory.MemoryOptions;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ProcessorName;

import java.util.Set;

/**
 * Installs the {@code Enum.values()} optimization into FML's class-loading pipeline.
 *
 * <h2>On the "transformed 100.00% of loaded class" warning</h2>
 *
 * <p>FML logs an error when a processor looks like it is doing mass-ASM:
 *
 * <pre>
 * Class processor duty_memory:enum_values transformed 100.00% of loaded class which is
 * suspiciously high; it may be attempting mass-ASM. Please report this to the mod author.
 * </pre>
 *
 * <p>That percentage does not mean what it sounds like. In {@code ClassTransformStatistics} the
 * numerator is incremented from {@code ClassProcessorSet#transformersFor} every time
 * {@link #handlesClass} returns {@code true} -- that is, every time a processor is <em>offered</em> a
 * class -- and never from the result of {@link #processClass}. So the figure reports "classes this
 * processor asked to look at", not "classes this processor changed". A processor that inspects
 * everything and rewrites almost nothing reports 100%, identically to one that rewrites everything.
 *
 * <p>The reason this processor cannot answer more precisely is that {@link SelectionContext} carries
 * only the class's {@link org.objectweb.asm.Type} and whether it is empty. There is no bytecode at
 * selection time, and whether a class contains a {@code values()} call simply cannot be determined
 * from its name. The {@code BytecodeProvider} handed to {@link #link} is not a way out either: it
 * runs the processor chain to produce the bytes, so calling it for the class currently being loaded
 * would re-enter the pipeline.
 *
 * <p>What Duty does about it:
 *
 * <ul>
 *   <li>{@link #handlesClass} rejects the packages that provably cannot benefit -- the JDK, ASM,
 *       and our own generated holders. This is a real saving, not just a smaller number: rejected
 *       classes never get a {@link org.objectweb.asm.tree.ClassNode} built for this processor.
 *   <li>{@link #processClass} returns {@link ComputeFlags#NO_REWRITE} whenever nothing changed, so
 *       untouched classes cost one cheap instruction scan and are then passed through.
 * </ul>
 *
 * <p>The warning will still appear, because Minecraft and mod classes are the bulk of what gets
 * loaded and this optimization is inherently whole-program. It is benign. The accurate fix belongs
 * in FML -- counting {@code ComputeFlags != NO_REWRITE} rather than {@code handlesClass} would make
 * the statistic mean what its message claims, and would put this processor well under the 25%
 * threshold, since the share of classes containing a rewritable {@code values()} call is small.
 */
public final class EnumValuesProcessor implements ClassProcessor {
    private static final ProcessorName NAME = new ProcessorName("duty_memory", "enum_values");

    /**
     * Packages where the optimization is either impossible or pointless.
     *
     * <p>The JDK is loaded by the boot loader and is not ours to rewrite. ASM and the Mixin runtime
     * are part of the transformation machinery itself; touching them during class loading invites
     * cycles. Our own generated holders must obviously not be rewritten to call themselves.
     */
    private static final String[] EXCLUDED_PREFIXES = {
            "java/", "javax/", "jdk/", "sun/", "com/sun/",
            "org/objectweb/asm/",
            "org/spongepowered/asm/",
            "net/neoforged/fml/",
            AsmNames.GENERATED_PREFIX,
    };

    private final boolean enabled;
    private final EnumValuesTransformer transformer;

    public EnumValuesProcessor() {
        // Touch the option holder so the keys exist before anything reads them.
        MemoryOptions.init();
        this.enabled = DutyConfig.get(MemoryOptions.ENUM_VALUES_CACHING);
        this.transformer = new EnumValuesTransformer(DutyConfig.get(MemoryOptions.ENUM_VALUES_LOG_REWRITES));
        if (!enabled) {
            DutyLog.info("Enum.values() caching is disabled in config; Duty will not transform classes.");
        }
    }

    @Override
    public ProcessorName name() {
        return NAME;
    }

    @Override
    public boolean handlesClass(SelectionContext context) {
        if (!enabled) {
            return false;
        }
        String internalName = context.type().getInternalName();
        // We materialize these ourselves, so we must be offered them even though they are "empty".
        if (AsmNames.isCacheClass(internalName)) {
            return true;
        }
        if (context.empty()) {
            // Nothing on disk and not one of ours: there is no bytecode to optimize.
            return false;
        }
        for (String prefix : EXCLUDED_PREFIXES) {
            if (internalName.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ComputeFlags processClass(TransformationContext context) {
        String internalName = context.type().getInternalName();

        if (AsmNames.isCacheClass(internalName)) {
            // FML asked for a class in the package we declared in generatesPackages(). Fill it in.
            CacheClassGenerator.generateInto(context.node(), internalName);
            context.audit("generated enum values cache holder");
            return ComputeFlags.SIMPLE_REWRITE;
        }

        int rewritten = transformer.transform(context.node());
        if (rewritten == 0) {
            return ComputeFlags.NO_REWRITE;
        }
        context.audit("rewrote Enum.values() call sites", Integer.toString(rewritten));
        // The rewrite swaps one reference-producing instruction for another plus a CHECKCAST;
        // stack depth and frames are unchanged, so no recomputation is required.
        return ComputeFlags.SIMPLE_REWRITE;
    }

    /**
     * Declares the package our generated cache holders live in.
     *
     * <p>This is what lets the holders be produced through the normal class-loading path. Upstream
     * Jasione instead reaches for the transforming classloader and calls {@code defineClass}
     * reflectively; on a module-strict Java 25 runtime that is both fragile and racy -- it has to
     * catch {@link LinkageError} to paper over two threads defining the same class. Letting FML ask
     * us for the class when it is first referenced removes the reflection and the race together.
     */
    @Override
    public Set<String> generatesPackages() {
        return Set.of(AsmNames.GENERATED_PACKAGE);
    }

    @Override
    public OrderingHint orderingHint() {
        return OrderingHint.LATE;
    }

    @Override
    public Set<ProcessorName> runsAfter() {
        // Mixins must be applied first: we want to optimize the final bytecode, including any
        // values() calls that mixins introduced.
        return Set.of(ClassProcessorIds.MIXIN, ClassProcessorIds.SIMPLE_PROCESSORS_GROUP);
    }
}
