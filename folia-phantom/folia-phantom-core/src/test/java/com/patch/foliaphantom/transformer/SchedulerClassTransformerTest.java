package com.patch.foliaphantom.transformer;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SchedulerClassTransformerTest implements Opcodes {

    private static final String BUKKIT_RUNNABLE = "org/bukkit/scheduler/BukkitRunnable";
    private static final String BUKKIT_TASK = "org/bukkit/scheduler/BukkitTask";
    private static final String PATCHER = "com/patch/foliaphantom/patcher/FoliaPatcher";
    private static final String TEST_CLASS = "example/EcoRunnableTaskLike";
    private static final String TASK_FIELD = "__pastaBukkitTask";

    private static final String[][] RUNNABLE_METHODS = {
        {"runTask", "(Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/scheduler/BukkitTask;"},
        {"runTaskLater", "(Lorg/bukkit/plugin/Plugin;J)Lorg/bukkit/scheduler/BukkitTask;"},
        {"runTaskTimer", "(Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;"},
        {"runTaskAsynchronously", "(Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/scheduler/BukkitTask;"},
        {"runTaskLaterAsynchronously", "(Lorg/bukkit/plugin/Plugin;J)Lorg/bukkit/scheduler/BukkitTask;"},
        {"runTaskTimerAsynchronously", "(Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;"}
    };

    @Test
    public void rewritesAllBukkitRunnableSuperSchedulingCalls() {
        for (String[] methodSpec : RUNNABLE_METHODS) {
            String methodName = methodSpec[0];
            String descriptor = methodSpec[1];

            ClassNode transformed = transform(classWithSchedulingCall(
                    INVOKESPECIAL, BUKKIT_RUNNABLE, methodName, descriptor));
            MethodNode callMethod = findMethod(transformed, "call", "()Lorg/bukkit/scheduler/BukkitTask;");
            MethodInsnNode rewrittenCall = findMethodCall(callMethod, methodName + "_onRunnable");

            assertNotNull("Expected rewritten call for " + methodName, rewrittenCall);
            assertEquals(INVOKESTATIC, rewrittenCall.getOpcode());
            assertEquals(PATCHER, rewrittenCall.owner);
            assertEquals(
                    "(Ljava/lang/Runnable;" + descriptor.substring(1),
                    rewrittenCall.desc);
            assertFalse(rewrittenCall.itf);

            assertTrue("Expected transformed task to be stored for " + methodName,
                    containsTaskStore(callMethod, transformed.name));
            assertTrue(hasField(transformed, TASK_FIELD, "L" + BUKKIT_TASK + ";"));
            assertNotNull(findMethod(transformed, "cancel", "()V"));
            assertNotNull(findMethod(transformed, "isCancelled", "()Z"));
            assertNotNull(findMethod(transformed, "getTaskId", "()I"));
        }
    }

    @Test
    public void doesNotRewriteUnrelatedInvokeSpecialWithSameSignature() {
        String descriptor = "(Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;";
        ClassNode transformed = transform(classWithSchedulingCall(
                INVOKESPECIAL, "example/Unrelated", "runTaskTimer", descriptor));
        MethodNode callMethod = findMethod(transformed, "call", "()Lorg/bukkit/scheduler/BukkitTask;");
        MethodInsnNode call = findOnlyMethodCall(callMethod);

        assertEquals(INVOKESPECIAL, call.getOpcode());
        assertEquals("example/Unrelated", call.owner);
        assertEquals("runTaskTimer", call.name);
        assertEquals(descriptor, call.desc);
        assertFalse(hasField(transformed, TASK_FIELD, "L" + BUKKIT_TASK + ";"));
    }

    @Test
    public void keepsSubclassInvokeVirtualSupportWithoutInjectingStateIntoCaller() {
        String descriptor = "(Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;";
        ClassNode transformed = transform(classWithSchedulingCall(
                INVOKEVIRTUAL,
                "com/willfp/eco/internal/scheduling/EcoRunnableTask",
                "runTaskTimer",
                descriptor));
        MethodNode callMethod = findMethod(transformed, "call", "()Lorg/bukkit/scheduler/BukkitTask;");
        MethodInsnNode call = findMethodCall(callMethod, "runTaskTimer_onRunnable");

        assertNotNull(call);
        assertEquals(INVOKESTATIC, call.getOpcode());
        assertEquals(PATCHER, call.owner);
        assertFalse(hasField(transformed, TASK_FIELD, "L" + BUKKIT_TASK + ";"));
    }

    private static ClassNode transform(ClassNode input) {
        SchedulerClassTransformer transformer = new SchedulerClassTransformer();
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        byte[] bytes = transformer.transform(input, input.name, writer);
        ClassNode output = new ClassNode(ASM9);
        new ClassReader(bytes).accept(output, 0);
        return output;
    }

    private static ClassNode classWithSchedulingCall(
            int opcode, String owner, String methodName, String descriptor) {
        ClassNode classNode = new ClassNode(ASM9);
        classNode.version = V17;
        classNode.access = ACC_PUBLIC | ACC_ABSTRACT;
        classNode.name = TEST_CLASS;
        classNode.superName = BUKKIT_RUNNABLE;

        MethodNode method = new MethodNode(
                ASM9,
                ACC_PUBLIC,
                "call",
                "()Lorg/bukkit/scheduler/BukkitTask;",
                null,
                null);
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        for (Type argumentType : Type.getArgumentTypes(descriptor)) {
            switch (argumentType.getSort()) {
                case Type.LONG -> method.instructions.add(new InsnNode(LCONST_0));
                case Type.FLOAT -> method.instructions.add(new InsnNode(FCONST_0));
                case Type.DOUBLE -> method.instructions.add(new InsnNode(DCONST_0));
                case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT ->
                        method.instructions.add(new InsnNode(ICONST_0));
                default -> method.instructions.add(new InsnNode(ACONST_NULL));
            }
        }
        method.instructions.add(new MethodInsnNode(
                opcode, owner, methodName, descriptor, false));
        method.instructions.add(new InsnNode(ARETURN));
        classNode.methods.add(method);
        return classNode;
    }

    private static MethodNode findMethod(ClassNode classNode, String name, String descriptor) {
        for (MethodNode method : classNode.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    private static MethodInsnNode findMethodCall(MethodNode method, String name) {
        if (method == null) {
            return null;
        }
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode methodInsn && name.equals(methodInsn.name)) {
                return methodInsn;
            }
        }
        return null;
    }

    private static MethodInsnNode findOnlyMethodCall(MethodNode method) {
        MethodInsnNode result = null;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode methodInsn) {
                result = methodInsn;
            }
        }
        return result;
    }

    private static boolean containsTaskStore(MethodNode method, String owner) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode fieldInsn
                    && fieldInsn.getOpcode() == PUTFIELD
                    && owner.equals(fieldInsn.owner)
                    && TASK_FIELD.equals(fieldInsn.name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasField(ClassNode classNode, String name, String descriptor) {
        for (FieldNode field : classNode.fields) {
            if (name.equals(field.name) && descriptor.equals(field.desc)) {
                return true;
            }
        }
        return false;
    }
}
