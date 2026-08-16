package net.dutymod.memory.enums;

import org.objectweb.asm.tree.MethodInsnNode;

/** An owner/name/descriptor triple, so method invocations can be looked up in a {@code Set}. */
record MethodRef(String owner, String name, String descriptor) {
    static MethodRef of(MethodInsnNode insn) {
        return new MethodRef(insn.owner, insn.name, insn.desc);
    }
}
