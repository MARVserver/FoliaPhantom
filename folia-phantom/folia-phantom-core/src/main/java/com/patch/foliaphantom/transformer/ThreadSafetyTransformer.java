package com.patch.foliaphantom.transformer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

/**
 * {@code Block} API calls that require region ownership are rewritten to Folia-safe bridges.
 */
public final class ThreadSafetyTransformer implements ClassTransformer, Opcodes {

    private static final String BLOCK_OWNER = "org/bukkit/block/Block";
    private static final String PATCHER_OWNER = "com/patch/foliaphantom/patcher/FoliaPatcher";
    private static final String BLOCK_READ_OWNER = "com/patch/foliaphantom/patcher/BlockReadBridge";

    private static final String SET_TYPE_1_DESC =
            "(Lorg/bukkit/block/Block;Lorg/bukkit/Material;)V";
    private static final String SET_TYPE_2_DESC =
            "(Lorg/bukkit/block/Block;Lorg/bukkit/Material;Z)V";
    private static final String SET_BLOCK_DATA_1_DESC =
            "(Lorg/bukkit/block/Block;Lorg/bukkit/block/data/BlockData;)V";
    private static final String SET_BLOCK_DATA_2_DESC =
            "(Lorg/bukkit/block/Block;Lorg/bukkit/block/data/BlockData;Z)V";
    private static final String BREAK_NATURALLY_0_DESC =
            "(Lorg/bukkit/block/Block;)Z";
    private static final String BREAK_NATURALLY_1_DESC =
            "(Lorg/bukkit/block/Block;Lorg/bukkit/inventory/ItemStack;)Z";
    private static final String APPLY_BONE_MEAL_DESC =
            "(Lorg/bukkit/block/Block;Lorg/bukkit/block/BlockFace;)Z";

    private static final String GET_TYPE_DESC =
            "(Lorg/bukkit/block/Block;)Lorg/bukkit/Material;";
    private static final String GET_BLOCK_DATA_DESC =
            "(Lorg/bukkit/block/Block;)Lorg/bukkit/block/data/BlockData;";
    private static final String GET_STATE_DESC =
            "(Lorg/bukkit/block/Block;)Lorg/bukkit/block/BlockState;";
    private static final String GET_DROPS_0_DESC =
            "(Lorg/bukkit/block/Block;)Ljava/util/Collection;";
    private static final String GET_DROPS_1_DESC =
            "(Lorg/bukkit/block/Block;Lorg/bukkit/inventory/ItemStack;)Ljava/util/Collection;";

    @Override
    public byte[] transform(ClassNode classNode, String className, ClassWriter writer) {
        List<MethodNode> methods = classNode.methods;
        if (methods == null) {
            classNode.accept(writer);
            return writer.toByteArray();
        }
        for (MethodNode method : methods) {
            transformMethod(method);
        }
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private void transformMethod(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof MethodInsnNode methodInsn) {
                replaceIfBlockCall(methodInsn);
            }
        }
    }

    private void replaceIfBlockCall(MethodInsnNode methodInsn) {
        if (!BLOCK_OWNER.equals(methodInsn.owner)) {
            return;
        }

        String name = methodInsn.name;
        int argCount = getArgumentCount(methodInsn.desc);

        if ("getType".equals(name) && argCount == 0) {
            replaceStatic(methodInsn, BLOCK_READ_OWNER, "getType", GET_TYPE_DESC);
            return;
        }
        if ("getBlockData".equals(name) && argCount == 0) {
            replaceStatic(methodInsn, BLOCK_READ_OWNER, "getBlockData", GET_BLOCK_DATA_DESC);
            return;
        }
        if ("getState".equals(name) && argCount == 0) {
            replaceStatic(methodInsn, BLOCK_READ_OWNER, "getState", GET_STATE_DESC);
            return;
        }
        if ("getDrops".equals(name) && argCount == 0) {
            replaceStatic(methodInsn, BLOCK_READ_OWNER, "getDrops", GET_DROPS_0_DESC);
            return;
        }
        if ("getDrops".equals(name) && argCount == 1
                && methodInsn.desc.startsWith("(Lorg/bukkit/inventory/ItemStack;")) {
            replaceStatic(methodInsn, BLOCK_READ_OWNER, "getDrops", GET_DROPS_1_DESC);
            return;
        }

        if ("setType".equals(name) && argCount == 1) {
            replaceStatic(methodInsn, PATCHER_OWNER, "safeSetType", SET_TYPE_1_DESC);
            return;
        }
        if ("setType".equals(name) && argCount == 2) {
            replaceStatic(methodInsn, PATCHER_OWNER, "safeSetTypeWithPhysics", SET_TYPE_2_DESC);
            return;
        }
        if ("setBlockData".equals(name) && argCount == 1) {
            replaceStatic(methodInsn, PATCHER_OWNER, "safeSetBlockData", SET_BLOCK_DATA_1_DESC);
            return;
        }
        if ("setBlockData".equals(name) && argCount == 2) {
            replaceStatic(methodInsn, PATCHER_OWNER, "safeSetBlockData", SET_BLOCK_DATA_2_DESC);
            return;
        }
        if ("breakNaturally".equals(name) && argCount == 0) {
            replaceStatic(methodInsn, PATCHER_OWNER, "safeBreakNaturally", BREAK_NATURALLY_0_DESC);
            return;
        }
        if ("breakNaturally".equals(name) && argCount == 1) {
            replaceStatic(methodInsn, PATCHER_OWNER, "safeBreakNaturally", BREAK_NATURALLY_1_DESC);
            return;
        }
        if ("applyBoneMeal".equals(name)) {
            replaceStatic(methodInsn, PATCHER_OWNER, "safeApplyBoneMeal", APPLY_BONE_MEAL_DESC);
        }
    }

    private static void replaceStatic(
            MethodInsnNode methodInsn, String owner, String name, String descriptor) {
        methodInsn.owner = owner;
        methodInsn.name = name;
        methodInsn.desc = descriptor;
        methodInsn.setOpcode(INVOKESTATIC);
        methodInsn.itf = false;
    }

    private static int getArgumentCount(String desc) {
        int count = 0;
        int i = 1;
        while (desc.charAt(i) != ')') {
            count++;
            char c = desc.charAt(i);
            if (c == 'L') {
                i = desc.indexOf(';', i) + 1;
            } else if (c == '[') {
                i++;
                while (desc.charAt(i) == '[') {
                    i++;
                }
                if (desc.charAt(i) == 'L') {
                    i = desc.indexOf(';', i) + 1;
                } else {
                    i++;
                }
            } else {
                i++;
            }
        }
        return count;
    }
}
