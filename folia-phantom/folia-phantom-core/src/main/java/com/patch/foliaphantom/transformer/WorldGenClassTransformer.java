package com.patch.foliaphantom.transformer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.util.List;

/**
 * ワールド生成関連の API 呼び出しを非同期化するトランスフォーマー。
 *
 * <p>Folia ではワールド生成は専用スレッドで行う必要がある。
 * 本トランスフォーマーは以下の呼び出しを変換する:
 * <ul>
 *   <li>{@code Bukkit.createWorld(WorldCreator)} → {@code FoliaPatcher.createWorld(WorldCreator)}</li>
 *   <li>{@code new WorldCreator(name).createWorld()} → {@code FoliaPatcher.createWorld(WorldCreator)}</li>
 *   <li>{@code plugin.getDefaultWorldGenerator(name, id)} → {@code FoliaPatcher.getDefaultWorldGenerator(plugin, name, id)}</li>
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

    /** FoliaPatcher の内部名 */
    private static final String PATCHER_OWNER = "com/patch/foliaphantom/patcher/FoliaPatcher";

    /** Bukkit.createWorld メソッド名 */
    private static final String CREATE_WORLD = "createWorld";

    /** WorldCreator.createWorld メソッド名 */
    private static final String WORLD_CREATOR_CREATE = "createWorld";

    /** Plugin.getDefaultWorldGenerator メソッド名 */
    private static final String GET_DEFAULT_WORLD_GENERATOR = "getDefaultWorldGenerator";

    /** FoliaPatcher.createWorld の記述子: {@code (Lorg/bukkit/WorldCreator;)Lorg/bukkit/World;} */
    private static final String CREATE_WORLD_DESC =
            "(Lorg/bukkit/WorldCreator;)Lorg/bukkit/World;";

    /** FoliaPatcher.getDefaultWorldGenerator の記述子: {@code (Lorg/bukkit/plugin/Plugin;Ljava/lang/String;Ljava/lang/String;)Lorg/bukkit/generator/ChunkGenerator;} */
    private static final String GET_DEFAULT_WORLD_GENERATOR_DESC =
            "(Lorg/bukkit/plugin/Plugin;Ljava/lang/String;Ljava/lang/String;)Lorg/bukkit/generator/ChunkGenerator;";

    /**
     * クラスノード内の全メソッドを走査し、
     * ワールド生成関連の呼び出しを変換する。
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
     * 単一メソッド内のワールド生成関連呼び出しを置き換える。
     *
     * @param method 変換対象のメソッドノード
     */
    private void transformMethod(MethodNode method) {
        AbstractInsnNode[] insns = method.instructions.toArray();
        for (AbstractInsnNode insn : insns) {
            if (insn instanceof MethodInsnNode methodInsn) {
                replaceIfWorldGenCall(methodInsn);
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
}
