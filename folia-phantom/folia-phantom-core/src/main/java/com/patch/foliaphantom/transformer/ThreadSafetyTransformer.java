package com.patch.foliaphantom.transformer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.util.List;
import java.util.Set;

/**
 * {@code Block} の書き込み操作呼び出しをスレッドセーフなラッパーに置き換えるトランスフォーマー。
 *
 * <p>Folia はリージョン単位のマルチスレッドモデルを採用しており、
 * ワールドスレッド以外からのブロック操作呼び出しは
 * スレッドセーフではない。本トランスフォーマーはこれらの呼び出しを
 * {@code FoliaPatcher} のラッパーに置き換える。</p>
 *
 * <p>変換対象:
 * <ul>
 *   <li>{@code Block.setType(Material)} → {@code FoliaPatcher.safeSetType(block, material)}</li>
 *   <li>{@code Block.setType(Material, boolean)} → {@code FoliaPatcher.safeSetTypeWithPhysics(block, material, applyPhysics)}</li>
 *   <li>{@code Block.setBlockData(BlockData)} → {@code FoliaPatcher.safeSetBlockData(block, data)}</li>
 *   <li>{@code Block.setBlockData(BlockData, boolean)} → {@code FoliaPatcher.safeSetBlockData(block, data, applyPhysics)}</li>
 *   <li>{@code Block.breakNaturally()} → {@code FoliaPatcher.safeBreakNaturally(block)}</li>
 *   <li>{@code Block.breakNaturally(ItemStack)} → {@code FoliaPatcher.safeBreakNaturally(block, tool)}</li>
 *   <li>{@code Block.applyBoneMeal(BlockFace)} → {@code FoliaPatcher.safeApplyBoneMeal(block, face)}</li>
 * </ul>
 * </p>
 */
public final class ThreadSafetyTransformer implements ClassTransformer, Opcodes {

    /** 変換対象の Block クラス内部名 */
    private static final String BLOCK_OWNER = "org/bukkit/block/Block";

    /** FoliaPatcher の内部名 */
    private static final String PATCHER_OWNER = "com/patch/foliaphantom/patcher/FoliaPatcher";

    /** 変換対象メソッド名と対応する FoliaPatcher メソッドのマッピング */
    private static final MethodMapping[] METHOD_MAPPINGS = {
        // setType は引数数で分岐するため特別処理
        new MethodMapping("setType", null, false),
        // setBlockData も引数数で分岐
        new MethodMapping("setBlockData", null, false),
        // breakNaturally: 引数なし版と ItemStack版
        new MethodMapping("breakNaturally", "safeBreakNaturally", false),
        // applyBoneMeal: BlockFace 引数
        new MethodMapping("applyBoneMeal", "safeApplyBoneMeal", false),
    };

    /** setType の記述子 */
    private static final String SET_TYPE_1_DESC =
            "(Lorg/bukkit/block/Block;Lorg/bukkit/Material;)V";
    private static final String SET_TYPE_2_DESC =
            "(Lorg/bukkit/block/Block;Lorg/bukkit/Material;Z)V";

    /** setBlockData の記述子 */
    private static final String SET_BLOCK_DATA_1_DESC =
            "(Lorg/bukkit/block/Block;Lorg/bukkit/block/data/BlockData;)V";
    private static final String SET_BLOCK_DATA_2_DESC =
            "(Lorg/bukkit/block/Block;Lorg/bukkit/block/data/BlockData;Z)V";

    /** breakNaturally の記述子（引数なし） */
    private static final String BREAK_NATURALLY_0_DESC =
            "(Lorg/bukkit/block/Block;)Z";
    /** breakNaturally の記述子（ItemStack版） */
    private static final String BREAK_NATURALLY_1_DESC =
            "(Lorg/bukkit/block/Block;Lorg/bukkit/inventory/ItemStack;)Z";

    /** applyBoneMeal の記述子 */
    private static final String APPLY_BONE_MEAL_DESC =
            "(Lorg/bukkit/block/Block;Lorg/bukkit/block/BlockFace;)Z";

    /**
     * クラスノード内の全メソッドを走査し、
     * Block の書き込み操作呼び出しを FoliaPatcher のラッパーに置き換える。
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
     * 単一メソッド内の Block 呼び出しを置き換える。
     *
     * @param method 変換対象のメソッドノード
     */
    private void transformMethod(MethodNode method) {
        AbstractInsnNode[] insns = method.instructions.toArray();
        for (int i = 0; i < insns.length; i++) {
            if (insns[i] instanceof MethodInsnNode methodInsn) {
                replaceIfBlockCall(methodInsn);
            }
        }
    }

    /**
     * Block の呼び出しであれば適切なラッパーに置き換える。
     */
    private void replaceIfBlockCall(MethodInsnNode methodInsn) {
        if (!BLOCK_OWNER.equals(methodInsn.owner)) {
            return;
        }
        String name = methodInsn.name;
        int argCount = getArgumentCount(methodInsn.desc);

        // setType(Material)
        if ("setType".equals(name) && argCount == 1) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = "safeSetType";
            methodInsn.desc = SET_TYPE_1_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        // setType(Material, boolean)
        if ("setType".equals(name) && argCount == 2) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = "safeSetTypeWithPhysics";
            methodInsn.desc = SET_TYPE_2_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        // setBlockData(BlockData)
        if ("setBlockData".equals(name) && argCount == 1) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = "safeSetBlockData";
            methodInsn.desc = SET_BLOCK_DATA_1_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        // setBlockData(BlockData, boolean)
        if ("setBlockData".equals(name) && argCount == 2) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = "safeSetBlockData";
            methodInsn.desc = SET_BLOCK_DATA_2_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        // breakNaturally()
        if ("breakNaturally".equals(name) && argCount == 0) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = "safeBreakNaturally";
            methodInsn.desc = BREAK_NATURALLY_0_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        // breakNaturally(ItemStack)
        if ("breakNaturally".equals(name) && argCount == 1) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = "safeBreakNaturally";
            methodInsn.desc = BREAK_NATURALLY_1_DESC;
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        // applyBoneMeal(BlockFace)
        if ("applyBoneMeal".equals(name)) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = "safeApplyBoneMeal";
            methodInsn.desc = APPLY_BONE_MEAL_DESC;
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

    /** メソッドマッピングを保持する内部レコード */
    private record MethodMapping(String originalName, String patcherName,
                                 boolean isOverloaded) {}
}
