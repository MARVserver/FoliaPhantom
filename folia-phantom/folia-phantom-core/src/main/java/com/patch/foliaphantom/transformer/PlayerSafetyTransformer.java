package com.patch.foliaphantom.transformer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.util.List;

/**
 * {@code Player} の書き込み操作呼び出しを
 * スレッドセーフなラッパーに置き換えるトランスフォーマー。
 *
 * <p>Folia ではプレイヤー操作はそのプレイヤーエンティティを所有する
 * リージョンのスレッド上で行う必要がある。本トランスフォーマーは
 * Player の書き込み操作を {@code FoliaPatcher} のラッパーに置き換え、
 * {@code EntityScheduler} 経由で正しいスレッドにルーティングする。</p>
 *
 * <p>変換例:
 * <pre>
 *   player.openInventory(inventory);
 *     → FoliaPatcher.safeOpenInventory(player, inventory);
 *
 *   player.closeInventory();
 *     → FoliaPatcher.safeCloseInventory(player);
 *
 *   player.kickPlayer(message);
 *     → FoliaPatcher.safeKickPlayer(player, message);
 *
 *   player.setGameMode(GameMode.SURVIVAL);
 *     → FoliaPatcher.safeSetGameMode(player, GameMode.SURVIVAL);
 * </pre>
 * </p>
 */
public final class PlayerSafetyTransformer implements ClassTransformer, Opcodes {

    /** Player クラスの内部名 */
    private static final String PLAYER_OWNER = "org/bukkit/entity/Player";

    /** FoliaPatcher の内部名 */
    private static final String PATCHER_OWNER = "com/patch/foliaphantom/patcher/FoliaPatcher";

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
                replacePlayerCall(methodInsn);
            }
        }
    }

    private void replacePlayerCall(MethodInsnNode methodInsn) {
        if (!PLAYER_OWNER.equals(methodInsn.owner)) {
            return;
        }
        String name = methodInsn.name;
        int argCount = getArgumentCount(methodInsn.desc);

        // Player.openInventory(Inventory) → safeOpenInventory(Player, Inventory)
        if ("openInventory".equals(name) && argCount == 1) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = "safeOpenInventory";
            methodInsn.desc = "(Lorg/bukkit/entity/Player;Lorg/bukkit/inventory/Inventory;)Lorg/bukkit/inventory/InventoryView;";
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        // Player.closeInventory() → safeCloseInventory(Player)
        if ("closeInventory".equals(name)) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = "safeCloseInventory";
            methodInsn.desc = "(Lorg/bukkit/entity/Player;)V";
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        // Player.kickPlayer(String) → safeKickPlayer(Player, String)
        if ("kickPlayer".equals(name) && argCount == 1) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = "safeKickPlayer";
            methodInsn.desc = "(Lorg/bukkit/entity/Player;Ljava/lang/String;)V";
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        // Player.setGameMode(GameMode) → safeSetGameMode(Player, GameMode)
        if ("setGameMode".equals(name)) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = "safeSetGameMode";
            methodInsn.desc = "(Lorg/bukkit/entity/Player;Lorg/bukkit/GameMode;)V";
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
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
