package com.patch.foliaphantom.gui;

/**
 * JavaFX アプリケーションのランチャークラス。
 *
 * <p>JavaFX 11+ ではモジュールパスの問題を回避するため、
 * メインクラスとは別にランチャークラスを用意し、
 * このクラスから {@link FoliaPhantomApp} を起動する。</p>
 *
 * <p>使用法:
 * <pre>
 *   java -jar folia-phantom-gui-1.0.0.jar
 * </pre>
 * </p>
 */
public final class Launcher {

    private Launcher() {
        throw new UnsupportedOperationException("Launcher class");
    }

    /**
     * エントリポイント。JavaFX アプリケーションを起動する。
     *
     * @param args コマンドライン引数（現在は未使用）
     */
    public static void main(String[] args) {
        FoliaPhantomApp.main(args);
    }
}
