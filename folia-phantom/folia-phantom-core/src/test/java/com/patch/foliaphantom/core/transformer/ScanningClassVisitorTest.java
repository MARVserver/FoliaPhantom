package com.patch.foliaphantom.core.transformer;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

class ScanningClassVisitorTest {

    // -------------------------------------------------------------------------
    // Original method-call based tests (kept for regression)
    // -------------------------------------------------------------------------

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
        assertTrue(scanner.getPatchReasons().contains(ScanningClassVisitor.PatchReason.SCHEDULER_METHOD_CALL));
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
        assertTrue(scanner.getPatchReasons().contains(ScanningClassVisitor.PatchReason.SCOREBOARD_TEAM));
    }

    // -------------------------------------------------------------------------
    // New: class hierarchy detection
    // -------------------------------------------------------------------------

    @Test
    void detectsExtendsBukkitRunnable() {
        ScanningClassVisitor scanner = new ScanningClassVisitor();
        // Simulate a class that extends BukkitRunnable
        scanner.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/MyTask", null,
                "org/bukkit/scheduler/BukkitRunnable", null);

        assertTrue(scanner.needsPatching());
        assertTrue(scanner.getPatchReasons().contains(ScanningClassVisitor.PatchReason.EXTENDS_BUKKIT_RUNNABLE));
    }

    @Test
    void doesNotTriggerForUnrelatedSuperclass() {
        ScanningClassVisitor scanner = new ScanningClassVisitor();
        scanner.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null,
                "java/lang/Object", null);

        assertTrue(scanner.needsPatching());
        assertTrue(scanner.getPatchReasons().isEmpty());
    }

    @Test
    void detectsImplementsBukkitSchedulerInterface() {
        ScanningClassVisitor scanner = new ScanningClassVisitor();
        scanner.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/MyScheduler", null,
                "java/lang/Object",
                new String[] { "org/bukkit/scheduler/BukkitScheduler" });

        assertTrue(scanner.needsPatching());
        assertTrue(scanner.getPatchReasons().contains(ScanningClassVisitor.PatchReason.IMPLEMENTS_BUKKIT_INTERFACE));
    }

    // -------------------------------------------------------------------------
    // New: field type detection
    // -------------------------------------------------------------------------

    @Test
    void detectsBukkitTaskFieldDescriptor() {
        ScanningClassVisitor scanner = new ScanningClassVisitor();
        scanner.visitField(Opcodes.ACC_PRIVATE, "myTask",
                "Lorg/bukkit/scheduler/BukkitTask;", null, null);

        assertTrue(scanner.needsPatching());
        assertTrue(scanner.getPatchReasons().contains(ScanningClassVisitor.PatchReason.FIELD_TYPE_BUKKIT));
    }

    @Test
    void detectsBukkitSchedulerFieldDescriptor() {
        ScanningClassVisitor scanner = new ScanningClassVisitor();
        scanner.visitField(Opcodes.ACC_PRIVATE, "scheduler",
                "Lorg/bukkit/scheduler/BukkitScheduler;", null, null);

        assertTrue(scanner.needsPatching());
        assertTrue(scanner.getPatchReasons().contains(ScanningClassVisitor.PatchReason.FIELD_TYPE_BUKKIT));
    }

    @Test
    void doesNotTriggerForUnrelatedFieldDescriptor() {
        ScanningClassVisitor scanner = new ScanningClassVisitor();
        scanner.visitField(Opcodes.ACC_PRIVATE, "name", "Ljava/lang/String;", null, null);

        assertTrue(scanner.needsPatching());
        assertTrue(scanner.getPatchReasons().isEmpty());
    }

    // -------------------------------------------------------------------------
    // New: WorldCreator constructor (INVOKESPECIAL <init>)
    // -------------------------------------------------------------------------

    @Test
    void detectsWorldCreatorConstructor() {
        ScanningClassVisitor scanner = new ScanningClassVisitor();
        MethodVisitor mv = scanner.visitMethod(Opcodes.ACC_PUBLIC, "createMyWorld", "()V", null, null);

        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "org/bukkit/WorldCreator",
                "<init>",
                "(Ljava/lang/String;)V",
                false);

        assertTrue(scanner.needsPatching());
        assertTrue(scanner.getPatchReasons().contains(ScanningClassVisitor.PatchReason.WORLD_CREATOR_CONSTRUCTOR));
    }

    // -------------------------------------------------------------------------
    // New: Block.setType detection
    // -------------------------------------------------------------------------

    @Test
    void detectsBlockSetType() {
        ScanningClassVisitor scanner = new ScanningClassVisitor();
        MethodVisitor mv = scanner.visitMethod(Opcodes.ACC_PUBLIC, "build", "()V", null, null);

        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                "org/bukkit/block/Block",
                "setType",
                "(Lorg/bukkit/Material;)V",
                true);

        assertTrue(scanner.needsPatching());
        assertTrue(scanner.getPatchReasons().contains(ScanningClassVisitor.PatchReason.BLOCK_SET_TYPE));
    }

    // -------------------------------------------------------------------------
    // New: multiple reasons accumulate
    // -------------------------------------------------------------------------

    @Test
    void multipleReasonsAccumulate() {
        ScanningClassVisitor scanner = new ScanningClassVisitor();
        // Hierarchy trigger
        scanner.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/ComboClass", null,
                "org/bukkit/scheduler/BukkitRunnable", null);
        // Field trigger
        scanner.visitField(Opcodes.ACC_PRIVATE, "task",
                "Lorg/bukkit/scheduler/BukkitTask;", null, null);
        // Method trigger
        MethodVisitor mv = scanner.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                "org/bukkit/scoreboard/Scoreboard",
                "registerNewTeam",
                "(Ljava/lang/String;)Lorg/bukkit/scoreboard/Team;",
                true);

        assertTrue(scanner.needsPatching());
        assertTrue(scanner.getPatchReasons().contains(ScanningClassVisitor.PatchReason.EXTENDS_BUKKIT_RUNNABLE));
        assertTrue(scanner.getPatchReasons().contains(ScanningClassVisitor.PatchReason.FIELD_TYPE_BUKKIT));
        assertTrue(scanner.getPatchReasons().contains(ScanningClassVisitor.PatchReason.SCOREBOARD_TEAM));
    }
}
