package com.patch.foliaphantom.transformer;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class ThreadSafetyTransformerTest implements Opcodes {

    private static final String BLOCK = "org/bukkit/block/Block";
    private static final String BRIDGE = "com/patch/foliaphantom/patcher/BlockReadBridge";

    @Test
    public void rewritesGetTypeToRegionOwnedBridgeWithoutChangingStackShape() {
        ClassNode transformed = transform(classWithBlockRead(
                "getType", "()Lorg/bukkit/Material;", "()Lorg/bukkit/Material;"));
        MethodInsnNode call = findOnlyCall(transformed.methods.get(0));

        assertNotNull(call);
        assertEquals(INVOKESTATIC, call.getOpcode());
        assertEquals(BRIDGE, call.owner);
        assertEquals("getType", call.name);
        assertEquals("(Lorg/bukkit/block/Block;)Lorg/bukkit/Material;", call.desc);
        assertFalse(call.itf);
    }

    @Test
    public void rewritesCommonBlockStateReads() {
        assertReadRewrite(
                "getBlockData",
                "()Lorg/bukkit/block/data/BlockData;",
                "(Lorg/bukkit/block/Block;)Lorg/bukkit/block/data/BlockData;");
        assertReadRewrite(
                "getState",
                "()Lorg/bukkit/block/BlockState;",
                "(Lorg/bukkit/block/Block;)Lorg/bukkit/block/BlockState;");
        assertReadRewrite(
                "getDrops",
                "()Ljava/util/Collection;",
                "(Lorg/bukkit/block/Block;)Ljava/util/Collection;");
    }

    private static void assertReadRewrite(String name, String originalDesc, String expectedDesc) {
        ClassNode transformed = transform(classWithBlockRead(name, originalDesc, originalDesc));
        MethodInsnNode call = findOnlyCall(transformed.methods.get(0));
        assertNotNull(call);
        assertEquals(INVOKESTATIC, call.getOpcode());
        assertEquals(BRIDGE, call.owner);
        assertEquals(name, call.name);
        assertEquals(expectedDesc, call.desc);
    }

    private static ClassNode transform(ClassNode input) {
        ThreadSafetyTransformer transformer = new ThreadSafetyTransformer();
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        byte[] bytes = transformer.transform(input, input.name, writer);
        ClassNode output = new ClassNode(ASM9);
        new ClassReader(bytes).accept(output, 0);
        return output;
    }

    private static ClassNode classWithBlockRead(
            String methodName, String invocationDesc, String returnDesc) {
        ClassNode classNode = new ClassNode(ASM9);
        classNode.version = V17;
        classNode.access = ACC_PUBLIC;
        classNode.name = "example/BlockReadFixture";
        classNode.superName = "java/lang/Object";

        MethodNode method = new MethodNode(
                ASM9,
                ACC_PUBLIC | ACC_STATIC,
                "read",
                "(Lorg/bukkit/block/Block;)" + returnDesc.substring(2),
                null,
                null);
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
                INVOKEINTERFACE, BLOCK, methodName, invocationDesc, true));
        method.instructions.add(new InsnNode(ARETURN));
        classNode.methods.add(method);
        return classNode;
    }

    private static MethodInsnNode findOnlyCall(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode methodInsn) {
                return methodInsn;
            }
        }
        return null;
    }
}
