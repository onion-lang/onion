# コンパイラアーキテクチャ

Onion コンパイラは、ソースコードを JVM バイトコードに変換するマルチフェーズパイプラインコンパイラです。

## 概要

```
ソースコード (.on ファイル)
    ↓
[1] パース (JavaCC) → 型なし AST
    ↓
[2] 書き換え → 正規化された型なし AST
    ↓
[3] 型付け → Typed AST
    ↓
[4] TailCallOptimization → Typed AST
    ↓
[5] MutualRecursionOptimization → Typed AST
    ↓
[6] TypedAstCodeGeneration (ASM) → JVM バイトコード (.class ファイル)
```

## 実装

- **言語**: Scala 3.3.7
- **パーサージェネレータ**: JavaCC 5.0
- **バイトコードライブラリ**: ASM 9.8
- **ビルドツール**: SBT
- **JVM ターゲット**: Java 17+

## フェーズ 1: パース

**ファイル**: `src/main/scala/onion/compiler/Parsing.scala`

### 文法

パーサー文法は JavaCC 構文で `grammar/JJOnionParser.jj` に定義されています。

### 処理

1. 文法から JavaCC が `JJOnionParser.java` を生成
2. ソースコードがトークン化されパースされる
3. `ASTBuilder` が型なし抽象構文木を構築
4. パーサーエラーが収集・報告される

### 出力

`src/main/scala/onion/compiler/AST.scala` で定義される型なし AST:

- `ClassDef` - クラス定義
- `MethodDef` - メソッド定義
- `VarDef` - 変数宣言
- 式: `BinaryOp`、`MethodCall`、`Literal` など

## フェーズ 2: 書き換え

**ファイル**: `src/main/scala/onion/compiler/Rewriting.scala`

### 目的

型なし AST を変換・正規化します。

- 複雑な構文を脱糖
- 式を正規化
- 型チェックの準備

### 例

- 高レベル構文をより単純な形に変換
- 構文糖衣を展開
- 演算子の優先順位を正規化

## フェーズ 3: 型付け

**ファサード**: `src/main/scala/onion/compiler/Typing.scala`

### 責務

1. **型推論**: 式の型を決定
2. **型検証**: 型の互換性を確認
3. **名前解決**: 変数名やメソッド名を解決
4. **シンボルテーブル**: シンボル情報を構築・管理

### 内部構造

`Typing.scala` は、4つのパスのオーケストレーション層です。

1. `TypingHeaderPass` - クラス、インポート、トップレベルコンテナを登録
2. `TypingOutlinePass` - メンバー、スーパータイプ、エイリアスのアウトラインを解決
3. `TypingBodyPass` - 実行可能な本体と式を型チェック
4. `TypingDuplicationPass` - 重複メンバーと消去後のシグネチャを検証

サポートヘルパーは `src/main/scala/onion/compiler/typing/` にあります。

- `NameResolution.scala` - 型パラメータ、型エイリアス、インポート名の解決
- `MethodResolution.scala` - オーバーロードとジェネリックメソッドの選択
- `TypeSubstitution.scala` と `GenericMethodTypeArguments.scala` - ジェネリック特殊化
- `ControlExpressionTyping.scala`、`StatementTyping.scala`、`MethodCallTyping.scala` - 本体レベルの型付けヘルパー

### サポートコンポーネント

**シンボルテーブル** (`src/main/scala/onion/compiler/`):
- `ClassTable.scala` - クラスのシンボルテーブル
- `LocalContext.scala` - ローカル変数環境
- `Symbol.scala` - シンボル定義

**エラー報告**:
- `SemanticError.scala` - 型エラー定義
- `SemanticErrorReporter.scala` - エラー収集

**型システム**:
- `BasicType` - プリミティブ型（Int、Long、Double など）
- `ClassType` - 参照型
- `ArrayType` - 配列型
- `NullType` - null リテラル型

### 処理

1. クラス定義からクラステーブルを構築
2. インポートを処理し外部型を解決
3. メソッド本体を型チェック
4. 代入とメソッド呼び出しを検証
5. 可能な場所で型を推論
6. 型エラーを報告

### 出力

`src/main/scala/onion/compiler/TypedAST.scala` で定義される Typed AST:

- 各ノードに型情報が付く
- 名前はシンボルに解決済み
- コード生成の準備完了

## フェーズ 4: 末尾呼び出し最適化

**ファイル**: `src/main/scala/onion/compiler/optimization/TailCallOptimization.scala`

このフェーズは、自己末尾再帰の private メソッドを Typed AST 内の明示的なループに書き換えます。

## フェーズ 5: 相互再帰最適化

**ファイル**: `src/main/scala/onion/compiler/optimization/MutualRecursionOptimization.scala`

このフェーズは、`@TailRecursive` 相互再帰グループをステートマシンスタイルのメソッドに低減します。

## フェーズ 6: コード生成

**プライマリ境界**: `src/main/scala/onion/compiler/codegen/TypedAstCodeGeneration.scala`  
**ASM バックエンド境界**: `src/main/scala/onion/compiler/backend/asm/AsmBackend.scala`  
**レガシー実装本体**: `src/main/scala/onion/compiler/backend/asm/AsmCodeGeneration.scala`

### ASM ライブラリ

バイトコード生成に ASM ライブラリを使用します。
- 型安全なバイトコード出力
- スタックフレーム管理
- ローカル変数の追跡

### サポートコンポーネント

**バイトコードユーティリティ** (`src/main/scala/onion/compiler/backend/asm/`):
- `MethodEmitter.scala` - JVM メソッド生成
- `LocalVarContext.scala` - ローカル変数管理
- `AsmUtil.scala` - ASM ヘルパー関数

**ビジターパターン**:
- `AsmCodeGenerationVisitor.scala` - コード生成のための AST 走査

メインパイプラインは現在 `TypedAstCodeGeneration` をターゲットとし、`backend.asm.AsmBackend` に委譲します。`TypedGenerating.scala` は `codegen.legacy.TypedGeneratingBridge` 上の公開ファサードとして残っていますが、プライマリなコード生成エントリーポイントではなくなりました。

### 出力

`CompiledClass` オブジェクトを含む:
- クラス名
- JVM バイトコード（バイト配列）
- `.class` ファイルに書き出し可能、またはメモリ内にロード可能

## コンパイラのオーケストレーション

**ファサード**: `src/main/scala/onion/compiler/OnionCompiler.scala`  
**パイプライン**: `src/main/scala/onion/compiler/pipeline/CompilerPipeline.scala`

`OnionCompiler` は意図的に薄くなっています。フェーズ実行を `CompilerPipeline` に委譲し、例外マッピングを処理し、タイミング、診断、デバッグ成果物が必要なツール向けに `compileDetailed` を公開します。

### プロファイリング

コンパイルプロファイリングは以下で制御されます。

- `--verbose` - 人間が読めるフェーズタイミング
- `--profile-compile` - 構造化プロファイルの出力
- `--profile-format text|json`
- `--profile-output stderr|stdout|<path>`

プロファイルには以下が含まれます。

- 総 wall-clock 時間
- フェーズごとの wall-clock 時間
- ソース数
- クラスパスエントリ数
- 生成クラス数

### コンパイル結果

`CompilationOutcome` を返します。
- `Success(classes: Seq[CompiledClass])` - コンパイル成功
- `Failure(errors: Seq[CompileError])` - コンパイル失敗

## エントリーポイント

### CompilerFrontend

**ファイル**: `src/main/scala/onion/tools/CompilerFrontend.scala`

`onionc` のコマンドラインインターフェース:
- コマンドライン引数をパース
- コンパイラを呼び出す
- `.class` ファイルをディスクに書き出す

### ScriptRunner

**ファイル**: `src/main/scala/onion/tools/ScriptRunner.scala`

`onion` のコマンドラインインターフェース:
- ソースファイルをメモリにコンパイル
- カスタムクラスローダーでクラスをロード
- main メソッドまたはトップレベルコードを実行

### REPL

**ファイル**: `src/main/scala/onion/tools/Repl.scala`

推奨される REPL エントリーポイントは `sbt repl`、`onion repl`、`onion-repl` です。

## クラスローディング

**ファイル**: `src/main/scala/onion/compiler/OnionClassLoader.scala`

メモリ内コンパイル用のカスタムクラスローダー:
- バイトコードを直接受け取る
- 実行時にクラスを定義
- スクリプト実行をサポート

## エラー処理

### コンパイルエラー

**型**:
- `ParseError` - 構文エラー
- `SemanticError` - 型エラー
- `CompileError` - 一般的なコンパイルエラー

**報告**:
- `CompilationReporter` - エラーの整形と表示
- ソース位置（ファイル、行、列）を含む
- エラー周辺のコンテキストを表示

### エラーの例

```
Error: Type mismatch
  Expected: Int
  Found: String
  at MyProgram.on:15
    val x: Int = "hello"
                 ^^^^^^^
```

## ユーティリティ

### Toolbox

**場所**: `src/main/scala/onion/compiler/toolbox/`

ヘルパーユーティリティ:
- `Classes.scala` - クラス操作
- `Paths.scala` - パス処理
- `SymbolGenerator.scala` - 一意なシンボル生成
- `MethodInvoker.scala` - リフレクションベースの呼び出し
- `Boxing.scala` - プリミティブ/オブジェクトのボックス化

### Environment

**場所**: `src/main/scala/onion/compiler/environment/`

型解決:
- `ClassFileTable.scala` - ロード済みクラスのキャッシュ
- `AsmRefs.scala` - ASM 型参照
- `ReflectionRefs.scala` - リフレクションベースの型

## ビルドプロセス

### SBT 設定

**ファイル**: `build.sbt`

- バージョン: sbt-dynver により直近の git タグから導出
- Scala バージョン: 3.3.7
- メインクラス: `onion.tools.CompilerFrontend`
- REPL タスク: `sbt repl`
- ベンチマークタスク: `sbt bench`

### パーサー生成

文法が変更されると自動的にパーサーを再生成します。

```scala
sourceGenerators in Compile += Def.task {
  val grammar = file("grammar/JJOnionParser.jj")
  val parser = sourceManaged.value / "java" / "onion" / "compiler" / "parser" / "JJOnionParser.java"

  if (grammar.lastModified() > parser.lastModified()) {
    javacc(classpath.value, sourceManaged.value / "java", streams.value.log)
  }
}
```

### アセンブリ

スタンドアロン JAR を作成:

```bash
sbt assembly
# onion.jar を作成
```

### ディストリビューション

配布用 ZIP を作成:

```bash
sbt dist
# 以下を含む target/onion-dist.zip を作成:
#   - onion.jar
#   - lib/ (依存関係)
#   - bin/ (スクリプト)
#   - run/ (サンプル)
```

## テスト

**フレームワーク**: ScalaTest 3.2.19

**場所**: `src/test/scala/onion/compiler/tools/`

**基底クラス**: `AbstractShellSpec`
- Onion コードを実行
- 出力をキャプチャ
- 結果を検証

**テスト例**:
- `HelloWorldSpec` - 基本的な出力
- `FactorialSpec` - 再帰
- `StringInterpolationSpec` - 文字列機能

## 開発ワークフロー

1. **文法を修正**: `grammar/JJOnionParser.jj` を編集
2. **コンパイル**: `sbt compile` を実行（パーサーを再生成）
3. **テスト**: `sbt test` を実行
4. **繰り返し**

## パフォーマンス特性

- **パース**: O(n)、n = ソースサイズ
- **型チェック**: O(n × m)、n = ノード数、m = シンボル数
- **コード生成**: O(n)、n = Typed AST ノード数

## 今後の強化

潜在的な改善:
- インクリメンタルコンパイル
- より良いエラーメッセージ
- 型推論の改善
- 最適化パス
- デバッグサポート

## 次のステップ

- [言語仕様](specification.md) - 言語の詳細
- [ソースからビルド](../contributing/building.md) - 開発者ガイド
- [貢献](../contributing/development.md) - 貢献方法
