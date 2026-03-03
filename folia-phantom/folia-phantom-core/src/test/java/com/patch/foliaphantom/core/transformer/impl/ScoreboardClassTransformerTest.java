package com.patch.foliaphantom.core.transformer.impl;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ScoreboardClassTransformerTest {
    @Test
    void redirectsRegisterNewTeamCallToFoliaPatcher() {
        CapturingMethodVisitor capturingMethodVisitor = new CapturingMethodVisitor();
        MethodVisitor mv = createVisitor(capturingMethodVisitor);

        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                "org/bukkit/scoreboard/Scoreboard",
                "registerNewTeam",
                "(Ljava/lang/String;)Lorg/bukkit/scoreboard/Team;",
                true);

        assertEquals(Opcodes.INVOKESTATIC, capturingMethodVisitor.lastOpcode);
        assertEquals("com/patch/foliaphantom/core/patcher/FoliaPatcher", capturingMethodVisitor.lastOwner);
        assertEquals("registerNewTeamSafely", capturingMethodVisitor.lastName);
        assertEquals("(Lorg/bukkit/scoreboard/Scoreboard;Ljava/lang/String;)Lorg/bukkit/scoreboard/Team;",
                capturingMethodVisitor.lastDesc);
        assertFalse(capturingMethodVisitor.lastIsInterface);
    }

    private MethodVisitor createVisitor(CapturingMethodVisitor capturingMethodVisitor) {
        ScoreboardClassTransformer transformer = new ScoreboardClassTransformer(Logger.getLogger("test"));
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
