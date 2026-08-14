package com.patch.foliaphantom.transformer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

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
 *
 * <p>{@code super.runTask*()} は JVM 上で {@code INVOKESPECIAL} になる。通常の
 * {@code INVOKEVIRTUAL} 呼び出しと同様に変換しつつ、元の {@code BukkitRunnable}
 * が保持していたタスク状態をサブクラス側に保存する。これにより eco の
 * {@code EcoRunnableTask} のようなラッパーでも {@code cancel()},
 * {@code isCancelled()}, {@code getTaskId()} が legacy {@code BukkitScheduler}
 * に戻らず動作する。</p>
 */
public final class SchedulerClassTransformer implements ClassTransformer, Opcodes {

    /** BukkitScheduler の内部名 */
    private static final String BUKKIT_SCHEDULER_OWNER = "org/bukkit/scheduler/BukkitScheduler";

    /** BukkitRunnable の内部名 */
    private static final String BUKKIT_RUNNABLE_OWNER = "org/bukkit/scheduler/BukkitRunnable";

    /** BukkitTask の内部名 */
    private static final String BUKKIT_TASK_OWNER = "org/bukkit/scheduler/BukkitTask";

    /** BukkitTask の descriptor */
    private static final String BUKKIT_TASK_DESC = "Lorg/bukkit/scheduler/BukkitTask;";

    /** super.runTask*() 変換時にサブクラスへ追加するタスク状態フィールド */
    private static final String RUNNABLE_TASK_FIELD = "__pastaBukkitTask";

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
        "scheduleAsyncDelayedTask",
        "scheduleSyncRepeatingTask",
        "scheduleAsyncRepeatingTask",
        "cancelTask",
        "cancelTasks",
        "isCurrentlyRunning",
        "isQueued",
        "getPendingTasks",
        "getActiveWorkers",
        "callSyncMethod"
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

        boolean needsRunnableState = false;
        for (MethodNode method : methods) {
            if (transformMethod(classNode, method)) {
                needsRunnableState = true;
            }
        }
        if (needsRunnableState) {
            ensureRunnableStateMembers(classNode);
        }

        classNode.accept(writer);
        return writer.toByteArray();
    }

    /**
     * 単一メソッド内のスケジューラ呼び出しを置き換える。
     *
     * @param classNode 変換対象クラス
     * @param method 変換対象のメソッドノード
     * @return BukkitRunnable の super 呼び出しを変換した場合 true
     */
    private boolean transformMethod(ClassNode classNode, MethodNode method) {
        boolean transformedSuperCall = false;
        AbstractInsnNode[] insns = method.instructions.toArray();
        for (AbstractInsnNode insn : insns) {
            if (insn instanceof MethodInsnNode methodInsn) {
                replaceSchedulerCall(methodInsn);
                if (replaceBukkitRunnableCall(method, methodInsn, classNode.name)) {
                    transformedSuperCall = true;
                }
            }
        }
        return transformedSuperCall;
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
     * <p>通常の {@code INVOKEVIRTUAL} では BukkitRunnable のサブクラス経由の
     * 呼び出しで owner がサブクラス名になるため owner チェックは行わない。
     * 代わりに BukkitRunnable のメソッドシグネチャ先頭が
     * {@code (Lorg/bukkit/plugin/Plugin;} であることを確認して偽陽性を排除する。</p>
     *
     * <p>{@code INVOKESPECIAL} は {@code super.runTask*()} のときだけ対象とし、
     * owner が {@code BukkitRunnable} と完全一致する場合に限定する。変換後の
     * BukkitTask はサブクラスへ追加する synthetic field に保存する。</p>
     *
     * @param method 変換対象メソッド
     * @param methodInsn 検査対象のメソッド呼び出しノード
     * @param className 現在のクラス内部名
     * @return super 呼び出しを変換した場合 true
     */
    private boolean replaceBukkitRunnableCall(
            MethodNode method, MethodInsnNode methodInsn, String className) {
        int originalOpcode = methodInsn.getOpcode();
        boolean isVirtualCall = originalOpcode == INVOKEVIRTUAL;
        boolean isSuperCall = originalOpcode == INVOKESPECIAL
                && BUKKIT_RUNNABLE_OWNER.equals(methodInsn.owner);

        if (!isVirtualCall && !isSuperCall) {
            return false;
        }
        if (!isRunnableInstanceMethod(methodInsn.name)) {
            return false;
        }
        // BukkitRunnable メソッドの第一引数は必ず Plugin。
        // 別クラスに同名メソッドがある場合の偽陽性を排除する。
        if (!methodInsn.desc.startsWith("(Lorg/bukkit/plugin/Plugin;")) {
            return false;
        }

        // desc の先頭に Runnable 引数を追加: (Lplugin;...) → (Lrunnable;Lplugin;...)
        String newName = methodInsn.name + "_onRunnable";
        String newDesc = "(Ljava/lang/Runnable;" + methodInsn.desc.substring(1);
        methodInsn.owner = PATCHER_OWNER;
        methodInsn.name = newName;
        methodInsn.desc = newDesc;
        methodInsn.setOpcode(INVOKESTATIC);
        methodInsn.itf = false;

        if (isSuperCall) {
            storeSuperCallTask(method, methodInsn, className);
        }
        return isSuperCall;
    }

    /**
     * 変換済み super.runTask*() の戻り値をサブクラス側へ保存する。
     * 呼び出し結果は元コード向けにスタック上へ残す。
     */
    private void storeSuperCallTask(MethodNode method, MethodInsnNode methodInsn, String className) {
        InsnList store = new InsnList();
        store.add(new InsnNode(DUP));
        store.add(new VarInsnNode(ALOAD, 0));
        store.add(new InsnNode(SWAP));
        store.add(new FieldInsnNode(
                PUTFIELD, className, RUNNABLE_TASK_FIELD, BUKKIT_TASK_DESC));
        method.instructions.insert(methodInsn, store);
    }

    /**
     * BukkitRunnable が通常提供するタスク状態 API をサブクラス側で再現する。
     *
     * <p>元の BukkitRunnable.cancel() は Bukkit.getScheduler().cancelTask(...) を
     * 呼ぶため Folia では利用できない。FoliaPatcher が返す BukkitTask を直接
     * 操作する override を注入する。</p>
     */
    private void ensureRunnableStateMembers(ClassNode classNode) {
        if (!hasField(classNode, RUNNABLE_TASK_FIELD)) {
            classNode.fields.add(new FieldNode(
                    ACC_PRIVATE | ACC_TRANSIENT | ACC_SYNTHETIC,
                    RUNNABLE_TASK_FIELD,
                    BUKKIT_TASK_DESC,
                    null,
                    null));
        }
        if (!hasMethod(classNode, "cancel", "()V")) {
            classNode.methods.add(createCancelMethod(classNode.name));
        }
        if (!hasMethod(classNode, "isCancelled", "()Z")) {
            classNode.methods.add(createTaskStateMethod(
                    classNode.name, "isCancelled", "()Z", IRETURN));
        }
        if (!hasMethod(classNode, "getTaskId", "()I")) {
            classNode.methods.add(createTaskStateMethod(
                    classNode.name, "getTaskId", "()I", IRETURN));
        }
    }

    private MethodNode createCancelMethod(String className) {
        MethodNode method = new MethodNode(
                ASM9,
                ACC_PUBLIC | ACC_SYNCHRONIZED | ACC_SYNTHETIC,
                "cancel",
                "()V",
                null,
                null);
        appendLoadedTaskOrThrow(method.instructions, className);
        method.instructions.add(new MethodInsnNode(
                INVOKEINTERFACE, BUKKIT_TASK_OWNER, "cancel", "()V", true));
        method.instructions.add(new InsnNode(RETURN));
        return method;
    }

    private MethodNode createTaskStateMethod(
            String className, String name, String descriptor, int returnOpcode) {
        MethodNode method = new MethodNode(
                ASM9,
                ACC_PUBLIC | ACC_SYNCHRONIZED | ACC_SYNTHETIC,
                name,
                descriptor,
                null,
                null);
        appendLoadedTaskOrThrow(method.instructions, className);
        method.instructions.add(new MethodInsnNode(
                INVOKEINTERFACE, BUKKIT_TASK_OWNER, name, descriptor, true));
        method.instructions.add(new InsnNode(returnOpcode));
        return method;
    }

    /** スタックへ保存済み BukkitTask を積み、未スケジュールなら例外を投げる。 */
    private void appendLoadedTaskOrThrow(InsnList instructions, String className) {
        LabelNode scheduled = new LabelNode();
        instructions.add(new VarInsnNode(ALOAD, 0));
        instructions.add(new FieldInsnNode(
                GETFIELD, className, RUNNABLE_TASK_FIELD, BUKKIT_TASK_DESC));
        instructions.add(new InsnNode(DUP));
        instructions.add(new JumpInsnNode(IFNONNULL, scheduled));
        instructions.add(new InsnNode(POP));
        instructions.add(new TypeInsnNode(NEW, "java/lang/IllegalStateException"));
        instructions.add(new InsnNode(DUP));
        instructions.add(new LdcInsnNode("Not scheduled yet"));
        instructions.add(new MethodInsnNode(
                INVOKESPECIAL,
                "java/lang/IllegalStateException",
                "<init>",
                "(Ljava/lang/String;)V",
                false));
        instructions.add(new InsnNode(ATHROW));
        instructions.add(scheduled);
    }

    private static boolean hasField(ClassNode classNode, String name) {
        for (FieldNode field : classNode.fields) {
            if (name.equals(field.name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMethod(ClassNode classNode, String name, String descriptor) {
        for (MethodNode method : classNode.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                return true;
            }
        }
        return false;
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
