# 開発ガイド

Onionプログラミング言語への貢献ガイドです。

## はじめに

### 前提条件

- JDK 17 以降
- SBT（Scala Build Tool）
- Git
- テキストエディタまたはIDE（IntelliJ IDEA推奨）

### リポジトリのクローン

```bash
git clone https://github.com/onion-lang/onion.git
cd onion
```

### プロジェクトのビルド

```bash
sbt compile
```

これにより以下が行われます。
- 依存関係のダウンロード
- JavaCC文法からのパーサー生成
- すべてのScalaおよびJavaソースファイルのコンパイル

## プロジェクト構成

```
onion/
├── build.sbt                 # SBTビルド設定
├── grammar/
│   └── JJOnionParser.jj     # JavaCCパーサー文法
├── src/
│   ├── main/
│   │   ├── scala/           # Scalaソースコード
│   │   │   └── onion/
│   │   │       ├── compiler/        # コンパイラフェーズ
│   │   │       ├── tools/           # CLIツール
│   │   │       └── ...
│   │   └── java/            # Javaランタイムライブラリ
│   │       └── onion/
│   │           ├── Function0.java - Function10.java
│   │           ├── IO.java
│   │           └── ...
│   └── test/
│       ├── scala/           # テストスイート
│       └── run/             # サンプルプログラム
├── run/                     # Onionのサンプルプログラム
└── docs/                    # ドキュメント
```

## 開発ワークフロー

### 1. 機能ブランチの作成

```bash
git checkout -b feature/my-new-feature
```

### 2. 変更を加える

好みのエディタでソースファイルを編集します。

### 3. コンパイル

```bash
sbt compile
```

### 4. テストを実行

```bash
sbt test
```

### 5. 手動での確認

```bash
# サンプルを実行
sbt 'runScript run/Hello.on'

# またはコンパイラを使う
sbt compile
sbt 'run-main onion.tools.CompilerFrontend run/Hello.on'
java -cp . Hello
```

### 6. コードの整形

Scalaのスタイル規約に従います。
- インデントは2スペース
- 行の長さ制限: 120文字
- 意味のある変数名を使う

### 7. 変更をコミット

```bash
git add .
git commit -m "Add feature: description of change"
```

### 8. プッシュしてプルリクエストを作成

```bash
git push origin feature/my-new-feature
```

その後、GitHubでプルリクエストを作成します。

## 一般的な開発タスク

### パーサーの修正

`grammar/JJOnionParser.jj` を編集してから:

```bash
sbt clean compile
```

パーサーは自動的に再生成されます。

### 言語機能の追加

1. `JJOnionParser.jj` の文法を更新
2. `AST.scala` のASTを更新
3. `Typing.scala` の型チェックを更新
4. バイトコード境界を `codegen/TypedAstCodeGeneration.scala` / `backend/asm/AsmBackend.scala` で更新
5. 必要に応じて、従来の実装本体 `backend/asm/AsmCodeGeneration.scala` を更新
6. テストを追加

### テストの追加

`src/test/scala/onion/compiler/tools/` に新しいテストを作成します。

```scala
package onion.compiler.tools

class MyFeatureSpec extends AbstractShellSpec {
  "MyFeature" should "work correctly" in {
    val source = """
      |// Your test code here
      |println("Test")
    """.stripMargin

    val result = runShell(source)
    result should include("Test")
  }
}
```

テストの実行:

```bash
sbt 'testOnly *MyFeatureSpec'
```

### コンパイラのデバッグ

print文の追加またはデバッガの使用:

```scala
// コンパイラコード内
println(s"Debug: AST = $ast")
```

またはIntelliJ IDEAのデバッガを使います。
1. ブレークポイントを設定
2. テストをデバッグモードで実行

## コード構成

### コンパイラフェーズ

**パース** (`Parsing.scala`):
- コンパイルのエントリーポイント
- JavaCC生成パーサーを使用
- 型なしASTを生成

**書き換え** (`Rewriting.scala`):
- ASTの正規化
- 複雑な構文を脱糖

**型チェック** (`Typing.scala`):
- 型推論と検証
- 名前解決
- シンボルテーブル管理
- セッション状態は `typing/session/TypingSession.scala` で保持

**コード生成** (`codegen/TypedAstCodeGeneration.scala`, `backend/asm/AsmBackend.scala`):
- 型付きASTからバイトコード境界へ
- ASMベースのバイトコード生成
- JVM命令の出力

### サポートモジュール

**AST** (`AST.scala`, `TypedAST.scala`):
- 抽象構文木の定義

**シンボルテーブル** (`ClassTable.scala`, `LocalContext.scala`):
- シンボル管理
- スコープ処理

**エラー処理** (`SemanticError.scala`, `CompilationReporter.scala`):
- エラー収集
- エラー整形

## テスト戦略 {#testing-strategy}

### ユニットテスト

個別のコンポーネントをテストします。

```scala
class TypingSpec extends AnyFlatSpec with Matchers {
  "Type checker" should "infer Int type" in {
    // 型推論のテスト
  }
}
```

### 統合テスト

完全なコンパイルをテストします。

```scala
class IntegrationSpec extends AbstractShellSpec {
  "Compiler" should "compile and run program" in {
    val source = """println("Hello")"""
    val result = runShell(source)
    result should include("Hello")
  }
}
```

### 例に基づくテスト

サンプルプログラムがコンパイル・実行できることを確認します。

```bash
sbt test
# サンプルの検証も含めてすべてのテストを実行
```

## ドキュメント

### コードドキュメント

公開APIにはScalaDocを使います。

```scala
/**
 * OnionのソースコードをJVMバイトコードにコンパイルします。
 *
 * @param source ソースコード文字列
 * @param config コンパイラ設定
 * @return コンパイル結果
 */
def compile(source: String, config: CompilerConfig): CompilationOutcome = {
  // ...
}
```

### ユーザードキュメント

`docs/` ディレクトリのドキュメントを更新します。
- Markdown形式を使用
- コード例を含める
- 例を最新の状態に保つ

## 貢献ガイドライン

### コードスタイル

- Scalaの規約に従う
- 意味のある名前を使う
- 関数は単一の責務に保つ
- 複雑なロジックにはコメントを追加

### コミットメッセージ

明確で説明的なコミットメッセージを使います。

```
Add feature: Lambda expression support

- Implement lambda syntax in parser
- Add lambda type checking
- Generate Function interface calls
- Add lambda tests
```

### プルリクエストのプロセス

1. リポジトリをフォーク
2. 機能ブランチを作成
3. テスト付きで変更を加える
4. すべてのテストが通ることを確認
5. ドキュメントを更新
6. プルリクエストを作成
7. レビュー指摘に対応

### イシュー

イシューを作成する際は:
- 説明的なタイトルを使う
- 例のコードを提供
- エラーメッセージを含める
- Onionのバージョンを明記

## パフォーマンスに関する考慮事項

- パーサー生成は遅いため、不要な再コンパイルを避ける
- 大きなファイルでは型チェックが高コストになりうる
- 可能な限りインクリメンタルコンパイルを活用する

## デバッグのヒント

### コンパイラクラッシュ

1. 失敗しているフェーズを特定
2. デバッグ出力を追加
3. AST構造を確認
4. 型情報を検証

### 型エラー

1. シンボルテーブルを確認
2. 型解決を検証
3. 型変換ルールを確認

### バイトコードの問題

1. `javap` で生成されたバイトコードを確認
2. スタックフレームの正しさを検証
3. ローカル変数のインデックスを確認

## リリースプロセス

1. `build.sbt` のバージョンを更新
2. すべてのテストを実行: `sbt test`
3. ディストリビューションを作成: `sbt dist`
4. リリースタグを作成: `git tag v0.2.0`
5. タグをプッシュ: `git push origin v0.2.0`
6. GitHubリリースを作成

詳細は [RELEASING.md](../RELEASING.md) を参照してください。

## 次のステップ

- [ソースからビルド](building.md) - 詳細なビルド手順
- [コンパイラアーキテクチャ](../../reference/compiler-architecture.md) - コンパイラの内部構造
- [言語仕様](../../reference/specification.md) - 言語の詳細
