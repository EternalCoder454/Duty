package net.dutymod.memory.enums;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

/**
 * Builds the per-enum holder class that rewritten call sites read from.
 *
 * <p>One holder is generated per enum, the equivalent of:
 *
 * <pre>{@code
 * public final class Holder {
 *     public static final Object[] VALUES  = EnumValuesAccessor.values("com.example.Foo");
 *     public static final boolean IS_ENUM  = EnumValuesAccessor.isEnum("com.example.Foo");
 *
 *     public static Object[] values() {
 *         return IS_ENUM ? VALUES : invokeOriginalValues();
 *     }
 *
 *     public static Object[] invokeOriginalValues() {
 *         try {
 *             return Foo.values();
 *         } catch (IllegalAccessError e) {
 *             return EnumValuesAccessor.invokeValuesSlow("com.example.Foo");
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>The {@code IS_ENUM} branch is what makes the loose call-site matching safe. A non-enum class
 * can coincidentally have a {@code static Foo[] values()} method; when that happens the holder just
 * calls the original and nothing is optimized. Because both fields are {@code static final}, the JIT
 * folds the branch away and the fallback costs nothing in the normal case.
 *
 * <p>Everything is typed as {@code Object[]} deliberately: the enum may be private or
 * package-private, and naming it in a field descriptor would make the holder unverifiable. The
 * {@code CHECKCAST} the transformer inserts at the call site restores the concrete type, and the JIT
 * removes it.
 */
final class CacheClassGenerator {
    private CacheClassGenerator() {}

    /**
     * Populates {@code node} -- handed to us empty by FML -- with the holder for the enum that
     * {@code cacheInternalName} was derived from.
     */
    static void generateInto(ClassNode node, String cacheInternalName) {
        String enumInternalName = AsmNames.enumClassFor(cacheInternalName);
        String enumBinaryName = enumInternalName.replace('/', '.');

        node.version = Opcodes.V21;
        node.access = AsmNames.ACC_PUBLIC_FINAL_SYNTHETIC;
        node.name = cacheInternalName;
        node.superName = AsmNames.OBJECT;
        node.interfaces.clear();
        node.fields.clear();
        node.methods.clear();

        node.fields.add(new FieldNode(
                AsmNames.ACC_PUBLIC_STATIC_FINAL_SYNTHETIC, "VALUES", AsmNames.DESC_OBJECT_ARRAY, null, null));
        node.fields.add(new FieldNode(
                AsmNames.ACC_PUBLIC_STATIC_FINAL_SYNTHETIC, "IS_ENUM", AsmNames.DESC_BOOLEAN, null, null));

        node.methods.add(staticInitializer(cacheInternalName, enumBinaryName));
        node.methods.add(valuesMethod(cacheInternalName));
        node.methods.add(invokeOriginalValuesMethod(enumInternalName, enumBinaryName));
    }

    private static MethodNode staticInitializer(String cacheClass, String enumBinaryName) {
        MethodNode clinit = new MethodNode(Opcodes.ACC_STATIC, AsmNames.CLINIT, "()V", null, null);

        clinit.instructions.add(new LdcInsnNode(enumBinaryName));
        clinit.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, EnumValuesAccessor.INTERNAL_NAME,
                "values", "(Ljava/lang/String;)[Ljava/lang/Object;", false));
        clinit.instructions.add(new FieldInsnNode(
                Opcodes.PUTSTATIC, cacheClass, "VALUES", AsmNames.DESC_OBJECT_ARRAY));

        clinit.instructions.add(new LdcInsnNode(enumBinaryName));
        clinit.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, EnumValuesAccessor.INTERNAL_NAME,
                "isEnum", "(Ljava/lang/String;)Z", false));
        clinit.instructions.add(new FieldInsnNode(
                Opcodes.PUTSTATIC, cacheClass, "IS_ENUM", AsmNames.DESC_BOOLEAN));

        clinit.instructions.add(new InsnNode(Opcodes.RETURN));
        clinit.maxStack = 1;
        clinit.maxLocals = 0;
        return clinit;
    }

    private static MethodNode valuesMethod(String cacheClass) {
        MethodNode values = new MethodNode(AsmNames.ACC_PUBLIC_STATIC_SYNTHETIC,
                AsmNames.VALUES, AsmNames.DESC_RETURNS_OBJECT_ARRAY, null, null);

        LabelNode notAnEnum = new LabelNode();
        LabelNode end = new LabelNode();

        values.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, cacheClass, "IS_ENUM", AsmNames.DESC_BOOLEAN));
        values.instructions.add(new JumpInsnNode(Opcodes.IFEQ, notAnEnum));
        values.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, cacheClass, "VALUES", AsmNames.DESC_OBJECT_ARRAY));
        values.instructions.add(new JumpInsnNode(Opcodes.GOTO, end));
        values.instructions.add(notAnEnum);
        values.instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        values.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, cacheClass,
                "invokeOriginalValues", AsmNames.DESC_RETURNS_OBJECT_ARRAY, false));
        values.instructions.add(end);
        values.instructions.add(new FrameNode(
                Opcodes.F_SAME1, 0, null, 1, new Object[]{AsmNames.DESC_OBJECT_ARRAY}));
        values.instructions.add(new InsnNode(Opcodes.ARETURN));

        values.maxStack = 1;
        values.maxLocals = 0;
        return values;
    }

    private static MethodNode invokeOriginalValuesMethod(String enumInternalName, String enumBinaryName) {
        MethodNode method = new MethodNode(AsmNames.ACC_PUBLIC_STATIC_SYNTHETIC,
                "invokeOriginalValues", AsmNames.DESC_RETURNS_OBJECT_ARRAY, null, null);

        LabelNode tryStart = new LabelNode();
        LabelNode tryEnd = new LabelNode();
        LabelNode handler = new LabelNode();
        LabelNode end = new LabelNode();

        method.instructions.add(tryStart);
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, enumInternalName,
                AsmNames.VALUES, "()[L" + enumInternalName + ";", false));
        method.instructions.add(tryEnd);
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, end));

        // The direct call above is unverifiable if the class turns out to be inaccessible from
        // here. That surfaces as IllegalAccessError at link time, so fall back to reflection.
        method.instructions.add(handler);
        method.instructions.add(new FrameNode(
                Opcodes.F_SAME1, 0, null, 1, new Object[]{AsmNames.ILLEGAL_ACCESS_ERROR}));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new LdcInsnNode(enumBinaryName));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, EnumValuesAccessor.INTERNAL_NAME,
                "invokeValuesSlow", "(Ljava/lang/String;)[Ljava/lang/Object;", false));

        method.instructions.add(end);
        method.instructions.add(new FrameNode(
                Opcodes.F_SAME1, 0, null, 1, new Object[]{AsmNames.DESC_OBJECT_ARRAY}));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));

        method.tryCatchBlocks.add(
                new TryCatchBlockNode(tryStart, tryEnd, handler, AsmNames.ILLEGAL_ACCESS_ERROR));
        method.maxStack = 1;
        method.maxLocals = 0;
        return method;
    }
}
