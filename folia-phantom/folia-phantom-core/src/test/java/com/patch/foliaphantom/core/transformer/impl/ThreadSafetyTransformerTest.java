package com.patch.foliaphantom.core.transformer.impl;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ThreadSafetyTransformerTest {

    @Test
    void redirectsEntityTeleportCallToFoliaPatcher() {
        CapturingMethodVisitor capturingMethodVisitor = new CapturingMethodVisitor();
        MethodVisitor mv = createVisitor(capturingMethodVisitor);

        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                "org/bukkit/entity/Entity",
                "teleport",
                "(Lorg/bukkit/Location;)Z",
                false);

        assertEquals(Opcodes.INVOKESTATIC, capturingMethodVisitor.lastOpcode);
        assertEquals("com/patch/foliaphantom/core/patcher/FoliaPatcher", capturingMethodVisitor.lastOwner);
        assertEquals("safeTeleport", capturingMethodVisitor.lastName);
        assertEquals("(Lorg/bukkit/entity/Entity;Lorg/bukkit/Location;)Z", capturingMethodVisitor.lastDesc);
        assertFalse(capturingMethodVisitor.lastIsInterface);
    }

    @Test
    void redirectsEntityTeleportWithCauseCallToFoliaPatcher() {
        CapturingMethodVisitor capturingMethodVisitor = new CapturingMethodVisitor();
        MethodVisitor mv = createVisitor(capturingMethodVisitor);

        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                "org/bukkit/entity/LivingEntity",
                "teleport",
                "(Lorg/bukkit/Location;Lorg/bukkit/event/player/PlayerTeleportEvent$TeleportCause;)Z",
                true);

        assertEquals(Opcodes.INVOKESTATIC, capturingMethodVisitor.lastOpcode);
        assertEquals("com/patch/foliaphantom/core/patcher/FoliaPatcher", capturingMethodVisitor.lastOwner);
        assertEquals("safeTeleportWithCause", capturingMethodVisitor.lastName);
        assertEquals(
                "(Lorg/bukkit/entity/Entity;Lorg/bukkit/Location;Lorg/bukkit/event/player/PlayerTeleportEvent$TeleportCause;)Z",
                capturingMethodVisitor.lastDesc);
        assertFalse(capturingMethodVisitor.lastIsInterface);
    }

    private MethodVisitor createVisitor(CapturingMethodVisitor capturingMethodVisitor) {
        ThreadSafetyTransformer transformer = new ThreadSafetyTransformer(Logger.getLogger("test"));
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
