package com.patch.foliaphantom.transformer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/**
 * クラスファイルのバイトコード変換を行うトランスフォーマーのインターフェース。
 *
 * <p>各トランスフォーマーは特定の Bukkit API 呼び出しを検出し、
 * Folia 互換の呼び出しに置き換える責務を持つ。
 * 実装は {@link ClassNode} を操作し、変換後のバイト配列を返す。</p>
 *
 * <p>適用順序は {@link PluginPatcher} で管理され、
 * トランスフォーマーチェーンとして逐次適用される。</p>
 *
 * @see PluginPatcher
 */
public interface ClassTransformer {

    /**
     * 指定されたクラスノードに対して変換を適用する。
     *
     * @param classNode  変換対象のクラスノード（AST表現）
     * @param className  クラスの内部名（例: "com/example/MyPlugin"）
     * @param writer     出力先の ClassWriter（フラグ設定済み）
     * @return 変換後のクラスバイト配列。変換不要の場合は null を返してもよい
     */
    byte[] transform(ClassNode classNode, String className, ClassWriter writer);
}
