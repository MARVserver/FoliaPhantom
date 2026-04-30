package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.ClassVisitor;
import com.patch.foliaphantom.core.transformer.ScanningClassVisitor;
import java.util.Set;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.logging.Logger;

/**
 * Redirects scoreboard team registration to a Folia-safe bridge.
 */
public class ScoreboardClassTransformer implements ClassTransformer {
    private final Logger logger;

    public ScoreboardClassTransformer(Logger logger) {
        this.logger = logger;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new ScoreboardClassVisitor(next);
    }

    private static class ScoreboardClassVisitor extends ClassVisitor {
        public ScoreboardClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            return new ScoreboardMethodVisitor(super.visitMethod(access, name, desc, sig, ex));
        }
    }

    private static class ScoreboardMethodVisitor extends MethodVisitor {
        private static final String PATCHER = "com/patch/foliaphantom/core/patcher/FoliaPatcher";

        public ScoreboardMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if ((opcode == Opcodes.INVOKEINTERFACE || opcode == Opcodes.INVOKEVIRTUAL)
                    && "org/bukkit/scoreboard/Scoreboard".equals(owner)
                    && "registerNewTeam".equals(name)
                    && "(Ljava/lang/String;)Lorg/bukkit/scoreboard/Team;".equals(desc)) {
                super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        PATCHER,
                        "registerNewTeamSafely",
                        "(Lorg/bukkit/scoreboard/Scoreboard;Ljava/lang/String;)Lorg/bukkit/scoreboard/Team;",
                        false);
                return;
            }

            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }

    @Override
    public boolean supports(Set<ScanningClassVisitor.PatchReason> reasons) {
        return reasons.contains(ScanningClassVisitor.PatchReason.SCOREBOARD_TEAM);
    }
}
