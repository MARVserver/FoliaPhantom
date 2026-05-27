package com.patch.foliaphantom.transformer;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import java.util.Set;

/**
 * 高速スキャン用 AST ビジター。
 *
 * <p>クラスファイル全体をトランスフォームする前に、
 * このビジターで事前スキャンを行いパッチが必要か判定する（fast-fail）。
 * 対象のメソッド呼び出しが1つも見つからなければ、
 * フル変換をスキップして処理を高速化する。</p>
 *
 * <p>検出対象:
 * <ul>
 *   <li>{@code BukkitScheduler} の任意メソッド</li>
 *   <li>{@code BukkitRunnable} の任意メソッド（インスタンスメソッド含む）</li>
 *   <li>{@code WorldCreator} の任意メソッド</li>
 *   <li>{@code Block.setType}</li>
 *   <li>{@code Bukkit.createWorld}</li>
 *   <li>{@code Plugin.getDefaultWorldGenerator}</li>
 * </ul>
 * </p>
 */
public final class ScanningClassVisitor extends ClassVisitor {

    /** パッチが必要と判定された場合 true */
    private boolean needsPatching;

    /** 検出対象のメソッド所有者（内部名） */
    private static final Set<String> TARGET_OWNERS = Set.of(
        "org/bukkit/scheduler/BukkitScheduler",
        "org/bukkit/scheduler/BukkitRunnable",
        "org/bukkit/WorldCreator",
        "org/bukkit/block/Block",
        "org/bukkit/Bukkit",
        "org/bukkit/plugin/Plugin"
    );

    /** {@code Block.setType} のメソッド名 */
    private static final String BLOCK_SET_TYPE = "setType";

    /** {@code Bukkit.createWorld} のメソッド名 */
    private static final String BUKKIT_CREATE_WORLD = "createWorld";

    /** {@code Plugin.getDefaultWorldGenerator} のメソッド名 */
    private static final String GET_DEFAULT_WORLD_GENERATOR = "getDefaultWorldGenerator";

    /** BukkitRunnable のインスタンスメソッド一覧 */
    private static final Set<String> BUKKIT_RUNNABLE_INSTANCE_METHODS = Set.of(
        "runTask",
        "runTaskLater",
        "runTaskTimer",
        "runTaskAsynchronously",
        "runTaskLaterAsynchronously",
        "runTaskTimerAsynchronously"
    );

    /**
     * コンストラクタ。
     *
     * @param api ASM API バージョン（Opcodes.ASM9 等）
     * @param delegate 委譲先の ClassVisitor（なければ null）
     */
    public ScanningClassVisitor(int api, ClassVisitor delegate) {
        super(api, delegate);
        this.needsPatching = false;
    }

    /**
     * パッチが必要かどうかを返す。
     *
     * @return パッチ対象の呼び出しが1つでもあれば true
     */
    public boolean needsPatching() {
        return this.needsPatching;
    }

    /**
     * 各メソッドを visit するたびに呼ばれ、
     * 内部で MethodVisitor を生成する。
     *
     * @param access     メソッドのアクセスフラグ
     * @param name       メソッド名
     * @param descriptor メソッド記述子
     * @param signature  ジェネリックシグネチャ（なければ null）
     * @param exceptions  スローする例外の内部名配列
     * @return メソッドビジター（自身の scanningMethodVisitor をラップ）
     */
    @Override
    public MethodVisitor visitMethod(
            int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions) {
        // 既にパッチが確定している場合、以降のスキャンを省略
        if (this.needsPatching) {
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new ScanningMethodVisitor(api, mv);
    }

    // ---- 内部 MethodVisitor ----

    /**
     * メソッド内のメソッド呼び出しをインターセプトし、
     * 対象APIが使われているか判定する内部クラス。
     */
    private final class ScanningMethodVisitor extends MethodVisitor {

        /**
         * @param api ASM API バージョン
         * @param mv  委譲先の MethodVisitor
         */
        ScanningMethodVisitor(int api, MethodVisitor mv) {
            super(api, mv);
        }

        /**
         * メソッド呼び出し命令（INVOKEVIRTUAL, INVOKESTATIC 等）を検査する。
         *
         * @param opcode     呼び出しのopcode
         * @param owner      メソッド所有者の内部名
         * @param name       メソッド名
         * @param descriptor メソッド記述子
         * @param isInterface インターフェースメソッドか
         */
        @Override
        public void visitMethodInsn(
                int opcode,
                String owner,
                String name,
                String descriptor,
                boolean isInterface) {
            // まだパッチ判定が確定していない場合のみ検査
            if (!needsPatching && isTargetOwner(owner, name)) {
                needsPatching = true;
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        /**
         * 指定された所有者＋メソッド名がパッチ対象か判定する。
         *
         * @param owner 内部名
         * @param name  メソッド名
         * @return 対象の呼び出しであれば true
         */
        private boolean isTargetOwner(String owner, String name) {
            // 完全一致の対象所有者
            if (TARGET_OWNERS.contains(owner)) {
                return true;
            }
            // BukkitRunnable インスタンスメソッドの検出
            if (BUKKIT_RUNNABLE_INSTANCE_METHODS.contains(name)
                    && "java/lang/Runnable".equals(owner)) {
                // 注意: 実際の INVOKEVIRTUAL では owner がサブクラスになるため、
                // より精確な判定は SchedulerClassTransformer で行う
                return false;
            }
            return false;
        }
    }
}
