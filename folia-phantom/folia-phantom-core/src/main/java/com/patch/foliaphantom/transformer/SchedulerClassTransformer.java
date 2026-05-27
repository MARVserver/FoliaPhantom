package com.patch.foliaphantom.transformer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.util.List;

/**
 * {@code BukkitScheduler} / {@code BukkitRunnable} の呼び出しを
 * Folia スケジューラに置き換えるトランスフォーマー。
 *
 * <p>以下の変換を行う:
 * <ul>
 *   <li>{@code BukkitScheduler.runTask(...)} → {@code FoliaPatcher.runTask(...)}</li>
 *   <li>{@code BukkitScheduler.runTaskLater(...)} → {@code FoliaPatcher.runTaskLater(...)}</li>
 *   <li>{@code BukkitScheduler.runTaskTimer(...)} → {@code FoliaPatcher.runTaskTimer(...)}</li>
 *   <li>{@code BukkitScheduler.runTaskAsynchronously(...)} → {@code FoliaPatcher.runTaskAsynchronously(...)}</li>
 *   <li>{@code BukkitScheduler.runTaskLaterAsynchronously(...)} → {@code FoliaPatcher.runTaskLaterAsynchronously(...)}</li>
 *   <li>{@code BukkitScheduler.runTaskTimerAsynchronously(...)} → {@code FoliaPatcher.runTaskTimerAsynchronously(...)}</li>
 *   <li>{@code BukkitRunnable.runTask(plugin)} → {@code FoliaPatcher.runTask_onRunnable(runnable, plugin)}</li>
 *   <li>{@code BukkitRunnable.runTaskLater(plugin, delay)} → {@code FoliaPatcher.runTaskLater_onRunnable(runnable, plugin, delay)}</li>
 *   <li>{@code BukkitRunnable.runTaskTimer(plugin, delay, period)} → {@code FoliaPatcher.runTaskTimer_onRunnable(runnable, plugin, delay, period)}</li>
 *   <li>{@code BukkitRunnable.runTaskAsynchronously(plugin)} → {@code FoliaPatcher.runTaskAsynchronously_onRunnable(runnable, plugin)}</li>
 *   <li>{@code BukkitRunnable.runTaskLaterAsynchronously(plugin, delay)} → {@code FoliaPatcher.runTaskLaterAsynchronously_onRunnable(runnable, plugin, delay)}</li>
 *   <li>{@code BukkitRunnable.runTaskTimerAsynchronously(plugin, delay, period)} → {@code FoliaPatcher.runTaskTimerAsynchronously_onRunnable(runnable, plugin, delay, period)}</li>
 * </ul>
 * </p>
 */
public final class SchedulerClassTransformer implements ClassTransformer, Opcodes {

    /** BukkitScheduler の内部名 */
    private static final String BUKKIT_SCHEDULER_OWNER = "org/bukkit/scheduler/BukkitScheduler";

    /** FoliaPatcher の内部名 */
    private static final String PATCHER_OWNER = "com/patch/foliaphantom/patcher/FoliaPatcher";

    /** BukkitScheduler のメソッド名一覧（変換対象） */
    private static final String[] SCHEDULER_METHOD_NAMES = {
        "runTask",
        "runTaskLater",
        "runTaskTimer",
        "runTaskAsynchronously",
        "runTaskLaterAsynchronously",
        "runTaskTimerAsynchronously",
        "scheduleSyncDelayedTask",
        "scheduleAsyncDelayedTask"
    };

    /** BukkitRunnable インスタンスメソッド名一覧 */
    private static final String[] RUNNABLE_INSTANCE_METHOD_NAMES = {
        "runTask",
        "runTaskLater",
        "runTaskTimer",
        "runTaskAsynchronously",
        "runTaskLaterAsynchronously",
        "runTaskTimerAsynchronously"
    };

    /**
     * クラスノード内の全メソッドを走査し、
     * スケジューラ関連の呼び出しを変換する。
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
     * 単一メソッド内のスケジューラ呼び出しを置き換える。
     *
     * @param method 変換対象のメソッドノード
     */
    private void transformMethod(MethodNode method) {
        AbstractInsnNode[] insns = method.instructions.toArray();
        for (AbstractInsnNode insn : insns) {
            if (insn instanceof MethodInsnNode methodInsn) {
                replaceSchedulerCall(methodInsn);
                replaceBukkitRunnableCall(methodInsn);
            }
        }
    }

    /**
     * BukkitScheduler のメソッド呼び出しを FoliaPatcher の静的メソッドに置き換える。
     *
     * @param methodInsn 検査対象のメソッド呼び出しノード
     */
    private void replaceSchedulerCall(MethodInsnNode methodInsn) {
        if (!BUKKIT_SCHEDULER_OWNER.equals(methodInsn.owner)) {
            return;
        }
        if (!isSchedulerMethod(methodInsn.name)) {
            return;
        }
        // scheduleSyncDelayedTask と scheduleAsyncDelayedTask は特別処理
        String newName = methodInsn.name;
        if ("scheduleSyncDelayedTask".equals(newName)) {
            // レガシー: runTaskLater にマッピング、taskId を返す
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = "scheduleSyncDelayedTask";
            methodInsn.desc = "(Lorg/bukkit/scheduler/BukkitScheduler;"
                    + "Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;J)I";
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        if ("scheduleAsyncDelayedTask".equals(newName)) {
            methodInsn.owner = PATCHER_OWNER;
            methodInsn.name = "scheduleAsyncDelayedTask";
            methodInsn.desc = "(Lorg/bukkit/scheduler/BukkitScheduler;"
                    + "Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;J)I";
            methodInsn.setOpcode(INVOKESTATIC);
            methodInsn.itf = false;
            return;
        }
        // 通常のスケジューラメソッド: 第1引数に BukkitScheduler インスタンスを追加
        methodInsn.owner = PATCHER_OWNER;
        methodInsn.desc = "(Lorg/bukkit/scheduler/BukkitScheduler;" 
                + methodInsn.desc.substring(1);
        methodInsn.setOpcode(INVOKESTATIC);
        methodInsn.itf = false;
    }

    /**
     * BukkitRunnable のインスタンスメソッド呼び出しを
     * FoliaPatcher の静的メソッドに置き換える。
     *
     * @param methodInsn 検査対象のメソッド呼び出しノード
     */
    private void replaceBukkitRunnableCall(MethodInsnNode methodInsn) {
        // INVOKEVIRTUAL 以外は BukkitRunnable インスタンスメソッドではない
        if (methodInsn.getOpcode() != INVOKEVIRTUAL) {
            return;
        }
        if (!isRunnableInstanceMethod(methodInsn.name)) {
            return;
        }
        // desc の先頭に Runnable 引数を追加: (Lplugin;...) → (Lrunnable;Lplugin;...)
        String newName = methodInsn.name + "_onRunnable";
        String newDesc = "(Ljava/lang/Runnable;" + methodInsn.desc.substring(1);
        methodInsn.owner = PATCHER_OWNER;
        methodInsn.name = newName;
        methodInsn.desc = newDesc;
        methodInsn.setOpcode(INVOKESTATIC);
        methodInsn.itf = false;
    }

    /**
     * 指定されたメソッド名が BukkitScheduler の対象メソッドか判定する。
     *
     * @param name メソッド名
     * @return 対象であれば true
     */
    private static boolean isSchedulerMethod(String name) {
        for (String candidate : SCHEDULER_METHOD_NAMES) {
            if (candidate.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 指定されたメソッド名が BukkitRunnable のインスタンスメソッドか判定する。
     *
     * @param name メソッド名
     * @return 対象であれば true
     */
    private static boolean isRunnableInstanceMethod(String name) {
        for (String candidate : RUNNABLE_INSTANCE_METHOD_NAMES) {
            if (candidate.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
