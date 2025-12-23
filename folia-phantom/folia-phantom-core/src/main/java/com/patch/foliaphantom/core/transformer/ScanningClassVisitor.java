/*
 * Folia Phantom - Scanning Class Visitor
 * 
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * A lightweight ClassVisitor that performs a fast scan of method calls.
 * 
 * <p>
 * Its purpose is to determine if a class needs patching without performing
 * full bytecode transformation. This significantly improves performance for
 * plugins with many classes that don't use Bukkit scheduler or threading APIs.
 * </p>
 */
public class ScanningClassVisitor extends ClassVisitor {
    private boolean needsPatching = false;

    public ScanningClassVisitor() {
        super(Opcodes.ASM9);
    }

    /**
     * @return true if the class contains calls that require transformation
     */
    public boolean needsPatching() {
        return needsPatching;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        return new ScanningMethodVisitor();
    }

    private class ScanningMethodVisitor extends MethodVisitor {
        public ScanningMethodVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (needsPatching)
                return;

            // Define targets that trigger the 'needsPatching' flag
            if ("org/bukkit/scheduler/BukkitScheduler".equals(owner) ||
                    "org/bukkit/scheduler/BukkitRunnable".equals(owner) ||
                    "org/bukkit/WorldCreator".equals(owner) ||
                    ("org/bukkit/block/Block".equals(owner) && name.equals("setType")) ||
                    ("org/bukkit/Bukkit".equals(owner) && name.equals("createWorld")) ||
                    ("org/bukkit/plugin/Plugin".equals(owner) && name.equals("getDefaultWorldGenerator"))) {
                needsPatching = true;
                return;
            }

            // Check for BukkitRunnable instance method calls
            if (opcode == Opcodes.INVOKEVIRTUAL && isBukkitRunnableInstanceMethod(name, desc)) {
                needsPatching = true;
                return;
            }

            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
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
