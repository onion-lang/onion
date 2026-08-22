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
- `-maxErrorReport <n>` - 報告するエラーの数を制限
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

5. **コード生成** (`src/main/scala/onion/compiler/codegen/TypedAstCodeGeneration.scala`)
   - **パイプラインの主境界** は `TypedAstCodeGeneration` → `backend/asm/AsmBackend.scala`
   - **既存の大きい実装本体** は `src/main/scala/onion/compiler/backend/asm/AsmCodeGeneration.scala`
   - ビジターパターン: `src/main/scala/onion/compiler/backend/asm/AsmCodeGenerationVisitor.scala`
   - バイトコードユーティリティ:
     - `backend/asm/MethodEmitter.scala` - JVMメソッド生成
     - `backend/asm/LocalVarContext.scala` - ローカル変数追跡
     - `backend/asm/AsmUtil.scala` - ASMヘルパー関数
   - 出力: `CompiledClass` オブジェクト（メモリ内またはファイル）

### 主要なアーキテクチャコンポーネント

**オーケストレーション:**
- `OnionCompiler.scala` - メインコンパイラ facade
- 実フェーズ実行は `compiler/pipeline/PipelineRunner.scala`
- `compileDetailed` で diagnostics / timings / debug artifacts を返せる

**エントリーポイント:**
- `onion.tools.CompilerFrontend` - `onionc` コマンドのCLI（.classファイルにコンパイル）
- `onion.tools.ScriptRunner` - `onion` コマンドのCLI（メモリ内でコンパイルして実行）
- `onion.tools.Shell` - 対話型REPLシェル

**型システム:**
- `BasicType` - プリミティブ (int, long, double, boolean, byte, short, char, float)
- `typing/session/TypingSession.scala` - unit-local / global typing state
- `typing/NameResolver.scala` 相当 (`NameResolution.scala`) - 型名解決の本体
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
- `Db` - JDBCアクセス (connect, query, update, トランザクション)
- `Archive` - Zip/gzipの作成と展開
- `Concurrent` - スレッドプール、カウンタ、ロック、チャネル
- `Net` - TCPソケット (connect, listen)
- `Server` - 最小限のHTTPサーバー (ルーティング、リクエスト/レスポンス)

## テスト

**フレームワーク:** ScalaTest 3.2.19

**テストの場所:** `src/test/scala/onion/compiler/tools/`

**基底クラス:** テストは統合テスト用に `AbstractShellSpec` を継承

**ロケール非依存性（重要）:** エラーメッセージは日英バイリンガル（`errorMessage.properties` / `errorMessage_ja.properties`）で、JVM のデフォルトロケールから解決されます。リリース CI は**英語**ロケールで実行されますが、ローカル開発環境はしばしば `ja_JP` です。エラー**コード**（`E0002`、`E0069` など）、`Shell.Failure(-1)`、またはロケールに依存しない部分文字列でアサートしてください — `"重複"` のようなローカライズされたメッセージ本文だけでアサートしないこと。さもないとローカルでは通ってもCIでのみ失敗します。

**スイートの実行方法（sbt 2.0.6 — 罠が2つ）:**

```bash
sbt shutdown && sbt -Duser.language=en testFull    # 続けて =ja でも実行する
```

1. **`test` は差分実行です。** sbt 2 では `testQuick` に委譲されるため、ソース無変更での
   2 回目は `No tests to run` と表示して終了コード 0 を返します。全件実行には `testFull` を使います。
2. **`-D` は新規サーバにしか届きません。** sbt サーバは実行間で常駐するため、`en` で起動済みの
   サーバに `-Duser.language=ja` を渡しても何も起きません（`java.util.Locale.getDefault()` は
   古い方を返し続けます）。先に `sbt shutdown` してください。

CI はどちらの影響も受けません。差分実行の状態は `target/` にあり、`setup-java` の
`cache: 'sbt'` はそこをキャッシュしないため、CI は毎回コールドスタートで全件実行します。

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
class MyClass extends ParentClass conforms Interface1, Interface2 {
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
list.map { x -> x * 2 }
list.filter { x -> x > 0 }

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
| `new Box("hi")` は型引数なしだと拒否される？ | ✓ 正しい - 型引数はコンストラクタ引数（または期待される型）から推論される。何にも束縛されない裸のジェネリック（`new Empty()`）だけがE0066になる |
| `String \| null` | `String?` - nullable型の構文 |
| `Box[String?]` は拒否される？ | ✓ 正しい - 裸の`[T]`はnullableな型引数を受け付ける（Kotlin方式） |
| 裸の`T`値に対する`t.method()` | E0057 - `t?.method()`、`t ?: default`、`if t != null`、または`[T extends B]`を使う |
| `[T extends Object]`は`[T]`と同じ？ | いいえ - `extends`はTを非nullにする。`[T extends B?]`でnullableに戻せる |
| `==` で参照等価 | `===` が参照等価、`==` は値等価 |

### 制御フロー

| 誤り（Java/Scala風） | 正しい（Onion） |
|---------------------|----------------|
| `if (condition) { }` | `if condition { }` - 条件を括弧で囲まない |
| `while (condition) { }` | `while condition { }` - 括弧なし |
| `else if condition { }` | ✓ 正しい - `else if`チェーンはサポートされている（式としても使える） |
| `switch value { case 1: }` | `select value { case 1: }` - `switch`ではなく`select` |
| `case s: String:`（Java/Scala流パターン） | `case s is String:` - 型パターンは`is`を使う。sealedの網羅性チェック（E0042）が適用される |
| `case Add(l, n is Num):` のようなネストした型パターン？ | ✓ 正しい - 型パターンはデストラクチャリングの中にネストでき、束縛された値は絞り込まれた型として使える。レコードでない要素にも使える（`case Wrap(s is String)`） |
| `case Circle(r):` は非サポート？ | ✓ 正しい - レコードのデストラクチャリングパターンは動作する。`case x when guard:` も使える |
| `for (int i = 0; ...)` | `for var i: Int = 0; ...` - 括弧なし |
| `for i in 0..10`（他言語） | `foreach i: Int in 0..10` - 範囲: `a..b`は両端含む、`a..<b`は上端を含まない |
| `i += 1`, `i -= 1`, `i++` | ✓ 正しい - 複合代入と`++`/`--`は文として使える |
| `while (x = read()) != null` | ✓ 正しい - 括弧付きで代入を条件に使用可 |

### メソッドとクラス

| 誤り（Java/Scala風） | 正しい（Onion） |
|---------------------|----------------|
| `public ClassName() { }` | `def this { }` - コンストラクタは`def this` |
| `class Point(val x: Int, val y: Int)` | ✓ 正しい - プライマリコンストラクタ。`val`/`var`引数は公開フィールドになり、素の引数は`: Super(args)`に渡される |
| `public ClassName(x: Int) { }` | `def this(x: Int) { }` - 引数付き |
| `catch (e: Exception) { }` | `catch e: Exception { }` - 括弧なし |
| `public void method()` | `public: def method(): void` - セクションベースのアクセス |
| `def method(): T { }` | `def method: T { }` - 引数なしなら括弧省略可 |
| `@Override void method()` | `override def method(): void` - アノテーションではなくキーワード |
| `fun String.twice()` (Kotlin風) | `extension String { def twice() { } }` - extensionブロック |
| `this.field = value` コンストラクタ内 | ✓ 正しい - `this.`/`self.`は使える。今は省略も可能（裸の`field`は暗黙アクセスでフィールドに解決される） |
| `Long.toString(0)` | `Long::toString(0L)` - 静的メソッドは`::`、longは`L`サフィックス |
| `System.out` | `System::out` - 静的フィールドも`::` |
| `C::field = v` は非サポート？ | ✓ 正しい - 静的フィールドへの代入は動作する（公開フィールド） |

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
| `Map.of("a", 1)` | `["a": 1, "b": 2]`は`Map`（LinkedHashMap）を作成。空マップは`[:]` |
| `list.get(i)` | `list[i]` - 配列もリストも`[]`でアクセス |
| `list.set(i, v)` | `list[i] = v` - 代入も`[]`を使用 |
| `arr.length()` | `arr.length` - 配列の長さはプロパティ、メソッドではない |
| `list.size()` | `list.size` - これもプロパティ |
| 標準ライブラリが配列を返す？ | いいえ - 標準ライブラリは常に`List`を受け取り、返す。配列は返さない（`Strings::split`、`Regex::findAll`、`Files::readLines`、`Rand::shuffle`、`Http`のヘッダー/パラメータ、`Future::all`）。配列が現れるのはJavaとの境界だけ：`main(args: String[])`、`byte[]`によるバイナリI/O、varargs。境界を越えるには`Colls::toList(args)`を使う |
| `foreach (x : list)` | `foreach x: Type in list { }` - 構文が異なる |
| `for (k, v) in map` | `foreach (k, v) in map { }` - マップのエントリをデストラクチャリング |
| `val (a, b) = pair` は非サポート？ | ✓ 正しい - レコード（と`Map.Entry`）を位置ベースでデストラクチャリングする。可変にするには`var (x, y) = ...` |

### リテラル

| 誤り | 正しい（Onion） |
|-----|----------------|
| `0` でlong | `0L` - Long型には明示的な`L`サフィックスが必要 |
| `0.0` でfloat | `0.0f` / `0.0F` - Float型には`f`/`F`サフィックス、Double型には`d`/`D`サフィックス（大文字小文字どちらも可） |
| `new Double(1.5)` / `new Integer(3)` | `Double`/`Int`はプリミティブ型 - そのまま`1.5`と書けばよい。Object/Numberスロットへのボクシングは自動。ラッパークラスは`JDouble`/`JInteger`/... |
| `"str" + 123` | ✓ 正しい - 自動文字列変換 |

### 継承とインターフェース

| 誤り | 正しい（Onion） |
|------|----------------|
| `class A : B`（この変更以前のOnion） | `class A extends B` - 親クラスは `extends` |
| `class A <: I`（この変更以前のOnion） | `class A conforms I` - インターフェースは `conforms` |
| `class A implements I`（Java） | `class A conforms I` |
| `class A extends B implements I`（Java） | `class A extends B conforms I` - 両方を組み合わせ |
| `class A conforms I, J` | ✓ 正しい - カンマ区切り |
| `def conforms(..)` はキーワードだから書けない？ | ✓ 書ける - `conforms` は**ソフトキーワード**で、スーパータイプ位置でのみ特別扱いされる |

### レコード

| 誤り（Java風） | 正しい（Onion） |
|---------------|----------------|
| `record Point { int x; int y; }` | `record Point(x: Int, y: Int)` - コンストラクタ形式 |
| `point.x` でレコードフィールド | `point.x()` - レコードフィールドはメソッド（括弧必要） |
| `point.copy(y=9)` は未サポート？ | ✓ 正しい - 名前付き部分コピー、`copy()`によるクローン、位置引数のコピーいずれも動作する |
| メソッドを持つレコード？ | `record Fraction(num: Int, den: Int) { public: def plus(o: Fraction): Fraction = ...; static def of(...) ... }` - レコードは `{ access-section* }` 本体（インスタンス/静的/演算子メソッド、privateヘルパー）を持てる。メソッドからは生成されたアクセサが見える。ジェネリックなインターフェースを実装するジェネリックレコードでも動作する（`record Foo[T](v: T) conforms Bar[T]`） |
| `enum Planet(mass: Double) { MERCURY(3.3) }` | ✓ 正しい - データを持つenum。`mass()` アクセサ、`values()`/`valueOf()` が使える |
| ADT（直和型）のenum？ | `enum Shape { case Circle(radius: Double); case Square(side: Double); case Origin; public: def area(): Double = select this { case c is Circle: ...; case o is Origin: 0.0 } }` - `case` キーワードで宣言する各ケースがそれぞれのフィールドを持つ。sealedインターフェース＋ケースごとのrecordに脱糖され、`select` の網羅性チェック（E0042）が適用される。フィールドが無いケース（シングルトン）は `new Origin()` で作るゼロフィールドrecordになる。`case`-enumは `java.lang.Enum` ではなくsealed階層（`values()`/`ordinal()` は無い）。共有パラメータと `case` ケースの混在はエラー |
| ジェネリックなADT enum？ | `enum Opt[T] { case Some(value: T); case Nothing }` - 型パラメータは生成されるsealedインターフェースと各ケースのrecordに伝播する。型パターンはスクルーティニーの型引数を復元するので、`Opt[String]` から `Some` をマッチさせると `Some[String]` が束縛され、`s.value()` は `String` になる。*homogeneous*（データを持たない）enumは型パラメータを取れない（`java.lang.Enum`になるため） |
| レコードから手でパーサーを書く | `record R(...) from re"..."` - `R::parse(s): R?`（アンカー一致、非マッチ/変換失敗はnull）と `R::parseAll(text): List` を合成する。`from` は `conforms` より前に書く |
| `null` ではなく失敗理由すべてが欲しい、あるいはレコード1つに複数の境界を名付けたい？ | `record R(...) shape name = re"..."` - `R::name(): onion.Shape[R]` を合成する。`.parse(s)` は `Outcome[R]`（値、またはそれが得られなかった全理由を `Defect` として保持する）を返し、可逆な場合は `.print(v)` で書き戻せる。`shape` 節はレコード1つに複数付けられる（正規表現以外に `shape name = json`/`config` という書式指定も可）。`from re"..."` と共存できる |
| レコードを手でシリアライズ（JSON/YAML） | `record R(...) derive!(Json, Yaml)` - 共通の `toMap`/`fromMap` を介して `R::fromJson`/`toJson`/`fromYaml`/`toYaml` をマクロ生成する。スカラー型のコンポーネントのみ（それ以外はE0062）、未知のマーカーはE0063。`from re"..."` と共存できる |
| 別スイートでレコードをテストする | `record R(...) law name(p: T) { boolExpr } example { boolExpr }` - コンパイラがビルド時に実行する。偽の `example` はE0065、反証された `law` はE0064（反例付き）になる。`parse∘format==id` のような性質を機械的に検証できる |

### ラムダと関数

| 誤り | 正しい（Onion） |
|-----|----------------|
| `x -> x * 2` | ✓ 正しい - 裸の単一引数はそのまま使える。式本体も使える |
| `(x) -> expr` | ✓ 正しい - 引数の型は期待される関数型から推論される |
| `func(arg)` でラムダ呼び出し | ✓ 正しい - 関数値は直接呼び出せる |
| `Runnable`/`Comparator`としてのラムダ | ✓ 正しい - JavaのSAM（関数型インターフェース）への変換が効く |
| `Int -> Int` | ✓ 正しい - 単一引数の関数型 |
| `(Int, Int) -> Int` | ✓ 正しい - 複数引数の関数型 |
| `list.map { x => x * 2 }` | `list.map { x -> x * 2 }` - トレイリングラムダの矢印も`->`。`=>`は言語には存在しない（関数型、`(x) -> e`、`{ x -> e }`のすべてで矢印は`->`の一種類だけ） |
| `stream().map().collect()` | `list.map { ... }.filter { ... }` - List/Iterable/配列に組み込みのextensionパイプラインがある |

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
| `using r = expr { }` | `try (val r = expr) { }` - try-with-resources。複数のリソースは`;`で区切り、逆順にクローズされる |
| `catch (Type e)` | `catch e: Type` - 括弧なし、コロンの後に型 |

### ツール、capability、と効果 (v0.10)

| 誤り | 正しい（Onion） |
|-----|----------------|
| 裸の`readText(p)` / `get(url)` / `now()` / `exit(1)` | **もう解決しない** — デフォルトの静的インポートが純粋なクラスに限定された。`Files::readText`のように修飾するか、明示的にインポートする: `import { onion.Files::*; java.lang.System::exit }`。裸の`println`だけは引き続き使用可能（`onion.IO`が唯一の例外） |
| 手書きの引数解析を行うCLI関数 | `tool name(args) [: T] [requires { caps }] { body }` — トップレベルのtool宣言。トップレベルでtoolを宣言し（`main`を持たない）スクリプトはCLIそのものになる: `--help`、`--contract`（機械可読なJSON）、`--plan`（バインドされた効果を表示するだけで何も実行しないドライラン）はすべて宣言から自動導出される |
| tool内の未宣言の副作用 | 呼び出し箇所でE0077 — 本体の効果は推移的に推論され、`requires { read(src), write(dst), console, unknown }`と照合される。過剰申告はE0078、不正なcapabilityはE0079。リストにないJava呼び出しは`unknown`として明示的に許可する必要がある |
| コメントを保持したい場合の`shape doc = json` | `shape doc = config` — コメント付きの`key = value`形式ファイル向けのLOSSLESSなshape。`parseLossless`は`Residue`（コメント、空白、キー順序、未知キー、値の表記）を保持し、`r.edit { v -> v.copy(port = 9090) }.render()`は該当する値のスロットだけを書き換える |
| その場しのぎのカスタムフォーマット実装 | `class MyShape conforms Shape[T]` — ユーザー定義のshapeはコンビネータとOutcome/Defectを無償で得られるが、そのファイルは法則を主張しなければならない（`example l1 { s.parse(s.print(v)).get() == v }`）。さもなければそのクラスはE0080になる |
| recordの中でしかチェックできない法則 | トップレベルの`example [name] { boolExpr }` — recordのexampleと同様にビルド時に実行される。偽の主張はE0065 |
| 未知の`--effects` | `onionc --effects` / `onion --effects file.on`は各メソッドの推論された効果集合を表示する（`read write net exec env clock rand console unknown`。空なら`pure`） |

### その他

| 誤り | 正しい（Onion） |
|-----|----------------|
| `null`チェックに`== null` | ✓ 正しい - 期待通りに動作 |
| nullable（`T?`）な`a`に対する`a == b` | ✓ 正しい - null安全な値等価（`java.util.Objects.equals`）：両方nullなら等しい、片方だけnullなら等しくない、それ以外は`equals()`。事前のnullチェックは不要 |
| `&&`, `\|\|` 演算子 | ✓ 正しい - Javaと同じ |
| `!condition` | ✓ 正しい - Javaと同じ |
| `a ? b : c` 三項演算子 | 非サポート - `if/else`式を使用 |
| `operator fun plus`（Kotlin風） | `def plus(o: T): T` - 左オペランドの`+ - * / %`はそれぞれplus/minus/times/div/remにマップされる |
| 文字列テンプレート `$var` や `${expr}` | `"text #{expr}"` - `#{}` 構文を使用 |
| `this`のみ | `self`も使用可 - 両方とも現在のインスタンスを参照 |
| 予約語を識別子に | `` `class` `` - バッククォートでキーワードをエスケープ |
| `println("Hi")` | ✓ 正しい - トップレベル関数、または`IO::println` |
| `Pattern.compile("\\d+")` | `re"\d+"` - 生の正規表現リテラル（二重エスケープ不要） |
| `new File(p).read...` | `file"path".text()` / `.lines()` / `.csv()` / `.json()` - リソースリテラル（動的には`file(p)`） |
| `x.let { f(it) }`（Kotlin風） | `x \|> f` - パイプラインが`x`を`f`の第一引数として渡す |
| `when (s) { matches(...) }` | `case re"(\d+)" (n):` - 正規表現のselectパターン。コンパイル時にチェックされる |
| main内での手動`args`解析 | `def main(name: String, n: Int = 1)` - シグネチャからauto-CLIがフラグ/usageを導出する |

## 既知の制限

- コンパイラは、変異ファザー・クラッシュ再現コーパス・コード生成正当性テストによって「クラッシュしない／誤コンパイルしない」基準を維持しています。もしクラッシュや誤コンパイルに遭遇したら、最小の再現例を報告してください（`run/` の例は動作確認済み）
- ジェネリクスは型消去ベース（具象化された型情報なし。型引数は不変で、変性やワイルドカードはなし）；型パラメータの null 性はコンパイル時に追跡される（裸の `[T]` は nullable、`[T extends B]` は非 null、Java の型変数は platform）
- 診断機能は改善中；一部のエラーは理想よりパイプラインの後半で報告される場合があります
- 末尾呼び出し最適化は直接の自己再帰と相互自己再帰をカバー（一般的な継続渡しスタイルは非対応）
- 型推論はローカル（左から右への一方向で、遅延／大域的な単一化は行わない）；型引数はその左側に書かれた内容によって確定するため、後続の式で型を決めたい場合は対象に型注釈を付ける

## 実行のエントリーポイント

`onion` コマンド（または `sbt runScript`）を使用する場合、エントリーポイントは以下の順で決定されます：
1. 明示的なクラス定義があり、そのクラスがmainメソッドを持つ場合はそのmainメソッド
2. 最上位のクラスのmainメソッド
3. それ以外の場合、最上位の最初の文
