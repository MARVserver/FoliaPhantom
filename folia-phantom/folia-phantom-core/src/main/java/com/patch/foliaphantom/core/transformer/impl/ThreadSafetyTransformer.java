/*
 * Folia Phantom - Thread Safety Class Transformer
 * 
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;
import java.util.logging.Logger;

/**
 * Transforms thread-unsafe method calls into safe versions.
 * 
 * <p>
 * Specifically targets Block.setType and similar calls that must be
 * executed on the correct region thread in Folia.
 * </p>
 */
public class ThreadSafetyTransformer implements ClassTransformer {
    private final Logger logger;

    public ThreadSafetyTransformer(Logger logger) {
        this.logger = logger;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new ThreadSafetyVisitor(next);
    }

    private static class ThreadSafetyVisitor extends ClassVisitor {
        public ThreadSafetyVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            return new ThreadSafetyMethodVisitor(super.visitMethod(access, name, desc, sig, ex));
        }
    }

    private static class ThreadSafetyMethodVisitor extends MethodVisitor {
        private static final String PATCHER = "com/patch/foliaphantom/core/patcher/FoliaPatcher";

        public ThreadSafetyMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            // Redirect Block.setType calls
            if ("org/bukkit/block/Block".equals(owner) && name.equals("setType")) {
                if ("(Lorg/bukkit/Material;)V".equals(desc)) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER, "safeSetType",
                            "(Lorg/bukkit/block/Block;Lorg/bukkit/Material;)V", false);
                    return;
                } else if ("(Lorg/bukkit/Material;Z)V".equals(desc)) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER, "safeSetTypeWithPhysics",
                            "(Lorg/bukkit/block/Block;Lorg/bukkit/Material;Z)V", false);
                    return;
                }
            }

            // Redirect Bukkit.dispatchCommand static helper to a Folia-safe bridge.
            if (opcode == Opcodes.INVOKESTATIC
                    && "org/bukkit/Bukkit".equals(owner)
                    && "dispatchCommand".equals(name)
                    && "(Lorg/bukkit/command/CommandSender;Ljava/lang/String;)Z".equals(desc)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER, "safeDispatchCommand",
                        "(Lorg/bukkit/command/CommandSender;Ljava/lang/String;)Z", false);
                return;
            }

            // Redirect Entity#teleport calls that are illegal in Folia region threads.
            if (isEntityOwner(owner) && "teleport".equals(name)) {
                if ("(Lorg/bukkit/Location;)Z".equals(desc)) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER, "safeTeleport",
                            "(Lorg/bukkit/entity/Entity;Lorg/bukkit/Location;)Z", false);
                    return;
                } else if ("(Lorg/bukkit/Location;Lorg/bukkit/event/player/PlayerTeleportEvent$TeleportCause;)Z"
                        .equals(desc)) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER, "safeTeleportWithCause",
                            "(Lorg/bukkit/entity/Entity;Lorg/bukkit/Location;Lorg/bukkit/event/player/PlayerTeleportEvent$TeleportCause;)Z",
                            false);
                    return;
                }
            }

            // Redirect Entity#getCustomName to avoid async thread-check violations.
            if (isEntityOwner(owner) && "getCustomName".equals(name)
                    && "()Ljava/lang/String;".equals(desc)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER, "safeGetCustomName",
                        "(Lorg/bukkit/entity/Entity;)Ljava/lang/String;", false);
                return;
            }

            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private static boolean isEntityOwner(String owner) {
            return owner != null && owner.startsWith("org/bukkit/entity/");
        }
    }
}
