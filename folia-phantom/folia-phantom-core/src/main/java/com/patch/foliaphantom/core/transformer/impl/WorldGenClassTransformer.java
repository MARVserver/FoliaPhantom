/*
 * Folia Phantom - World Generation Class Transformer
 * 
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;
import java.util.logging.Logger;

/**
 * Transforms world creation and generator related calls.
 * 
 * <p>
 * Redirects Bukkit.createWorld and related calls to async/safe alternatives
 * provided by FoliaPatcher.
 * </p>
 */
public class WorldGenClassTransformer implements ClassTransformer {
    private final Logger logger;

    public WorldGenClassTransformer(Logger logger) {
        this.logger = logger;
    }

    @Override
    public ClassVisitor createVisitor(ClassVisitor next) {
        return new WorldGenVisitor(next);
    }

    private static class WorldGenVisitor extends ClassVisitor {
        public WorldGenVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            return new WorldGenMethodVisitor(super.visitMethod(access, name, desc, sig, ex));
        }
    }

    private static class WorldGenMethodVisitor extends MethodVisitor {
        private static final String PATCHER = "com/patch/foliaphantom/core/patcher/FoliaPatcher";

        public WorldGenMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            // Redirect Plugin.getDefaultWorldGenerator
            if ("org/bukkit/plugin/Plugin".equals(owner) && name.equals("getDefaultWorldGenerator")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER, name,
                        "(Lorg/bukkit/plugin/Plugin;" + desc.substring(1), false);
                return;
            }

            // Redirect Bukkit.createWorld or WorldCreator.createWorld
            if (name.equals("createWorld") && desc.equals("(Lorg/bukkit/WorldCreator;)Lorg/bukkit/World;")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER, "createWorld", desc, false);
                return;
            }

            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
