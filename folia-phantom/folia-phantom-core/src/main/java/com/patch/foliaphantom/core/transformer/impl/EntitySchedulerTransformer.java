package com.patch.foliaphantom.core.transformer.impl;

import com.patch.foliaphantom.core.patcher.FoliaPatcher;
import com.patch.foliaphantom.core.transformer.ClassTransformer;
import org.objectweb.asm.*;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class EntitySchedulerTransformer implements ClassTransformer {
    private final Logger logger;

    // We need to keep track of classes that implement BukkitRunnable
    private final Set<String> bukkitRunnableImplementers = new HashSet<>();

    public EntitySchedulerTransformer(Logger logger) {
        this.logger = logger;
    }

    @Override
    public byte[] transform(byte[] originalBytes) {
        String className = "Unknown";
        try {
            ClassReader cr = new ClassReader(originalBytes);
            className = cr.getClassName();

            // Check if this class implements BukkitRunnable
            if (Arrays.asList(cr.getInterfaces()).contains("org/bukkit/scheduler/BukkitRunnable")) {
                bukkitRunnableImplementers.add(className);
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            EntitySchedulerClassVisitor cv = new EntitySchedulerClassVisitor(cw);
            cr.accept(cv, ClassReader.EXPAND_FRAMES);
            return cw.toByteArray();
        } catch (Exception e) {
            logger.log(Level.WARNING, "[FoliaPhantom] Failed to transform entity scheduler calls in class: " + className + ". Returning original bytes.", e);
            return originalBytes;
        }
    }

    private class EntitySchedulerClassVisitor extends ClassVisitor {
        private String currentClassName;
        public EntitySchedulerClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.currentClassName = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            return new EntitySchedulerMethodVisitor(mv);
        }
    }

    private class EntitySchedulerMethodVisitor extends MethodVisitor {
        private static final String PATCHER_INTERNAL_NAME = Type.getInternalName(FoliaPatcher.class);

        public EntitySchedulerMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            // Check for BukkitRunnable calls like bukkitRunnable.runTask(plugin)
            if (opcode == Opcodes.INVOKEVIRTUAL && bukkitRunnableImplementers.contains(owner)) {
                if (isBukkitRunnableTaskMethod(name, desc)) {
                    // Prepend "folia_" to the method name
                    String newName = "folia_" + name;
                    // The new descriptor will have an Entity as the first parameter
                    String newDesc = "(Lorg/bukkit/entity/Entity;" + desc.substring(1);
                    // We expect the Entity object to be on the stack before the BukkitRunnable instance.
                    // The transformer will need to ensure this is the case.
                    // For now, we just change the call.
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER_INTERNAL_NAME, newName, newDesc, false);
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }

        private boolean isBukkitRunnableTaskMethod(String name, String desc) {
            return (name.equals("runTask") && desc.equals("(Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/scheduler/BukkitTask;")) ||
                   (name.equals("runTaskLater") && desc.equals("(Lorg/bukkit/plugin/Plugin;J)Lorg/bukkit/scheduler/BukkitTask;")) ||
                   (name.equals("runTaskTimer") && desc.equals("(Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;"));
        }
    }
}
