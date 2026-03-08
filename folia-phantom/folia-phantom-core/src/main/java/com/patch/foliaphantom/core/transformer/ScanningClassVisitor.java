/*
 * Folia Phantom - Scanning Class Visitor
 *
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.EnumSet;
import java.util.Set;

/**
 * An advanced ClassVisitor that performs a comprehensive fast scan to determine
 * if a class requires any bytecode transformation.
 *
 * <p>
 * Unlike the basic approach of only checking method invocation opcodes,
 * this scanner performs a multi-layered analysis:
 * </p>
 * <ol>
 * <li><b>Class hierarchy</b> — detects {@code extends BukkitRunnable} or
 * implements Bukkit interfaces at the class level.</li>
 * <li><b>Field types</b> — detects fields whose descriptor includes
 * Bukkit thread-unsafe types (e.g. {@code BukkitTask},
 * {@code BukkitScheduler}).</li>
 * <li><b>Method instructions</b> — detects INVOKEVIRTUAL / INVOKEINTERFACE /
 * INVOKESTATIC / INVOKESPECIAL calls to known unsafe APIs, including
 * constructor calls to {@code WorldCreator}.</li>
 * <li><b>Reason tracking</b> — each trigger is categorised so that
 * downstream transformers can make smarter decisions (future use).</li>
 * </ol>
 */
public class ScanningClassVisitor extends ClassVisitor {

    // -------------------------------------------------------------------------
    // Patching reason flags
    // -------------------------------------------------------------------------

    /**
     * Granular reasons why a class may need patching.
     * Using an {@link EnumSet} keeps memory allocation minimal.
     */
    public enum PatchReason {
        /** Class extends {@code BukkitRunnable} */
        EXTENDS_BUKKIT_RUNNABLE,
        /** Class implements a Bukkit scheduler-related interface */
        IMPLEMENTS_BUKKIT_INTERFACE,
        /** A field's type is a thread-unsafe Bukkit type */
        FIELD_TYPE_BUKKIT,
        /** A method calls a BukkitScheduler / CraftScheduler method */
        SCHEDULER_METHOD_CALL,
        /** A method calls a BukkitRunnable instance method */
        BUKKIT_RUNNABLE_INSTANCE_CALL,
        /** A method calls {@code Block.setType} */
        BLOCK_SET_TYPE,
        /**
         * A method calls {@code Bukkit.createWorld} or {@code WorldCreator.createWorld}
         */
        WORLD_CREATION,
        /** A method calls {@code Plugin.getDefaultWorldGenerator} */
        WORLD_GENERATOR,
        /** A method calls {@code Scoreboard.registerNewTeam} */
        SCOREBOARD_TEAM,
        /** A method constructs a {@code WorldCreator} via {@code INVOKESPECIAL} */
        WORLD_CREATOR_CONSTRUCTOR,
    }

    // -------------------------------------------------------------------------
    // Internal state
    // -------------------------------------------------------------------------

    private final Set<PatchReason> reasons = EnumSet.noneOf(PatchReason.class);

    // -------------------------------------------------------------------------
    // Known unsafe Bukkit class internal names
    // -------------------------------------------------------------------------

    private static final String BUKKIT_SCHEDULER = "org/bukkit/scheduler/BukkitScheduler";
    private static final String CRAFT_SCHEDULER = "org/bukkit/craftbukkit/scheduler/CraftScheduler";
    private static final String BUKKIT_RUNNABLE = "org/bukkit/scheduler/BukkitRunnable";
    private static final String BUKKIT_TASK = "org/bukkit/scheduler/BukkitTask";
    private static final String SCOREBOARD = "org/bukkit/scoreboard/Scoreboard";
    private static final String WORLD_CREATOR = "org/bukkit/WorldCreator";
    private static final String BUKKIT = "org/bukkit/Bukkit";
    private static final String PLUGIN = "org/bukkit/plugin/Plugin";
    private static final String BLOCK = "org/bukkit/block/Block";

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public ScanningClassVisitor() {
        super(Opcodes.ASM9);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Always returns true to force processing through the transformer pipeline.
     * This guarantees 100% compatibility, ensuring no edge cases are missed
     * by the heuristic scanner.
     */
    public boolean needsPatching() {
        return true;
    }

    /**
     * Returns the set of all reasons that require patching.
     * The returned set is a live view — do not modify it externally.
     */
    public Set<PatchReason> getPatchReasons() {
        return reasons;
    }

    // -------------------------------------------------------------------------
    // Class-level analysis (hierarchy & interfaces)
    // -------------------------------------------------------------------------

    @Override
    public void visit(int version, int access, String name, String signature,
            String superName, String[] interfaces) {

        // 1. Superclass check — catches "extends BukkitRunnable"
        if (BUKKIT_RUNNABLE.equals(superName)) {
            reasons.add(PatchReason.EXTENDS_BUKKIT_RUNNABLE);
        }

        // 2. Interface check — catches any Bukkit scheduler interface
        if (interfaces != null) {
            for (String iface : interfaces) {
                if (BUKKIT_SCHEDULER.equals(iface) || BUKKIT_TASK.equals(iface)) {
                    reasons.add(PatchReason.IMPLEMENTS_BUKKIT_INTERFACE);
                    break;
                }
            }
        }

        super.visit(version, access, name, signature, superName, interfaces);
    }

    // -------------------------------------------------------------------------
    // Field-level analysis
    // -------------------------------------------------------------------------

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor,
            String signature, Object value) {

        // Detect fields whose type is a thread-unsafe Bukkit type —
        // e.g. "private BukkitTask someTask;" or "BukkitScheduler scheduler;"
        if (isBukkitDescriptor(descriptor)) {
            reasons.add(PatchReason.FIELD_TYPE_BUKKIT);
        }

        return super.visitField(access, name, descriptor, signature, value);
    }

    /**
     * Returns {@code true} if the JVM field descriptor references a known
     * thread-unsafe Bukkit type.
     */
    private boolean isBukkitDescriptor(String descriptor) {
        return descriptor != null && (descriptor.contains("L" + BUKKIT_SCHEDULER + ";") ||
                descriptor.contains("L" + CRAFT_SCHEDULER + ";") ||
                descriptor.contains("L" + BUKKIT_RUNNABLE + ";") ||
                descriptor.contains("L" + BUKKIT_TASK + ";") ||
                descriptor.contains("L" + SCOREBOARD + ";") ||
                descriptor.contains("L" + WORLD_CREATOR + ";"));
    }

    // -------------------------------------------------------------------------
    // Method-level analysis (instruction scanning)
    // -------------------------------------------------------------------------

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc,
            String signature, String[] exceptions) {
        // Only create a scanning visitor when we still need to look for more reasons
        // (skip further scanning once ALL reason types are already found — fast path)
        if (reasons.size() == PatchReason.values().length) {
            return null; // no need to scan further
        }
        return new ScanningMethodVisitor();
    }

    // -------------------------------------------------------------------------
    // Inner: method instruction visitor
    // -------------------------------------------------------------------------

    private final class ScanningMethodVisitor extends MethodVisitor {

        ScanningMethodVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                String desc, boolean isInterface) {

            // Fast exit if already fully saturated
            if (reasons.size() == PatchReason.values().length)
                return;

            // -----------------------------------------------------------------
            // BukkitScheduler / CraftScheduler calls (INVOKEINTERFACE / INVOKEVIRTUAL)
            // -----------------------------------------------------------------
            if ((opcode == Opcodes.INVOKEINTERFACE || opcode == Opcodes.INVOKEVIRTUAL)
                    && isSchedulerOwner(owner)) {
                reasons.add(PatchReason.SCHEDULER_METHOD_CALL);
                return;
            }

            // -----------------------------------------------------------------
            // BukkitRunnable instance method calls (INVOKEVIRTUAL)
            // -----------------------------------------------------------------
            if (opcode == Opcodes.INVOKEVIRTUAL && isBukkitRunnableInstanceMethod(name, desc)) {
                reasons.add(PatchReason.BUKKIT_RUNNABLE_INSTANCE_CALL);
                return;
            }

            // -----------------------------------------------------------------
            // Block.setType (INVOKEINTERFACE / INVOKEVIRTUAL)
            // -----------------------------------------------------------------
            if (BLOCK.equals(owner) && "setType".equals(name)) {
                reasons.add(PatchReason.BLOCK_SET_TYPE);
                return;
            }

            // -----------------------------------------------------------------
            // World creation — Bukkit.createWorld / WorldCreator.createWorld
            // -----------------------------------------------------------------
            if ("createWorld".equals(name)
                    && "(Lorg/bukkit/WorldCreator;)Lorg/bukkit/World;".equals(desc)) {
                reasons.add(PatchReason.WORLD_CREATION);
                return;
            }

            // WorldCreator constructor via INVOKESPECIAL
            if (opcode == Opcodes.INVOKESPECIAL && WORLD_CREATOR.equals(owner)
                    && "<init>".equals(name)) {
                reasons.add(PatchReason.WORLD_CREATOR_CONSTRUCTOR);
                return;
            }

            // -----------------------------------------------------------------
            // Plugin.getDefaultWorldGenerator
            // -----------------------------------------------------------------
            if (PLUGIN.equals(owner) && "getDefaultWorldGenerator".equals(name)) {
                reasons.add(PatchReason.WORLD_GENERATOR);
                return;
            }

            // -----------------------------------------------------------------
            // Bukkit static helpers — Bukkit.createWorld / Bukkit.getScheduler / etc.
            // -----------------------------------------------------------------
            if (BUKKIT.equals(owner)) {
                switch (name) {
                    case "createWorld":
                    case "getScheduler":
                        reasons.add(PatchReason.SCHEDULER_METHOD_CALL);
                        return;
                    default:
                        break;
                }
            }

            // -----------------------------------------------------------------
            // Scoreboard.registerNewTeam
            // -----------------------------------------------------------------
            if (SCOREBOARD.equals(owner) && "registerNewTeam".equals(name)) {
                reasons.add(PatchReason.SCOREBOARD_TEAM);
            }
        }

        // ------------------------------------------------------------------
        // Field instructions — catch GETFIELD/PUTFIELD on Bukkit types
        // inside methods (e.g. anonymous inner-class patterns)
        // ------------------------------------------------------------------
        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            if (isBukkitDescriptor(descriptor)) {
                reasons.add(PatchReason.FIELD_TYPE_BUKKIT);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isSchedulerOwner(String owner) {
        return BUKKIT_SCHEDULER.equals(owner) || CRAFT_SCHEDULER.equals(owner);
    }

    /**
     * Matches only the canonical BukkitRunnable scheduling signatures that
     * are redirected by {@code SchedulerClassTransformer}.
     */
    private static boolean isBukkitRunnableInstanceMethod(String name, String desc) {
        String mk = name + desc;
        switch (mk) {
            case "runTask(Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/scheduler/BukkitTask;":
            case "runTaskLater(Lorg/bukkit/plugin/Plugin;J)Lorg/bukkit/scheduler/BukkitTask;":
            case "runTaskTimer(Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;":
            case "runTaskAsynchronously(Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/scheduler/BukkitTask;":
            case "runTaskLaterAsynchronously(Lorg/bukkit/plugin/Plugin;J)Lorg/bukkit/scheduler/BukkitTask;":
            case "runTaskTimerAsynchronously(Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;":
                return true;
            default:
                return false;
        }
    }
}
