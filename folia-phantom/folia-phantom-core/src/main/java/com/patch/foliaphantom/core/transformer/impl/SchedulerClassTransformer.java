/*
 * Folia Phantom - Scheduler Class Transformer
 * 
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;
import java.util.logging.Logger;

/**
 * Transforms BukkitScheduler and BukkitRunnable method calls.
 * 
 * <p>
 * Redirects standard scheduler calls to FoliaPatcher utility methods
 * that handle Folia's region-based scheduling.
 * </p>
 */
public class SchedulerClassTransformer implements ClassTransformer {
    private final Logger logger;

    public SchedulerClassTransformer(Logger logger) {
        this.logger = logger;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new SchedulerClassVisitor(next);
    }

    private static class SchedulerClassVisitor extends ClassVisitor {
        public SchedulerClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            return new SchedulerMethodVisitor(super.visitMethod(access, name, desc, sig, ex));
        }
    }

    private static class SchedulerMethodVisitor extends MethodVisitor {
        private static final String PATCHER = "com/patch/foliaphantom/core/patcher/FoliaPatcher";

        public SchedulerMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            // Redirect BukkitScheduler interface calls
            if ("org/bukkit/scheduler/BukkitScheduler".equals(owner) && opcode == Opcodes.INVOKEINTERFACE) {
                if (isSchedulerMethod(name, desc)) {
                    String newDesc = "(Lorg/bukkit/scheduler/BukkitScheduler;" + desc.substring(1);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER, name, newDesc, false);
                    return;
                }
            }

            // Redirect BukkitRunnable instance method calls
            if (opcode == Opcodes.INVOKEVIRTUAL && isBukkitRunnableInstanceMethod(name, desc)) {
                String newName = name + "_onRunnable";
                String newDesc = "(Ljava/lang/Runnable;" + desc.substring(1);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER, newName, newDesc, false);
                return;
            }

            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private boolean isSchedulerMethod(String name, String desc) {
            String mk = name + desc;
            return mk.startsWith("runTask") || mk.startsWith("scheduleSync") ||
                    mk.startsWith("scheduleAsync") || mk.startsWith("cancel");
        }

        private boolean isBukkitRunnableInstanceMethod(String name, String desc) {
            String mk = name + desc;
            return mk.equals("runTask(Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/scheduler/BukkitTask;") ||
                    mk.equals("runTaskLater(Lorg/bukkit/plugin/Plugin;J)Lorg/bukkit/scheduler/BukkitTask;") ||
                    mk.equals("runTaskTimer(Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;") ||
                    mk.equals("runTaskAsynchronously(Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/scheduler/BukkitTask;") ||
                    mk.equals(
                            "runTaskLaterAsynchronously(Lorg/bukkit/plugin/Plugin;J)Lorg/bukkit/scheduler/BukkitTask;")
                    ||
                    mk.equals(
                            "runTaskTimerAsynchronously(Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;");
        }
    }
}
