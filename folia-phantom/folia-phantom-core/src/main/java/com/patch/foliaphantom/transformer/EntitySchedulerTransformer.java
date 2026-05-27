package com.patch.foliaphantom.transformer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entity スケジューラに関するトランスフォーマー（拡張用スタブ）。
 *
 * <p>現在は予約済みトランスフォーマーとして存在し、
 * 実際の変換処理は行わない。将来の Folia API 変更や
 * Entity スケジューリング要件に応じて実装を追加する。</p>
 *
 * <p>本クラスはトランスフォーマーチェーンの3番目に配置され、
 * 既存のプラグインに対する影響を最小限に抑えつつ、
 * 拡張ポイントとして機能する。</p>
 */
public final class EntitySchedulerTransformer implements ClassTransformer {

    /** ロガーインスタンス */
    private static final Logger log = LoggerFactory.getLogger(EntitySchedulerTransformer.class);

    /**
     * 変換処理（現在はパススルー）。
     *
     * <p>エンティティスケジューラの変換が必要になった場合、
     * ここに変換ロジックを実装する。現時点では
     * クラスノードをそのまま ClassWriter に書き出す。</p>
     *
     * @param classNode  変換対象のクラスノード
     * @param className  クラス内部名
     * @param writer     出力先の ClassWriter
     * @return 変換後のバイト配列（現状は未変換）
     */
    @Override
    public byte[] transform(ClassNode classNode, String className, ClassWriter writer) {
        log.debug("EntitySchedulerTransformer: pass-through for class '{}'", className);
        classNode.accept(writer);
        return writer.toByteArray();
    }
}
