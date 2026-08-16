package net.dutymod.memory.enums;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Decides whether the array produced by one specific {@code values()} call can safely be replaced
 * by a shared, cached array.
 *
 * <p>{@code Enum.values()} hands out a fresh clone every time precisely because the array is
 * mutable and callers could scribble on it. Returning a shared array is only correct when the
 * caller demonstrably cannot mutate it, and cannot hand it to anyone else who might. This
 * interpreter answers that by tracking the array through the method's dataflow and recording a
 * violation as soon as it does anything we cannot vouch for:
 *
 * <ul>
 *   <li>being stored into a field ({@code PUTFIELD}/{@code PUTSTATIC}) -- it outlives the method
 *   <li>being written to ({@code AASTORE} and friends) -- direct mutation
 *   <li>being returned -- the caller can do anything with it
 *   <li>being passed to another method -- we would have to analyse that method too
 * </ul>
 *
 * <p>Reads ({@code AALOAD}, {@code ARRAYLENGTH}) and iteration are fine, and those are what the
 * overwhelming majority of {@code values()} call sites do.
 *
 * <p>The analysis is deliberately pessimistic: anything it cannot prove safe is left untouched. A
 * missed optimization costs an array copy, whereas a wrong one is a silent, extremely hard to
 * diagnose bug in someone else's mod.
 */
final class EscapeInterpreter extends Interpreter<TrackedValue> {
    /**
     * Methods known not to retain or mutate the array they are handed.
     *
     * <p>Passing the array to a method is normally an immediate violation, because proving safety
     * would mean analysing the callee. These few are worth special-casing by hand: they are
     * extremely common at {@code values()} call sites and their contracts are fixed by the JDK.
     */
    private static final Set<MethodRef> SAFE_CONSUMERS = Set.of(
            new MethodRef("java/util/Arrays", "stream", "([Ljava/lang/Object;)Ljava/util/stream/Stream;"),
            new MethodRef("java/util/Arrays", "stream", "([Ljava/lang/Object;II)Ljava/util/stream/Stream;")
    );

    private final SourceInterpreter delegate = new SourceInterpreter();
    private final AbstractInsnNode valuesInsn;
    private boolean violation;

    EscapeInterpreter(AbstractInsnNode valuesInsn) {
        super(Opcodes.ASM9);
        this.valuesInsn = valuesInsn;
    }

    boolean hasViolation() {
        return violation;
    }

    private void violateIf(boolean condition) {
        if (condition) {
            violation = true;
        }
    }

    @Override
    public TrackedValue newValue(Type type) {
        return TrackedValue.of(delegate.newValue(type));
    }

    @Override
    public TrackedValue newOperation(AbstractInsnNode insn) throws org.objectweb.asm.tree.analysis.AnalyzerException {
        return TrackedValue.of(delegate.newOperation(insn));
    }

    @Override
    public TrackedValue copyOperation(AbstractInsnNode insn, TrackedValue value)
            throws org.objectweb.asm.tree.analysis.AnalyzerException {
        return TrackedValue.of(delegate.copyOperation(insn, value.sourceValue)).trackIf(value.tracking);
    }

    @Override
    public TrackedValue unaryOperation(AbstractInsnNode insn, TrackedValue value)
            throws org.objectweb.asm.tree.analysis.AnalyzerException {
        int opcode = insn.getOpcode();
        // Stored in a static field: the array escapes the method entirely.
        violateIf(value.tracking && opcode == Opcodes.PUTSTATIC);
        // A CHECKCAST does not change the identity of the array, so keep tracking through it.
        return TrackedValue.of(delegate.unaryOperation(insn, value.sourceValue))
                .trackIf(value.tracking && opcode == Opcodes.CHECKCAST);
    }

    @Override
    public TrackedValue binaryOperation(AbstractInsnNode insn, TrackedValue value1, TrackedValue value2)
            throws org.objectweb.asm.tree.analysis.AnalyzerException {
        // value2 is the value being stored; AALOAD (a read) is deliberately not a violation.
        violateIf(value2.tracking && insn.getOpcode() == Opcodes.PUTFIELD);
        return TrackedValue.of(delegate.binaryOperation(insn, value1.sourceValue, value2.sourceValue));
    }

    @Override
    public TrackedValue ternaryOperation(
            AbstractInsnNode insn, TrackedValue value1, TrackedValue value2, TrackedValue value3)
            throws org.objectweb.asm.tree.analysis.AnalyzerException {
        // Array stores land here; value1 is the array being written to.
        violateIf(value1.tracking);
        return TrackedValue.of(
                delegate.ternaryOperation(insn, value1.sourceValue, value2.sourceValue, value3.sourceValue));
    }

    @Override
    public TrackedValue naryOperation(AbstractInsnNode insn, List<? extends TrackedValue> values)
            throws org.objectweb.asm.tree.analysis.AnalyzerException {
        boolean isValuesCall = insn == valuesInsn;

        if (!isValuesCall) {
            boolean safeConsumer = insn instanceof MethodInsnNode call && SAFE_CONSUMERS.contains(MethodRef.of(call));
            if (!safeConsumer) {
                for (TrackedValue value : values) {
                    if (value.tracking) {
                        // The array was handed to a method we have not vetted.
                        violateIf(true);
                        break;
                    }
                }
            }
        }

        List<SourceValue> delegated = new ArrayList<>(values.size());
        for (TrackedValue value : values) {
            delegated.add(value.sourceValue);
        }
        // The result of the call we are considering is the array we want to follow.
        return TrackedValue.of(delegate.naryOperation(insn, delegated)).trackIf(isValuesCall);
    }

    @Override
    public void returnOperation(AbstractInsnNode insn, TrackedValue value, TrackedValue expected)
            throws org.objectweb.asm.tree.analysis.AnalyzerException {
        violateIf(value.tracking);
        delegate.returnOperation(insn, value.sourceValue, expected.sourceValue);
    }

    @Override
    public TrackedValue merge(TrackedValue value1, TrackedValue value2) {
        SourceValue merged = delegate.merge(value1.sourceValue, value2.sourceValue);
        if (merged == value1.sourceValue) {
            return value1.trackIf(value2.tracking);
        }
        if (merged == value2.sourceValue) {
            return value2.trackIf(value1.tracking);
        }
        return TrackedValue.of(merged).trackIf(value1.tracking || value2.tracking);
    }
}
