package net.dutymod.memory.enums;

import net.dutymod.framework.DutyLog;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;

import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites {@code SomeEnum.values()} call sites to read from a shared array instead of allocating a
 * fresh copy.
 *
 * <p>{@code values()} is compiled to {@code return VALUES.clone()}. On a hot path -- and the game
 * has plenty, iterating {@code Direction.values()} being the classic -- that clone is pure garbage:
 * allocated, read once, discarded. Removing it does not speed up any single call much, it reduces
 * how often the collector has to run at all.
 *
 * <p>Correctness rests entirely on {@link EscapeInterpreter}: the rewrite only happens where the
 * array provably cannot be mutated or leaked. Everything else is left exactly as the compiler
 * emitted it.
 */
final class EnumValuesTransformer {
    private final boolean logRewrites;

    EnumValuesTransformer(boolean logRewrites) {
        this.logRewrites = logRewrites;
    }

    /**
     * Rewrites every provably-safe {@code values()} call in {@code node}.
     *
     * @return the number of call sites rewritten; zero means the class was left untouched
     */
    int transform(ClassNode node) {
        int rewritten = 0;
        for (MethodNode method : node.methods) {
            // `VALUES = values()` in a static initializer is the enum's own defensive copy, and
            // <clinit> runs once. Nothing to win, and rewriting it risks recursion into the cache
            // holder we are about to generate.
            if (AsmNames.CLINIT.equals(method.name)) {
                continue;
            }
            rewritten += transformMethod(node, method);
        }
        if (rewritten > 0 && logRewrites) {
            DutyLog.info("Rewrote " + rewritten + " Enum.values() call site"
                    + (rewritten == 1 ? "" : "s") + " in " + node.name);
        }
        return rewritten;
    }

    private int transformMethod(ClassNode classNode, MethodNode method) {
        // Cheap scan first. The dataflow analysis below is not free, so only pay for it in methods
        // that actually contain a candidate call -- which is a small minority.
        List<MethodInsnNode> candidates = null;
        for (var insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && looksLikeEnumValues(call)) {
                if (candidates == null) {
                    candidates = new ArrayList<>(2);
                }
                candidates.add(call);
            }
        }
        if (candidates == null) {
            return 0;
        }

        int rewritten = 0;
        for (MethodInsnNode call : candidates) {
            if (isSafe(classNode.name, method, call)) {
                rewrite(call, method);
                rewritten++;
            }
        }
        return rewritten;
    }

    /**
     * {@return whether {@code insn} has the exact shape of a compiler-generated {@code values()}}
     *
     * <p>A static method named {@code values} taking nothing and returning an array of its own
     * owner. Non-enum classes can match this by coincidence; that is fine, because the generated
     * cache holder checks {@code isEnum} at runtime and falls back to calling the original method.
     * Being loose here and strict there is much safer than trying to resolve the class now, during
     * class loading, when the enum may not be loadable yet.
     */
    private static boolean looksLikeEnumValues(MethodInsnNode insn) {
        return insn.getOpcode() == Opcodes.INVOKESTATIC
                && insn.name.equals(AsmNames.VALUES)
                && insn.desc.equals("()[L" + insn.owner + ";");
    }

    private boolean isSafe(String className, MethodNode method, MethodInsnNode call) {
        EscapeInterpreter interpreter = new EscapeInterpreter(call);
        Analyzer<TrackedValue> analyzer = new Analyzer<>(interpreter);

        // MixinExtras' synthesized methods arrive without usable maxStack/maxLocals, which the
        // analyzer requires. Supply workable values for the duration of the analysis and put the
        // originals back afterwards -- we must not perturb what other processors will see.
        boolean patchMaxs = needsSyntheticMaxs(method.name);
        int originalMaxStack = method.maxStack;
        int originalMaxLocals = method.maxLocals;
        if (patchMaxs) {
            applySyntheticMaxs(method);
        }

        try {
            analyzer.analyze(className, method);
        } catch (AnalyzerException e) {
            // Could not prove anything, so change nothing.
            DutyLog.debug("Skipping " + className + "#" + method.name + ": " + e.getMessage());
            return false;
        } finally {
            if (patchMaxs) {
                method.maxStack = originalMaxStack;
                method.maxLocals = originalMaxLocals;
            }
        }
        return !interpreter.hasViolation();
    }

    private static boolean needsSyntheticMaxs(String methodName) {
        return methodName.contains("mixinextras$wrapped") || methodName.contains("mixinextras$bridge");
    }

    private static void applySyntheticMaxs(MethodNode method) {
        int argsAndReturn = Type.getArgumentsAndReturnSizes(method.desc);
        int locals = argsAndReturn >> 2;
        if ((method.access & Opcodes.ACC_STATIC) != 0) {
            locals--;
        }
        method.maxLocals = Math.max(method.maxLocals, locals);
        // A generous constant rather than a real computation: these are small synthesized bridges,
        // and the values are discarded again as soon as the analysis finishes.
        method.maxStack = Math.max(method.maxStack, 16);
    }

    /**
     * Replaces the {@code values()} invocation with a call to the generated cache holder.
     *
     * <p>The holder returns {@code Object[]}, so a {@code CHECKCAST} back to the concrete array type
     * follows. Both the call and the cast are routinely eliminated by the JIT, leaving a plain field
     * read where there used to be an allocation.
     */
    private static void rewrite(MethodInsnNode call, MethodNode method) {
        String cacheClass = AsmNames.cacheClassFor(call.owner);
        String arrayDescriptor = call.desc.substring(2); // "()[LFoo;" -> "[LFoo;"

        InsnList replacement = new InsnList();
        replacement.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, cacheClass, AsmNames.VALUES, AsmNames.DESC_RETURNS_OBJECT_ARRAY, false));
        replacement.add(new TypeInsnNode(Opcodes.CHECKCAST, arrayDescriptor));

        method.instructions.insert(call, replacement);
        method.instructions.remove(call);
        // Stack depth is unchanged: one reference out, one reference out. No recomputation needed.
    }
}
