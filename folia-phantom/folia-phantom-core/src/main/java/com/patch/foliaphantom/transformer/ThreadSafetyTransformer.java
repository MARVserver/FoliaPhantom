package com.patch.foliaphantom.transformer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.util.List;

/**
 * {@code Block.setType} 呼び出しをスレッドセーフなラッパーに置き換えるトランスフォーマー。
 *
 * <p>Folia はリージョン単位のマルチスレッドモデルを採用しており、
 * ワールドスレッド以外からの {@code Block.setType} 呼び出しは
 * スレッドセーフではない。本トランスフォーマーはこれらの呼び出しを
 * {@code FoliaPatcher.safeSetType} / {@code FoliaPatcher.safeSetTypeWithPhysics}
 * に置き換える。</p>
 *
 * <p>変換例:
 * <pre>
 *   block.setType(Material.STONE);
 *     → FoliaPatcher.safeSetType(block, Material.STONE);
 *
 *   block.setType(Material.STONE, false);
 *     → FoliaPatcher.safeSetTypeWithPhysics(block, Material.STONE, false);
 * </pre>
 * </p>
 */
public final class ThreadSafetyTransformer implements ClassTransformer, Opcodes {

    /** 変換対象の Block クラス内部名 */
    private static final String BLOCK_OWNER = "org/bukkit/block/Block";

    /** setType メソッド名 */
    private static final String SET_TYPE = "setType";

    /** FoliaPatcher の内部名 */
    private static final String PATCHER_OWNER = "com/patch/foliaphantom/patcher/FoliaPatcher";

    /** safeSetType メソッド名（Material のみ） */
    private static final String SAFE_SET_TYPE = "safeSetType";

    /** safeSetTypeWithPhysics メソッド名（Material + boolean） */
    private static final String SAFE_SET_TYPE_WITH_PHYSICS = "safeSetTypeWithPhysics";

    /**
     * {@code (Lorg/bukkit/block/Block;Lorg/bukkit/Material;)V}
     * safeSetType のメソッド記述子
     */
    private static final String SAFE_SET_TYPE_DESC =
            "(Lorg/bukkit/block/Block;Lorg/bukkit/Material;)V";

    /**
     * {@code (Lorg/bukkit/block/Block;Lorg/bukkit/Material;Z)V}
     * safeSetTypeWithPhysics のメソッド記述子
     */
    private static final String SAFE_SET_TYPE_WITH_PHYSICS_DESC =
            "(Lorg/bukkit/block/Block;Lorg/bukkit/Material;Z)V";

    /**
     * クラスノード内の全メソッドを走査し、
     * {@code Block.setType} 呼び出しを FoliaPatcher のラッパーに置き換える。
     *
     * @param classNode  変換対象のクラスノード
     * @param className  クラス内部名（未使用だがインターフェース規定で保持）
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
     * 単一メソッド内の Block.setType 呼び出しを置き換える。
     *
     * @param method 変換対象のメソッドノード
     */
    private void transformMethod(MethodNode method) {
        AbstractInsnNode[] insns = method.instructions.toArray();
        for (int i = 0; i < insns.length; i++) {
            if (insns[i] instanceof MethodInsnNode methodInsn) {
                replaceIfBlockSetType(method, methodInsn, i);
            }
        }
    }

    /**
     * {@code Block.setType} の呼び出しであれば、
     * 引数の数に応じて適切な safeSetType に置き換える。
     *
     * @param method    対象メソッドノード
     * @param methodInsn 現在のメソッド呼び出しノード
     * @param index     命令配列内のインデックス（未使用）
     */
    private void replaceIfBlockSetType(
            MethodNode method,
            MethodInsnNode methodInsn,
            @SuppressWarnings("unused") int index) {
        if (!BLOCK_OWNER.equals(methodInsn.owner)) {
            return;
        }
        if (!SET_TYPE.equals(methodInsn.name)) {
            return;
        }
        // 引数が1つ: setType(Material)
        // 引数が2つ: setType(Material, boolean)
        int argCount = getArgumentCount(methodInsn.desc);
        if (argCount == 1) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = SAFE_SET_TYPE;
            methodInsn.desc = SAFE_SET_TYPE_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
        } else if (argCount == 2) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = SAFE_SET_TYPE_WITH_PHYSICS;
            methodInsn.desc = SAFE_SET_TYPE_WITH_PHYSICS_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
        }
    }

    /**
     * メソッド記述子から引数の数を取得する。
     *
     * @param desc メソッド記述子（例: "(Lorg/bukkit/Material;)V"）
     * @return 引数の数
     */
    private static int getArgumentCount(String desc) {
        int count = 0;
        int i = 1; // '(' の次から開始
        while (desc.charAt(i) != ')') {
            count++;
            char c = desc.charAt(i);
            if (c == 'L') {
                i = desc.indexOf(';', i) + 1;
            } else if (c == '[') {
                i++;
                // 配列の次が 'L' ならクラス名の終わりまでスキップ
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
