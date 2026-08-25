package com.patch.foliaphantom.transformer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.util.List;

/**
 * {@code Bukkit.createWorld} / {@code WorldCreator.createWorld} と
 * {@code World} 操作呼び出しを Folia 互換ラッパーへ置き換える。
 */
public final class WorldGenClassTransformer implements ClassTransformer, Opcodes {

    private static final String BUKKIT_OWNER = "org/bukkit/Bukkit";
    private static final String WORLD_CREATOR_OWNER = "org/bukkit/WorldCreator";
    private static final String PLUGIN_OWNER = "org/bukkit/plugin/Plugin";
    private static final String WORLD_OWNER = "org/bukkit/World";
    private static final String PATCHER_OWNER = "com/patch/foliaphantom/patcher/FoliaPatcher";

    private static final String CREATE_WORLD = "createWorld";
    private static final String WORLD_CREATOR_CREATE = "createWorld";
    private static final String GET_DEFAULT_WORLD_GENERATOR = "getDefaultWorldGenerator";

    private static final String CREATE_WORLD_DESC =
            "(Lorg/bukkit/WorldCreator;)Lorg/bukkit/World;";
    private static final String GET_DEFAULT_WORLD_GENERATOR_DESC =
            "(Lorg/bukkit/plugin/Plugin;Ljava/lang/String;Ljava/lang/String;)Lorg/bukkit/generator/ChunkGenerator;";
    private static final String SPAWN_DESC =
            "(Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;";
    private static final String DROP_ITEM_DESC =
            "(Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item;";
    private static final String DROP_ITEM_NATURALLY_DESC =
            "(Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item;";
    private static final String EXPLOSION_1_DESC =
            "(Lorg/bukkit/Location;F)Z";
    private static final String EXPLOSION_2_DESC =
            "(Lorg/bukkit/Location;FZ)Z";
    private static final String STRIKE_LIGHTNING_DESC =
            "(Lorg/bukkit/Location;)Lorg/bukkit/entity/LightningStrike;";

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
        AbstractInsnNode[] insns = method.instructions.toArray();
        for (AbstractInsnNode insn : insns) {
            if (insn instanceof MethodInsnNode methodInsn) {
                replaceIfWorldGenCall(methodInsn);
                replaceIfWorldOperation(method, methodInsn);
            }
        }
    }

    private void replaceIfWorldGenCall(MethodInsnNode methodInsn) {
        if (BUKKIT_OWNER.equals(methodInsn.owner)
                && CREATE_WORLD.equals(methodInsn.name)) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = CREATE_WORLD;
            methodInsn.desc = CREATE_WORLD_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        if (WORLD_CREATOR_OWNER.equals(methodInsn.owner)
                && WORLD_CREATOR_CREATE.equals(methodInsn.name)) {
            // WorldCreator is the original invokevirtual receiver and becomes the
            // first static argument, so the stack effect remains unchanged.
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = CREATE_WORLD;
            methodInsn.desc = CREATE_WORLD_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        if (PLUGIN_OWNER.equals(methodInsn.owner)
                && GET_DEFAULT_WORLD_GENERATOR.equals(methodInsn.name)) {
            // Plugin receiver is preserved as the first static argument.
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = GET_DEFAULT_WORLD_GENERATOR;
            methodInsn.desc = GET_DEFAULT_WORLD_GENERATOR_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
        }
    }

    private void replaceIfWorldOperation(MethodNode method, MethodInsnNode methodInsn) {
        if (!WORLD_OWNER.equals(methodInsn.owner)) {
            return;
        }
        String name = methodInsn.name;
        int argCount = getArgumentCount(methodInsn.desc);

        if ("spawn".equals(name) && argCount == 2) {
            discardWorldReceiver(method, methodInsn, 2);
            rewriteWorldCall(methodInsn, "safeSpawn", SPAWN_DESC);
            return;
        }
        if ("dropItem".equals(name) && argCount == 2) {
            discardWorldReceiver(method, methodInsn, 2);
            rewriteWorldCall(methodInsn, "safeDropItem", DROP_ITEM_DESC);
            return;
        }
        if ("dropItemNaturally".equals(name) && argCount == 2) {
            discardWorldReceiver(method, methodInsn, 2);
            rewriteWorldCall(methodInsn, "safeDropItemNaturally", DROP_ITEM_NATURALLY_DESC);
            return;
        }
        if ("createExplosion".equals(name) && argCount == 2) {
            discardWorldReceiver(method, methodInsn, 2);
            rewriteWorldCall(methodInsn, "safeCreateExplosion", EXPLOSION_1_DESC);
            return;
        }
        if ("createExplosion".equals(name) && argCount == 3) {
            discardWorldReceiver(method, methodInsn, 3);
            rewriteWorldCall(methodInsn, "safeCreateExplosion", EXPLOSION_2_DESC);
            return;
        }
        if ("strikeLightning".equals(name) && argCount == 1) {
            discardWorldReceiver(method, methodInsn, 1);
            rewriteWorldCall(methodInsn, "safeStrikeLightning", STRIKE_LIGHTNING_DESC);
        }
    }

    private static void rewriteWorldCall(
            MethodInsnNode methodInsn, String name, String descriptor) {
        methodInsn.owner = PATCHER_OWNER;
        methodInsn.name = name;
        methodInsn.desc = descriptor;
        methodInsn.setOpcode(INVOKESTATIC);
        methodInsn.itf = false;
    }

    /**
     * Remove the original {@code World} invokevirtual receiver while leaving all
     * arguments in their original order for the replacement static call.
     *
     * <p>All currently transformed World methods use category-1 arguments
     * (references, float and boolean), so these permutations do not require
     * temporary locals and therefore do not alter StackMapFrame local state.</p>
     */
    private static void discardWorldReceiver(
            MethodNode method, MethodInsnNode call, int argumentCount) {
        InsnList cleanup = new InsnList();
        switch (argumentCount) {
            case 1 -> {
                // world, a -> a
                cleanup.add(new InsnNode(SWAP));
                cleanup.add(new InsnNode(POP));
            }
            case 2 -> {
                // world, a, b -> a, b
                cleanup.add(new InsnNode(DUP2_X1));
                cleanup.add(new InsnNode(POP2));
                cleanup.add(new InsnNode(POP));
            }
            case 3 -> {
                // world, a, b, c -> a, b, c (all values are category-1)
                cleanup.add(new InsnNode(DUP_X2));
                cleanup.add(new InsnNode(POP));
                cleanup.add(new InsnNode(DUP2_X2));
                cleanup.add(new InsnNode(POP2));
                cleanup.add(new InsnNode(SWAP));
                cleanup.add(new InsnNode(POP));
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported World operation argument count: " + argumentCount);
        }
        method.instructions.insertBefore(call, cleanup);
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
