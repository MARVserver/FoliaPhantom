package com.patch.foliaphantom.core.transformer;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanningClassVisitorTest {
    @Test
    void marksCraftSchedulerCallsAsNeedingPatching() {
        ScanningClassVisitor scanner = new ScanningClassVisitor();
        MethodVisitor mv = scanner.visitMethod(Opcodes.ACC_PUBLIC, "demo", "()V", null, null);

        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                "org/bukkit/craftbukkit/scheduler/CraftScheduler",
                "runTaskTimer",
                "(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;JJ)Lorg/bukkit/scheduler/BukkitTask;",
                false);

        assertTrue(scanner.needsPatching());
    }

    @Test
    void marksScoreboardRegisterNewTeamCallsAsNeedingPatching() {
        ScanningClassVisitor scanner = new ScanningClassVisitor();
        MethodVisitor mv = scanner.visitMethod(Opcodes.ACC_PUBLIC, "demo", "()V", null, null);

        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                "org/bukkit/scoreboard/Scoreboard",
                "registerNewTeam",
                "(Ljava/lang/String;)Lorg/bukkit/scoreboard/Team;",
                true);

        assertTrue(scanner.needsPatching());
    }
}

