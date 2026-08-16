package net.dutymod.memory.enums;

import org.objectweb.asm.Opcodes;

/** Bytecode names and descriptors used across the enum-values transformer. */
final class AsmNames {
    static final String CLINIT = "<clinit>";
    static final String VALUES = "values";

    static final String OBJECT = "java/lang/Object";
    static final String ILLEGAL_ACCESS_ERROR = "java/lang/IllegalAccessError";

    static final String DESC_OBJECT_ARRAY = "[Ljava/lang/Object;";
    static final String DESC_RETURNS_OBJECT_ARRAY = "()[Ljava/lang/Object;";
    static final String DESC_BOOLEAN = "Z";

    /**
     * Package the generated per-enum cache holders live in.
     *
     * <p>Declared to FML through {@code ClassProcessor#generatesPackages()}, which is what lets us
     * materialize these classes on demand instead of reaching into a classloader with reflection.
     */
    static final String GENERATED_PACKAGE = "net.dutymod.memory.generated";
    static final String GENERATED_PREFIX = "net/dutymod/memory/generated/";

    static final int ACC_PUBLIC_FINAL_SYNTHETIC =
            Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC;
    static final int ACC_PUBLIC_STATIC_FINAL_SYNTHETIC =
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC;
    static final int ACC_PUBLIC_STATIC_SYNTHETIC =
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;

    private AsmNames() {}

    /** {@return the internal name of the cache holder generated for {@code enumInternalName}} */
    static String cacheClassFor(String enumInternalName) {
        return GENERATED_PREFIX + enumInternalName;
    }

    /** {@return whether {@code internalName} is one of our generated cache holders} */
    static boolean isCacheClass(String internalName) {
        return internalName.startsWith(GENERATED_PREFIX);
    }

    /** Inverse of {@link #cacheClassFor}. */
    static String enumClassFor(String cacheInternalName) {
        return cacheInternalName.substring(GENERATED_PREFIX.length());
    }
}
