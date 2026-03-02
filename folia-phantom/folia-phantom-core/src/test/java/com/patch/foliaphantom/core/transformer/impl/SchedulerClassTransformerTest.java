package com.patch.foliaphantom.core.transformer.impl;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SchedulerClassTransformerTest {

    @Test
    void redirectsBukkitSchedulerInterfaceCallToFoliaPatcher() {
        CapturingMethodVisitor capturingMethodVisitor = new CapturingMethodVisitor();
        MethodVisitor mv = createVisitor(capturingMethodVisitor);

        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                "org/bukkit/scheduler/BukkitScheduler",
                "runTaskAsynchronously",
                "(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;)Lorg/bukkit/scheduler/BukkitTask;",
                true);

        assertEquals(Opcodes.INVOKESTATIC, capturingMethodVisitor.lastOpcode);
        assertEquals("com/patch/foliaphantom/core/patcher/FoliaPatcher", capturingMethodVisitor.lastOwner);
        assertEquals("runTaskAsynchronously", capturingMethodVisitor.lastName);
        assertEquals(
                "(Lorg/bukkit/scheduler/BukkitScheduler;Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;)Lorg/bukkit/scheduler/BukkitTask;",
                capturingMethodVisitor.lastDesc);
        assertFalse(capturingMethodVisitor.lastIsInterface);
    }

    @Test
    void redirectsCraftSchedulerVirtualCallToFoliaPatcher() {
        CapturingMethodVisitor capturingMethodVisitor = new CapturingMethodVisitor();
        MethodVisitor mv = createVisitor(capturingMethodVisitor);

        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                "org/bukkit/craftbukkit/scheduler/CraftScheduler",
                "runTaskAsynchronously",
                "(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;)Lorg/bukkit/scheduler/BukkitTask;",
                false);

        assertEquals(Opcodes.INVOKESTATIC, capturingMethodVisitor.lastOpcode);
        assertEquals("com/patch/foliaphantom/core/patcher/FoliaPatcher", capturingMethodVisitor.lastOwner);
        assertEquals("runTaskAsynchronously", capturingMethodVisitor.lastName);
        assertEquals(
                "(Lorg/bukkit/scheduler/BukkitScheduler;Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;)Lorg/bukkit/scheduler/BukkitTask;",
                capturingMethodVisitor.lastDesc);
        assertFalse(capturingMethodVisitor.lastIsInterface);
    }

    private MethodVisitor createVisitor(CapturingMethodVisitor capturingMethodVisitor) {
        SchedulerClassTransformer transformer = new SchedulerClassTransformer(Logger.getLogger("test"));
        ClassVisitor cv = transformer.createVisitor(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                return capturingMethodVisitor;
            }
        });

        return cv.visitMethod(Opcodes.ACC_PUBLIC, "demo", "()V", null, null);
    }

    private static final class CapturingMethodVisitor extends MethodVisitor {
        private int lastOpcode;
        private String lastOwner;
        private String lastName;
        private String lastDesc;
        private boolean lastIsInterface;

        private CapturingMethodVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            this.lastOpcode = opcode;
            this.lastOwner = owner;
            this.lastName = name;
            this.lastDesc = descriptor;
            this.lastIsInterface = isInterface;
        }
    }
}
