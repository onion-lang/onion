# CLAUDE.md (日本語版)

このファイルは、Claude Code (claude.ai/code) がこのリポジトリで作業する際のガイダンスを提供します。

## プロジェクト概要

Onionは、JVMバイトコードにコンパイルされる静的型付けのオブジェクト指向プログラミング言語です。元々はJavaで書かれていましたが、パーサー（JavaCCを使用）を除き、Scalaで書き直されています。

**構成:**
- Scala バージョン: 3.3.7
- Java バージョン: 17
- SBT バージョン: ~1.9+
- 主要な依存関係: ASM 9.8 (バイトコード), JavaCC 5.0 (パーサー), ScalaTest 3.2.19 (テスト)

## ビルドコマンド

- **プロジェクトのコンパイル**: `sbt compile`
  - `grammar/JJOnionParser.jj` が変更されると、パーサーを自動再生成
- **テストの実行**: `sbt test`
- **単一テストスイートの実行**: `sbt 'testOnly *HelloWorldSpec'`
- **依存関係を含むJARのパッケージ化**: `sbt assembly` (`onion.jar`を作成)
- **配布パッケージの作成**: `sbt dist` (target/にlib/, bin/, run/, onion.jarを含むZIPを作成)
- **Onionスクリプトの実行**: `sbt 'runScript path/to/script.on [args]'`
- **REPLの起動**: `sbt repl`
- **クリーンビルド**: `sbt clean`
- **Scalaコンソール**: `sbt console`

### コンパイラオプション (onionc/onion用)

- `-classpath <path>` - コンパイル用のクラスパスを設定
- `-encoding <encoding>` - ソースファイルのエンコーディングを設定
- `-d <dir>` - クラスファイルの出力ディレクトリを設定
- `-maxErrorReports <n>` - 報告するエラーの数を制限
- `--dump-ast` - パースされたASTを標準エラー出力に表示
- `--dump-typed-ast` - 型付けされたASTの概要を標準エラー出力に表示
- `--warn <off|on|error>` - 警告レベルを設定
- `--Wno <codes>` - 特定の警告を抑制 (例: W0001,unused-parameter)

## 高レベルアーキテクチャ

Onionコンパイラは、古典的なコンパイラアーキテクチャに従った**マルチフェーズパイプラインコンパイラ**です。

### コンパイルパイプライン

```
ソースファイル (.on)
    ↓
[1] パース (JavaCC) → 型なしAST
    ↓
[2] 書き換え → 正規化された型なしAST
    ↓
[3] 型チェック → 型付きAST
    ↓
[4] 末尾呼び出し最適化 → 最適化された型付きAST
    ↓
[5] コード生成 (ASM) → JVMバイトコード
    ↓
クラスのロードと実行
```

### コアコンパイラフェーズ

すべてのフェーズは `Processor[A, B]` トレイトを継承し、`andThen()` を使って合成できます：

1. **パース** (`src/main/scala/onion/compiler/Parsing.scala`)
   - `grammar/JJOnionParser.jj` (36KBの文法ファイル) からJavaCC生成パーサーを使用
   - パーサークラス: `JJOnionParser` (`sourceManaged/`に自動生成)
   - ソーステキスト → 型なしAST (`AST.scala`) に変換
   - `ASTBuilder.scala` を使用してパーサートークンからASTを構築

2. **書き換え** (`src/main/scala/onion/compiler/Rewriting.scala`)
   - ASTの変換と正規化
   - 複雑な構文の簡略化
   - 出力: 正規化された型なしAST

3. **型チェック** (`src/main/scala/onion/compiler/Typing.scala`, 86KB - 最大のコンポーネント)
   - 型推論と検証
   - シンボル解決と名前束縛
   - オーバーロードを伴うメソッド解決
   - アクセス制御チェック
   - 主要なサポートファイル:
     - `ClassTable.scala` - クラスシンボルテーブル
     - `LocalContext.scala` - ローカル変数環境
     - `Symbol.scala` - シンボル定義
     - `SemanticErrorReporter.scala` - エラー収集
   - 出力: 型付きAST (`TypedAST.scala`, 37KB)

4. **末尾呼び出し最適化** (`src/main/scala/onion/compiler/optimization/TailCallOptimization.scala`)
   - 末尾再帰メソッド（return位置での自己呼び出し）を検出
   - スタックオーバーフローを防ぐため、末尾再帰をループに変換
   - 戦略:
     1. メソッド開始時にパラメータをループ変数にコピー
     2. すべてのパラメータ参照をループ変数を使用するように書き換え
     3. メソッド本体を `while(true)` ループでラップ
     4. 末尾呼び出しを変数更新 + continueに置き換え
   - 深い再帰（例: 10000回以上の呼び出し）でのStackOverflowErrorを防止
   - 出力: 最適化された型付きAST

5. **コード生成** (`src/main/scala/onion/compiler/AsmCodeGeneration.scala`, 42KB)
   - **ASMベースのバイトコード生成**（現在の実装）
   - ビジターパターン: `AsmCodeGenerationVisitor.scala`
   - バイトコードユーティリティ:
     - `bytecode/MethodEmitter.scala` - JVMメソッド生成
     - `bytecode/LocalVarContext.scala` - ローカル変数追跡
     - `bytecode/AsmUtil.scala` - ASMヘルパー関数
   - 出力: `CompiledClass` オブジェクト（メモリ内またはファイル）

### 主要なアーキテクチャコンポーネント

**オーケストレーション:**
- `OnionCompiler.scala` - メインコンパイラオーケストレーター、すべてのフェーズを合成
- `CompilationOutcome` を返す（クラス付きのSuccessまたはエラー付きのFailure）

**エントリーポイント:**
- `onion.tools.CompilerFrontend` - `onionc` コマンドのCLI（.classファイルにコンパイル）
- `onion.tools.ScriptRunner` - `onion` コマンドのCLI（メモリ内でコンパイルして実行）
- `onion.tools.Shell` - 対話型REPLシェル

**型システム:**
- `BasicType` - プリミティブ (int, long, double, boolean, byte, short, char, float)
- `ClassType` - 参照型
- `ArrayType` - コンポーネント追跡付きの配列型
- `NullType` - nullリテラル型
- `OnionTypeConversion.scala` - 型変換ロジック

**シンボルテーブルと環境:**
- `AbstractTable.scala`, `OrderedTable.scala`, `MultiTable.scala` - シンボルストレージ
- `LocalBinding.scala`, `ClosureLocalBinding.scala` - 変数束縛
- `LocalFrame.scala`, `LocalScope.scala` - スコープ管理
- `environment/ClassFileTable.scala` - ロードされたクラスメタデータのキャッシュ

**エラー処理:**
- `CompileError.scala`, `SemanticError.scala` - エラー型
- `SemanticErrorReporter.scala` - エラー収集と報告
- `CompilationReporter.scala` - ユーザー向けエラーフォーマット

**ランタイムサポート:**
- `onion/Function0.java` から `Function10.java` まで - クロージャ用の関数インターフェース
- `onion/IO.java` - Onionプログラム用のI/Oユーティリティ
- `OnionClassLoader.scala` - コンパイルされたクラス用のカスタムクラスローダー

**標準ライブラリ** (`src/main/java/onion/`):
- `IO` - コンソールI/O (println, readLine)
- `Strings` - 文字列ユーティリティ
- `Rand` - 乱数生成 (int, long, double, boolean, nextInt, shuffle)
- `Assert` - テストアサーション (assertTrue, assertEquals, assertNotNull, fail)
- `Timing` - 時間計測 (nanos, millis, measure, time, sleep)
- `Files` - ファイル操作
- `DateTime` - 日付/時刻ユーティリティ
- `Json` - JSONパース/シリアライズ
- `Http` - HTTPクライアント
- `Regex` - 正規表現
- `Option`, `Result`, `Future` - 関数型

## テスト

**フレームワーク:** ScalaTest 3.2.19

**テストの場所:** `src/test/scala/onion/compiler/tools/`

**基底クラス:** テストは統合テスト用に `AbstractShellSpec` を継承

**テストスイート:**
- `HelloWorldSpec.scala` - 基本出力
- `FactorialSpec.scala` - 再帰
- `StringInterpolationSpec.scala` - 文字列機能
- `BreakContinueSpec.scala` - 制御フロー
- `ImportSpec.scala` - モジュールシステム
- `BeanSpec.scala` - OOP機能
- `ForeachSpec.scala` - イテレーション
- `CompilationFailureSpec.scala` - エラー処理

**テストリソース:** `src/test/run/` にはテストで使用されるOnionプログラムの例が含まれています

## サンプルプログラム

`run/` ディレクトリにあります：
- `Hello.on` - 最もシンプルなプログラム: `IO::println("Hello")`
- `FizzBuzz.on` - 定番のFizzBuzz問題
- `Fibonacci.on` - 再帰版vs反復版の速度比較
- `GuessNumber.on` - 数当てゲーム（CLI対話型）
- `TodoApp.on` - Todoリスト管理アプリ
- `Calculator.on`, `Array.on`, `Bean.on`, `List.on` など

## パーサー文法の変更

パーサー文法 (`grammar/JJOnionParser.jj`) を変更する場合：
1. JavaCC文法ファイルを編集
2. `sbt compile` を実行 - パーサーが自動再生成される
3. 生成されたパーサーは `target/scala-3.3.7/src_managed/main/java/onion/compiler/parser/` に出力される

## 重要なコードの場所

- **メインコンパイラロジック**: `src/main/scala/onion/compiler/`
- **最適化**: `src/main/scala/onion/compiler/optimization/`
  - `TailCallOptimization.scala` - 末尾再帰 → ループ変換
- **パーサー文法**: `grammar/JJOnionParser.jj`
- **ランタイムライブラリ**: `src/main/java/onion/` (Javaインターフェース)
- **ツール (CLI)**: `src/main/scala/onion/tools/`
- **テスト**: `src/test/scala/onion/compiler/tools/`
- **テストプログラム**: `src/test/run/` (Onionプログラムの例)
- **ビルド設定**: `build.sbt`

## 言語構文

### 基本構文

```onion
// 継承とインターフェース実装を伴うクラス定義
class MyClass : ParentClass <: Interface1, Interface2 {
  val immutableField: String      // 不変フィールド
  var mutableField: Int           // 可変フィールド
public:
  def method(arg: Type): ReturnType { ... }
  static def staticMethod(): void { ... }
  def this { /* コンストラクタ */ }
}

// エイリアス付きimport構文
import {
  java.util.*
  java.lang.Long as JLong;
}

// 型キャスト（メソッドチェーンには括弧が必要）
val btn: JButton = (event.source as JButton)
val text: String = (obj as JButton).getText()

// 静的メソッド呼び出し
IO::println("Hello")
Long::toString(42L)

// インスタンスメソッド呼び出し
obj.method()
obj?.safeMethod()  // 安全呼び出し演算子（objがnullの場合nullを返す）
```

### 制御フロー

```onion
// if/else
if condition { ... } else { ... }

// whileループ
while condition { ... }

// forループ
for i = 0; i < 10; i++ { ... }

// foreach
foreach item: Type in collection { ... }

// select（値に対するパターンマッチング）
select value {
case 1, 2, 3: ...
case 4: ...
else: ...
}

// breakとcontinue
while true {
  if done { break }
  if skip { continue }
}
```

### 関数とラムダ

```onion
// ラムダ式
val f: Function1[Int, Int] = (x: Int) -> x * 2
val g = (x, y) -> x + y

// 末尾ラムダ構文
list.map { x => x * 2 }
list.filter { x => x > 0 }

// メソッド参照（静的）
Type::methodName
```

### 高度な機能

**モナド合成のためのdo記法:**
```onion
do[Future] { x <- asyncOp(); ret x + 1 }
do[Option] { a <- getA(); b <- getB(); ret a + b }
```

**非同期プログラミング:**
```onion
val future: Future[String] = Future::async(() -> { longOperation() })
future.map((s) -> s.toUpperCase())
future.onSuccess((s) -> IO::println(s))
future.onFailure((e) -> IO::println("Error: " + e.message()))
```

**Try-Catch:**
```onion
try {
  riskyOperation()
} catch e: Exception {
  IO::println("Error: " + e.message())
}
```

## よくある構文ミス（重要）

他の言語と混同しやすい構文です。**必ず確認してください：**

### 型システムと演算子

| 誤り（Java/Scala風） | 正しい（Onion） |
|---------------------|----------------|
| `expr$Type` | `expr as Type` - `as`キーワードを使用 |
| `(expr as Type).method()` はメソッドチェインに括弧が必要 | ✓ 正しい |
| `Box<T>` | `Box[T]` - ジェネリクスは角括弧 |
| `new Box<String>()` | `new Box[String]()` - 角括弧 |
| `String \| null` | `String?` - nullable型の構文 |
| `==` で参照等価 | `===` が参照等価、`==` は値等価 |

### 制御フロー

| 誤り（Java/Scala風） | 正しい（Onion） |
|---------------------|----------------|
| `if (condition) { }` | `if condition { }` - 条件を括弧で囲まない |
| `while (condition) { }` | `while condition { }` - 括弧なし |
| `else if condition { }` | `else { if condition { } }` - `else if`キーワードなし |
| `switch value { case 1: }` | `select value { case 1: }` - `switch`ではなく`select` |
| `for (int i = 0; ...)` | `for var i: Int = 0; ...` - 括弧なし |
| `i += 1` や `i++` | `i = i + 1` - 複合演算子は限定的（`++`はforループ内のみ） |
| `while (x = read()) != null` | ✓ 正しい - 括弧付きで代入を条件に使用可 |

### メソッドとクラス

| 誤り（Java/Scala風） | 正しい（Onion） |
|---------------------|----------------|
| `public ClassName() { }` | `def this { }` - コンストラクタは`def this` |
| `public ClassName(x: Int) { }` | `def this(x: Int) { }` - 引数付き |
| `catch (e: Exception) { }` | `catch e: Exception { }` - 括弧なし |
| `public void method()` | `public: def method(): void` - セクションベースのアクセス |
| `def method(): T { }` | `def method: T { }` - 引数なしなら括弧省略可 |
| `@Override void method()` | `override def method(): void` - アノテーションではなくキーワード |
| `fun String.twice()` (Kotlin風) | `extension String { def twice() { } }` - extensionブロック |
| `this.field = value` コンストラクタ内 | ✓ 正しい - フィールドは`this.`が必要 |
| `Long.toString(0)` | `Long::toString(0L)` - 静的メソッドは`::`、longは`L`サフィックス |
| `System.out` | `System::out` - 静的フィールドも`::` |

### インポートと型

| 誤り（Java/Scala風） | 正しい（Onion） |
|---------------------|----------------|
| `import java.util.*;` | `import { java.util.* }` - 波括弧が必要 |
| `import { Foo = pkg.Class; }` | `import { pkg.Class as Foo; }` - エイリアスに`as` |
| `new int[10]` | `new Int[10]` - プリミティブ型名は大文字 |
| `int`, `long`, `boolean` | `Int`, `Long`, `Boolean` - 大文字 |

### コレクション

| 誤り（Java/Scala風） | 正しい（Onion） |
|---------------------|----------------|
| `new String[] {"a", "b"}` | `["a", "b"]`は`List`を作成、配列は`new String[n]` |
| `list.get(i)` | `list[i]` - 配列もリストも`[]`でアクセス |
| `list.set(i, v)` | `list[i] = v` - 代入も`[]`を使用 |
| `arr.length()` | `arr.length` - 配列の長さはプロパティ、メソッドではない |
| `list.size()` | `list.size` - これもプロパティ |
| `foreach (x : list)` | `foreach x: Type in list { }` - 構文が異なる |

### リテラル

| 誤り | 正しい（Onion） |
|-----|----------------|
| `0` でlong | `0L` - Long型には明示的な`L`サフィックスが必要 |
| `0.0` でfloat | `0.0f` - Float型には明示的な`f`サフィックス |
| `"str" + 123` | ✓ 正しい - 自動文字列変換 |

### 継承とインターフェース

| 誤り（Java/Scala風） | 正しい（Onion） |
|---------------------|----------------|
| `class A extends B` | `class A : B` - 継承はコロン |
| `class A implements I` | `class A <: I` - インターフェースは`<:` |
| `class A extends B implements I` | `class A : B <: I` - 両方を組み合わせ |
| `class A implements I, J` | `class A <: I, J` - カンマ区切り |

### レコード

| 誤り（Java風） | 正しい（Onion） |
|---------------|----------------|
| `record Point { int x; int y; }` | `record Point(x: Int, y: Int)` - コンストラクタ形式 |
| `point.x` でレコードフィールド | `point.x()` - レコードフィールドはメソッド（括弧必要） |

### ラムダと関数

| 誤り | 正しい（Onion） |
|-----|----------------|
| `x -> x * 2` | `(x: Int) -> x * 2` - 型注釈が必要なことが多い |
| `(x) -> expr`（単一引数） | `(x: Type) -> expr` - 型が通常必要 |
| `func(arg)` でラムダ呼び出し | `func.call(arg)` または `func(arg)` - 両方OK |
| `Int -> Int` | ✓ 正しい - 単一引数の関数型 |
| `(Int, Int) -> Int` | ✓ 正しい - 複数引数の関数型 |
| `list.map(x -> x * 2)` | `list.map { x => x * 2 }` - トレイリングラムダは`=>`を使用 |

### メソッド呼び出し

| 誤り | 正しい（Onion） |
|-----|----------------|
| `obj.method()` を常に | `obj.method` - 引数なしメソッドは括弧省略可 |
| `str.equals(other)` | `str == other` - 文字列に`==`が使える（値等価） |
| `str1 == str2` で参照チェック | `str1 === str2` - 参照には`===` |

### 例外

| 誤り（Java風） | 正しい（Onion） |
|---------------|----------------|
| `throw new Exception();` | `throw new Exception("msg")` - 同じ構文 |
| `try { } catch { } finally { }` | ✓ 正しい - finallyサポートあり |
| `catch (Type e)` | `catch e: Type` - 括弧なし、コロンの後に型 |

### その他

| 誤り | 正しい（Onion） |
|-----|----------------|
| `null`チェックに`== null` | ✓ 正しい - 期待通りに動作 |
| `&&`, `\|\|` 演算子 | ✓ 正しい - Javaと同じ |
| `!condition` | ✓ 正しい - Javaと同じ |
| `a ? b : c` 三項演算子 | 非サポート - `if/else`式を使用 |
| 文字列テンプレート `$var` や `${expr}` | `"text #{expr}"` - `#{}` 構文を使用 |
| `this`のみ | `self`も使用可 - 両方とも現在のインスタンスを参照 |
| 予約語を識別子に | `` `class` `` - バッククォートでキーワードをエスケープ |
| `println("Hi")` | ✓ 正しい - トップレベル関数、または`IO::println` |

## 既知の制限

- コンパイラは特定のエッジケースでクラッシュする可能性があります（`run/` の例は動作確認済み）
- ジェネリクスは型消去ベース（変性、ワイルドカード、具象化された型情報なし）
- 診断機能は改善中；一部のエラーはパイプラインの後半で報告される場合があります
- 末尾呼び出し最適化は直接の自己再帰のみ処理（相互再帰や継続渡しスタイルは非対応）

## 実行のエントリーポイント

`onion` コマンド（または `sbt runScript`）を使用する場合、エントリーポイントは以下の順で決定されます：
1. 明示的なクラス定義があり、そのクラスがmainメソッドを持つ場合はそのmainメソッド
2. 最上位のクラスのmainメソッド
3. それ以外の場合、最上位の最初の文
