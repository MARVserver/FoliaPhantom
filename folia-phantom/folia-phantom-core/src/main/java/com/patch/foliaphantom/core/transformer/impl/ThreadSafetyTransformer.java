package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.patcher.FoliaPatcher;
import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ThreadSafetyTransformer implements ClassTransformer {
    private final Logger logger;

    public ThreadSafetyTransformer(Logger logger) {
        this.logger = logger;
    }

    @Override
    public byte[] transform(byte[] originalBytes) {
        String className = "Unknown";
        try {
            ClassReader cr = new ClassReader(originalBytes);
            className = cr.getClassName();
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ThreadSafetyClassVisitor cv = new ThreadSafetyClassVisitor(cw);
            cr.accept(cv, ClassReader.EXPAND_FRAMES);
            return cw.toByteArray();
        } catch (Exception e) {
            logger.log(Level.WARNING, "[FoliaPhantom] Failed to apply thread safety transformations to class: " + className + ". Returning original bytes.", e);
            return originalBytes;
        }
    }

    private static class ThreadSafetyClassVisitor extends ClassVisitor {
        public ThreadSafetyClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            return new ThreadSafetyMethodVisitor(mv);
        }
    }

    private static class ThreadSafetyMethodVisitor extends MethodVisitor {
        private static final String PATCHER_INTERNAL_NAME = Type.getInternalName(FoliaPatcher.class);

        public ThreadSafetyMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            String methodKey = name + desc;
            if (opcode == Opcodes.INVOKEINTERFACE && owner.equals("org/bukkit/block/Block") && FoliaPatcher.UNSAFE_METHOD_MAP.containsKey(methodKey)) {
                String patcherMethodName = FoliaPatcher.UNSAFE_METHOD_MAP.get(methodKey);
                String newDesc = "(Lorg/bukkit/block/Block;" + desc.substring(1); // Prepend Block instance
                super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER_INTERNAL_NAME, patcherMethodName, newDesc, false);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
