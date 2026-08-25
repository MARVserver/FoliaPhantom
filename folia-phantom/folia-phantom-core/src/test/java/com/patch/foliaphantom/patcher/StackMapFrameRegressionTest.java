package com.patch.foliaphantom.patcher;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;

import static org.junit.Assert.assertEquals;

public class StackMapFrameRegressionTest implements Opcodes {

    @Test
    public void preservesExistingExceptionHandlerFrameType() {
        ClassWriter originalWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        originalWriter.visit(V17, ACC_PUBLIC, "test/FrameFixture", null, "java/lang/Object", null);

        MethodNode method = new MethodNode(ASM9, ACC_PUBLIC | ACC_STATIC, "run", "()V", null, null);
        org.objectweb.asm.tree.LabelNode start = new org.objectweb.asm.tree.LabelNode();
        org.objectweb.asm.tree.LabelNode end = new org.objectweb.asm.tree.LabelNode();
        org.objectweb.asm.tree.LabelNode handler = new org.objectweb.asm.tree.LabelNode();
        method.instructions.add(start);
        method.instructions.add(new TypeInsnNode(NEW, "java/io/IOException"));
        method.instructions.add(new InsnNode(DUP));
        method.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/io/IOException", "<init>", "()V", false));
        method.instructions.add(new InsnNode(ATHROW));
        method.instructions.add(end);
        method.instructions.add(handler);
        method.instructions.add(new InsnNode(POP));
        method.instructions.add(new InsnNode(RETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, "java/io/IOException"));
        method.accept(originalWriter);
        originalWriter.visitEnd();

        ClassReader reader = new ClassReader(originalWriter.toByteArray());
        ClassNode classNode = new ClassNode(ASM9);
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);

        ClassWriter preservingWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(preservingWriter);
        ClassNode roundTripped = new ClassNode(ASM9);
        new ClassReader(preservingWriter.toByteArray()).accept(roundTripped, ClassReader.EXPAND_FRAMES);

        org.objectweb.asm.tree.FrameNode frame = null;
        for (org.objectweb.asm.tree.AbstractInsnNode insn : roundTripped.methods.get(0).instructions) {
            if (insn instanceof org.objectweb.asm.tree.FrameNode candidate) {
                frame = candidate;
                break;
            }
        }
        assertEquals("java/io/IOException", frame.stack.get(0));
    }
}
