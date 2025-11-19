package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.patcher.FoliaPatcher;
import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;

import java.util.logging.Level;
import java.util.logging.Logger;

public class WorldGenClassTransformer implements ClassTransformer {
    private final Logger logger;

    public WorldGenClassTransformer(Logger logger) {
        this.logger = logger;
    }

    @Override
    public byte[] transform(byte[] originalBytes) {
        String className = "Unknown";
        try {
            ClassReader cr = new ClassReader(originalBytes);
            className = cr.getClassName();
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            WorldGenClassVisitor cv = new WorldGenClassVisitor(cw);
            cr.accept(cv, ClassReader.EXPAND_FRAMES);
            return cw.toByteArray();
        } catch (Exception e) {
            logger.log(Level.WARNING, "[FoliaPhantom] Failed to transform class for world generation: " + className + ". Returning original bytes.", e);
            return originalBytes;
        }
    }

    private static class WorldGenClassVisitor extends ClassVisitor {
        public WorldGenClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            return new WorldGenMethodVisitor(mv);
        }
    }

    private static class WorldGenMethodVisitor extends MethodVisitor {
        private static final String PATCHER_INTERNAL_NAME = Type.getInternalName(FoliaPatcher.class);

        public WorldGenMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            // Intercept ChunkGenerator related calls
            if (opcode == Opcodes.INVOKEINTERFACE && owner.equals("org/bukkit/plugin/Plugin")) {
                if (name.equals("getDefaultWorldGenerator") && desc.equals("(Ljava/lang/String;Ljava/lang/String;)Lorg/bukkit/generator/ChunkGenerator;")) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER_INTERNAL_NAME, "getDefaultWorldGenerator", "(Lorg/bukkit/plugin/Plugin;Ljava/lang/String;Ljava/lang/String;)Lorg/bukkit/generator/ChunkGenerator;", false);
                    return;
                }
            }
            // Intercept calls to Bukkit.createWorld
            if (opcode == Opcodes.INVOKESTATIC && owner.equals("org/bukkit/Bukkit")) {
                if (name.equals("createWorld") && desc.equals("(Lorg/bukkit/WorldCreator;)Lorg/bukkit/World;")) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER_INTERNAL_NAME, "createWorld", "(Lorg/bukkit/WorldCreator;)Lorg/bukkit/World;", false);
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
