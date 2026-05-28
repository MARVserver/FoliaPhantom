package com.patch.foliaphantom.transformer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.util.List;

/**
 * ワールド生成関連 + ワールド操作の API 呼び出しを非同期化するトランスフォーマー。
 *
 * <p>Folia ではワールド操作は適切なリージョンスケジューラ上で行う必要がある。
 * 本トランスフォーマーは以下の呼び出しを変換する:
 * <ul>
 *   <li>{@code Bukkit.createWorld(WorldCreator)} → {@code FoliaPatcher.createWorld(WorldCreator)}</li>
 *   <li>{@code new WorldCreator(name).createWorld()} → {@code FoliaPatcher.createWorld(WorldCreator)}</li>
 *   <li>{@code plugin.getDefaultWorldGenerator(name, id)} → {@code FoliaPatcher.getDefaultWorldGenerator(plugin, name, id)}</li>
 *   <li>{@code World.spawn(Location, Class)} → {@code FoliaPatcher.safeSpawn(location, clazz)}</li>
 *   <li>{@code World.dropItem(Location, ItemStack)} → {@code FoliaPatcher.safeDropItem(location, item)}</li>
 *   <li>{@code World.dropItemNaturally(Location, ItemStack)} → {@code FoliaPatcher.safeDropItemNaturally(location, item)}</li>
 *   <li>{@code World.createExplosion(Location, float)} → {@code FoliaPatcher.safeCreateExplosion(location, power)}</li>
 *   <li>{@code World.strikeLightning(Location)} → {@code FoliaPatcher.safeStrikeLightning(location)}</li>
 * </ul>
 * </p>
 */
public final class WorldGenClassTransformer implements ClassTransformer, Opcodes {

    /** Bukkit クラスの内部名 */
    private static final String BUKKIT_OWNER = "org/bukkit/Bukkit";

    /** WorldCreator クラスの内部名 */
    private static final String WORLD_CREATOR_OWNER = "org/bukkit/WorldCreator";

    /** Plugin クラスの内部名 */
    private static final String PLUGIN_OWNER = "org/bukkit/plugin/Plugin";

    /** World クラスの内部名 */
    private static final String WORLD_OWNER = "org/bukkit/World";

    /** FoliaPatcher の内部名 */
    private static final String PATCHER_OWNER = "com/patch/foliaphantom/patcher/FoliaPatcher";

    /** Bukkit.createWorld メソッド名 */
    private static final String CREATE_WORLD = "createWorld";

    /** WorldCreator.createWorld メソッド名 */
    private static final String WORLD_CREATOR_CREATE = "createWorld";

    /** Plugin.getDefaultWorldGenerator メソッド名 */
    private static final String GET_DEFAULT_WORLD_GENERATOR = "getDefaultWorldGenerator";

    /** FoliaPatcher.createWorld の記述子 */
    private static final String CREATE_WORLD_DESC =
            "(Lorg/bukkit/WorldCreator;)Lorg/bukkit/World;";

    /** FoliaPatcher.getDefaultWorldGenerator の記述子 */
    private static final String GET_DEFAULT_WORLD_GENERATOR_DESC =
            "(Lorg/bukkit/plugin/Plugin;Ljava/lang/String;Ljava/lang/String;)Lorg/bukkit/generator/ChunkGenerator;";

    /** safeSpawn の記述子 */
    private static final String SPAWN_DESC =
            "(Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;";

    /** safeDropItem の記述子 */
    private static final String DROP_ITEM_DESC =
            "(Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item;";

    /** safeDropItemNaturally の記述子 */
    private static final String DROP_ITEM_NATURALLY_DESC =
            "(Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item;";

    /** safeCreateExplosion の記述子（float のみ） */
    private static final String EXPLOSION_1_DESC =
            "(Lorg/bukkit/Location;F)Z";

    /** safeCreateExplosion の記述子（float + boolean） */
    private static final String EXPLOSION_2_DESC =
            "(Lorg/bukkit/Location;FZ)Z";

    /** safeStrikeLightning の記述子 */
    private static final String STRIKE_LIGHTNING_DESC =
            "(Lorg/bukkit/Location;)Lorg/bukkit/entity/LightningStrike;";

    /**
     * クラスノード内の全メソッドを走査し、
     * ワールド生成/操作関連の呼び出しを変換する。
     *
     * @param classNode  変換対象のクラスノード
     * @param className  クラス内部名
     * @param writer     出力先の ClassWriter
     * @return 変換後のバイト配列
     */
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

    /**
     * 単一メソッド内のワールド生成/操作関連呼び出しを置き換える。
     *
     * @param method 変換対象のメソッドノード
     */
    private void transformMethod(MethodNode method) {
        AbstractInsnNode[] insns = method.instructions.toArray();
        for (AbstractInsnNode insn : insns) {
            if (insn instanceof MethodInsnNode methodInsn) {
                replaceIfWorldGenCall(methodInsn);
                replaceIfWorldOperation(methodInsn);
            }
        }
    }

    /**
     * メソッド呼び出しノードがワールド生成関連であれば変換する。
     *
     * @param methodInsn 検査対象のメソッド呼び出しノード
     */
    private void replaceIfWorldGenCall(MethodInsnNode methodInsn) {
        // Bukkit.createWorld の変換
        if (BUKKIT_OWNER.equals(methodInsn.owner)
                && CREATE_WORLD.equals(methodInsn.name)) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = CREATE_WORLD;
            methodInsn.desc = CREATE_WORLD_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        // WorldCreator.createWorld の変換
        if (WORLD_CREATOR_OWNER.equals(methodInsn.owner)
                && WORLD_CREATOR_CREATE.equals(methodInsn.name)) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = CREATE_WORLD;
            methodInsn.desc = CREATE_WORLD_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        // Plugin.getDefaultWorldGenerator の変換
        if (PLUGIN_OWNER.equals(methodInsn.owner)
                && GET_DEFAULT_WORLD_GENERATOR.equals(methodInsn.name)) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = GET_DEFAULT_WORLD_GENERATOR;
            methodInsn.desc = GET_DEFAULT_WORLD_GENERATOR_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
        }
    }

    /**
     * World インスタンスメソッドの呼び出しを変換する。
     */
    private void replaceIfWorldOperation(MethodInsnNode methodInsn) {
        if (!WORLD_OWNER.equals(methodInsn.owner)) {
            return;
        }
        String name = methodInsn.name;
        int argCount = getArgumentCount(methodInsn.desc);

        // World.spawn(Location, Class) → safeSpawn(Location, Class)
        if ("spawn".equals(name) && argCount >= 2) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = "safeSpawn";
            methodInsn.desc = SPAWN_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        // World.dropItem(Location, ItemStack) → safeDropItem(Location, ItemStack)
        if ("dropItem".equals(name)) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = "safeDropItem";
            methodInsn.desc = DROP_ITEM_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        // World.dropItemNaturally(Location, ItemStack) → safeDropItemNaturally(Location, ItemStack)
        if ("dropItemNaturally".equals(name)) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = "safeDropItemNaturally";
            methodInsn.desc = DROP_ITEM_NATURALLY_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        // World.createExplosion(Location, float) → safeCreateExplosion(Location, float)
        if ("createExplosion".equals(name) && argCount == 2) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = "safeCreateExplosion";
            methodInsn.desc = EXPLOSION_1_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        // World.createExplosion(Location, float, boolean) → safeCreateExplosion(Location, float, boolean)
        if ("createExplosion".equals(name) && argCount >= 3) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = "safeCreateExplosion";
            methodInsn.desc = EXPLOSION_2_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        // World.strikeLightning(Location) → safeStrikeLightning(Location)
        if ("strikeLightning".equals(name)) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = "safeStrikeLightning";
            methodInsn.desc = STRIKE_LIGHTNING_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
        }
    }

    /**
     * メソッド記述子から引数の数を取得する。
     */
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
