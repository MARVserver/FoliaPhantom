---
name: java-harness
description: Use when writing or reviewing Java code. Provides comprehensive coding conventions, style rules, and architecture patterns for Java projects.
---

# Java コーディングハーネス

## 命名規則

| 要素 | 規則 | 例 |
|------|------|-----|
| クラス | パスカルケース（名詞） | `OrderService`, `UserRepository` |
| インターフェース | パスカルケース（形容詞/能力） | `Serializable`, `CrudRepository` |
| メソッド | キャメルケース（動詞） | `findById()`, `processOrder()` |
| 変数 | キャメルケース | `totalAmount`, `customerName` |
| 定数 | 大文字スネークケース | `MAX_RETRY_COUNT`, `DEFAULT_TIMEOUT` |
| パッケージ | 小文字+ドット区切り | `com.example.project.service` |
| 列挙型 | パスカルケース（要素は大文字） | `enum Status { ACTIVE, INACTIVE }` |
| 型パラメータ | 大文字1文字 | `T`, `E`, `K`, `V`, `R` |

## ファイル構成

```
src/main/java/com/example/
├── model/          # エンティティ、DTO、ValueObject
├── repository/     # データアクセス層
├── service/        # ビジネスロジック
├── controller/     # HTTPエンドポイント（Springの場合）
├── config/         # 設定クラス
├── exception/      # カスタム例外
└── util/           # ユーティリティクラス（staticメソッドのみ）
```

各ファイルは1つの公開トップレベルクラスのみ。

## フォーマット規則

- インデント: 4スペース（タブ禁止）
- 行長: 120文字上限
- 改行: LF
- 波括弧: K&Rスタイル（行末に開き括弧）
- 空行: 論理ブロック間に1行
- インポート: ワイルドカード禁止、完全修飾で列挙
  - 静的インポートは濫用禁止
- アノテーション: 別行に配置（行内アノテーションは不可）
- 1行空行: `package`宣言の後、インポートブロックの後

## 型とnull安全性

- `null`を返さない（`Optional<T>`または空コレクションを返す）
- `@Nullable` / `@NonNull` アノテーションを積極的に使用
- `Optional` をフィールド型・メソッド引数に使用しない
- `Optional.ofNullable()` でラップせず、呼び出し元で `orElse()` / `orElseThrow()` を使用
- `var` の使用は右辺が明らかな型の場合のみ（`var list = new ArrayList<String>()` はOK、`var result = method()` は推奨しない）

## クラス設計

### 不変性
- 可能な限り `final` クラス・フィールド
- 不変クラスにする: `final class` + `private final` フィールド + コンストラクタで全設定 + セッターなし
- レコード型を積極活用（`record Point(int x, int y)`）
- コレクションは防御的コピーか `Collections.unmodifiable*` で返す

### 継承よりコンポジション
- `@Override` は常に記述
- 継承より委譲を優先
- `abstract` クラスよりインターフェース + `default` メソッド
- インターフェース分離の原則（ISP）を守る：1つのインターフェースに責務が多すぎない

## メソッド設計

- 1メソッド = 1責務（15行以内推奨）
- 引数は3つまで。超える場合はBuilderまたは専用DTO
- boolean引数は避ける（代わりに enum または別メソッドに分割）
- early return でネストを浅く保つ
- Stream API + メソッド参照を優先（ループより宣言的）
- チェーンが長い場合は改行して `.` で揃える

```java
// Good
List<String> names = users.stream()
    .filter(u -> u.isActive())
    .map(User::getName)
    .sorted()
    .toList();
```

## 例外処理

- チェック例外より非チェック例外（`RuntimeException` サブクラス）
- カスタム例外は意味のある名前とメッセージ
- 例外握りつぶし禁止（catchして何もしない）
- リソースは try-with-resources
- 例外の再スロー時は原因をラップ: `throw new AppException("message", cause)`
- ビジネス例外にはエラーコードを持つことを推奨

## ロギング

- SLF4J + Logback を標準とする
- `private static final Logger log = LoggerFactory.getLogger(Xxx.class)`
- 文字列連結禁止（パラメータ化: `log.info("user={}", user.getId())`）
- ログレベル適正化: ERROR=回復不能, WARN=想定内異常, INFO=主要処理, DEBUG=開発者詳細, TRACE=細粒度

## テスト

- JUnit 5 + AssertJ + Mockito を標準とする
- テストクラス名: `XxxTest`
- テストメソッド名: `methodName_shouldExpected_whenCondition`（英語）
- 1テスト = 1アサーション（可能な限り）
- `@ParameterizedTest` を積極活用
- テストコードもプロダクションコードと同等の品質基準

## アーキテクチャ原則

- レイヤー間の依存は上位→下位の一方向（循環依存禁止）
- Service は Repository に依存してよいが、Controller は Service にのみ依存
- Model 層はどの層にも依存してはならない（POJO / POJO record）
- DIはコンストラクタインジェクション（フィールドインジェクション禁止）
- 循環参照は DTO またはイベントで解消

## 使用禁止パターン

- `System.out.println()` / `printStackTrace()`
- `Thread.sleep()` をテスト以外で使用
- `finalize()` のオーバーライド
- 生の `new Thread()`（ExecutorService を使用）
- `Vector`, `Hashtable`, `Stack`, `StringBuffer`（同期版より非同期版を優先）
- `Date`, `Calendar`（`java.time` パッケージを使用）
- `null` でのセンチネル値表現

## 推奨ライブラリ

| 用途 | ライブラリ |
|------|-----------|
| JSON | Jackson または JSON-B |
| 値検証 | Bean Validation / Jakarta Validation |
| テスト | JUnit 5 + AssertJ + Mockito |
| ビルド | Maven または Gradle（Kotlin DSL推奨） |
| HTTP | Spring WebClient（RestTemplate非推奨） |

## コードレビューチェックリスト

1. ✅ null 非考慮のパスがないか
2. ✅ 可変コレクションをそのまま公開していないか
3. ✅ 同期 / スレッドセーフが正しいか
4. ✅ リソースが適切にクローズされるか
5. ✅ 適切な例外がスローされるか（握りつぶしがないか）
6. ✅ パッケージ循環がないか
7. ✅ マジックナンバーがないか（定数化すること）
8. ✅ 過度なネストがないか（最大3段階）
9. ✅ Stream / Optional が適切に使われているか
10. ✅ テストが新しいコードをカバーしているか
