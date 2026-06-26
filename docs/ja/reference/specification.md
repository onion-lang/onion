# 言語仕様

このページでは、現在のコンパイラによって実装されている Onion の構文と意味論を要約します。権威ある文法は `grammar/JJOnionParser.jj` です。

## 字句構造

### キーワード

```
abstract    break       case        catch       class       continue
def         do          else        enum        extends     extension
false       final       finally     for         foreach     forward
if          import      interface   is          module      new
null        override    private     protected   public      record
return      sealed      select      self        static      super
synchronized this        throw       true        try         type
val         var         when        while
```

キーワードはバッククォートでエスケープすれば識別子として使えます: `` `class` ``。

### 識別子

- 文字またはアンダースコアで始まり、文字、数字、アンダースコアが続く
- 大文字と小文字を区別

### リテラル

**整数リテラル**（任意の数値リテラルでアンダースコアを桁のグループ化に使えます）:
- 10進数: `42`、`1_000_000`
- 16進数: `0xFF`、`0xFF_FF`
- 2進数: `0b1010`、`0b1010_1010`
- 8進数: `077`

接尾辞で型を選択: `B` (Byte)、`S` (Short)、`L` (Long)。接尾辞なしの整数は `Int`。

**浮動小数点:** `3.14`、`1.23e10`、`1_234.5` — デフォルトは `Double`。
`f`/`F` で `Float`、`D` で明示的な `Double`。

**文字列:** `"text"`（通常のエスケープ付き）。`"""..."""` で複数行。
文字列補間は `#{expr}` で式を埋め込みます（補間内の入れ子文字列リテラルも許可）。

**文字:** `'A'`、`'\n'`。**真偽値:** `true`、`false`。**Null:** `null`。

**コレクションリテラル:** `[1, 2, 3]` は `List` を構築。`["a": 1, "b": 2]` は
挿入順の `Map` を構築。`[:]` は空のマップ。

**範囲:** `a..b`（包含）、`a..<b`（排他）— `foreach` で反復可能で、コレクションを
実体化しません。

## 型システム

### プリミティブ型

| 型 | サイズ |
|------|------|
| `Byte` / `Short` / `Int` / `Long` | 8/16/32/64ビット整数 |
| `Float` / `Double` | IEEE 754 |
| `Char` | 16ビット Unicode |
| `Boolean` | `true`/`false` |

大文字で始まる名前はプリミティブを表します。ボックス化ラッパークラスは
`JByte`、`JInteger`、`JDouble` などとして利用可能。`Object`/`Number` コンテキストへの
ボックス化は自動です。

### 参照型

- **クラス / インターフェース型**、適用済みジェネリクスを含む: `List[String]`、`Box[T]`
- **配列型**: `T[]`、`T?[]`（nullable 要素）、多次元 `T[][]`
- **関数型**: `Int -> Int`、`(Int, Int) -> String`
- **Nullable 型**: `T?` — 下記参照
- **Null 型**（`null` の型）と **ボトム型**（`return`/`throw`/`break`/`continue` などの
  非返却式）

### Nullable 型と Null 安全

`T` は `null` を保持できません。`T?` は `null` を保持できます。`T → T?` は暗黙に
拡大されます。`T? → T` はアンラップが必要です。

- `x?.member` — 安全呼び出し、結果は nullable に拡大
- `xs?[i]` — 安全インデックス
- `x ?: default` — Elvis。フォールバックが null でない場合、結果型から null 性が失われる
- `x!!` — 非 null アサーション（null のとき `NullPointerException` を投げる）
- スマートキャスト: `if x != null { ... }` は不変ローカルと一度も代入されない
  パラメータを狭める。`is` チェックも同様に狭め、`!(cond)` はどちらの分岐が
  狭まるかを入れ替える
- `null` リテラルを non-null 型に代入すると警告（W0012）
- `Object` はあらゆる nullable 値を受け入れる（Scala の `Any` のようなトップ型）

### ジェネリクス

`[]` 構文の消去ベースジェネリクス: `class Box[T]`、`def first[T](xs: List[T]): T`、
`record Pair[A, B](first: A, second: B)`。ワイルドカード `?`、`? extends T`、
`? super T` を型引数として受け入れます。

型パラメータの null 性は Kotlin に従います。

- 裸の `[T]` は nullable 型引数を受け入れ（`Box[String?]`）。ジェネリック本体では
  型 `T` の値を直接デリファレンスできません（E0057）。`?.`、`?:`、`!!` または
  null チェックで狭めるまで
- `[T extends B]` は `T` を非 null に制限し、直接デリファレンスを許可。
  `[T extends B?]` は境界 `B` で nullable に戻す
- Java クラスの型変数は *プラットフォーム*: 両方向で寛容

### 型変換

拡大（`Byte → Short → Int → Long → Float → Double`、`Char → Int`）は自動です。
縮小には `as`（またはレガシーな `$`）が必要。

```onion
val i: Int = (3.14 as Int)
```

`as` は参照キャストでもあります: `(obj as JButton).getText()`。
`expr is Type` は実行時型をテストします。

## 宣言

### 変数

```onion
val name: Type = expr     // 不変
var name: Type = expr     // 可変
val inferred = expr       // ローカル型推論
val (a, b) = recordValue  // 分解（records / Map.Entry）
```

### 関数

```onion
def name(param: Type, opt: Type = default): ReturnType { body }
def name(param: Type): ReturnType = expression      // 式本体
def vararg(parts: String...): String { ... }        // 可変長引数
def generic[T](x: T): T { ... }                     // メソッド型パラメータ
```

呼び出し側は名前付き引数を渡せます（`f(b = 2, a = 1)`）。省略されたパラメータは
デフォルトから埋められます。メソッド、コンストラクタ、records はすべて
名前付き引数とデフォルトをサポートします。

### クラス

```onion
class Name [TypeParams] [(primary params)] [: Super[(args)]] [<: I1, I2] {
  sections
}
```

**プライマリコンストラクタ:** `val`/`var` パラメータは public
（final/mutable）フィールドを自動的に宣言します。プレーンなパラメータは
コンストラクタ内でのみ存在します（例: `: Super(args)` に渡すため）。クラス本体は
オプションです。

```onion
class Point(val x: Int, val y: Int)
class Dog(name: String, val breed: String) : Animal(name)
```

**クラシックなコンストラクタ**も利用可能です。

```onion
def this(params) { body }
def this(params): (superArgs) { body }
```

メンバーはデフォルトで private です。`public:` / `protected:` / `private:` セクションで
可視性を設定します。`static def` で static メソッドを、`static val` で定数を宣言。
フィールドの初期化子は宣言順に実行されます。

### インターフェース

```onion
interface Greeter {
  def name(): String                                  // 抽象
  def greet(): String { return "Hello, " + this.name() }  // デフォルトメソッド
  def shout(): String = "HEY " + this.name()          // 式本体
}
```

本体を持つメソッドは JVM デフォルトメソッドにコンパイルされます。`sealed interface` は
実装をコンパイル単位内に制限し、`select` での網羅性チェックを有効にします。

### Records

```onion
record Point(x: Int, y: Int)
record Pair[A, B](first: A, second: B) <: SomeInterface
```

コンポーネントは private final フィールドになり、public アクセサ *メソッド* が
生成されます（`p.x()`）。`equals`/`hashCode`/`toString`/`copy` が生成されます。
`copy` は完全なクローン、位置引数と名前付き部分コピーをサポートします
（`p.copy(y = 9)`）。Records は `val (a, b) = p` と `select` パターンで
分解できます。

#### パターン付き records (`from re"..."`)

record は、コンポーネントリストの直後（`<:` スーパータイプの前）に正規表現リテラルを
付けることで、その形状から型付きパーサーを導出できます。`from` はソフトキーワードで、
直後に正規表現リテラルが続く場合にのみ認識されるため、通常の識別子としても使えます。

```onion
record Access(time: String, method: String, path: String, status: Int)
  from re"(\S+) (\w+) (\S+) (\d+)"
```

2つの static メソッドが合成されます。

- `Access::parse(s: String): Access?` — 文字列全体の ANCHORED マッチ。
  各キャプチャグループはコンポーネントの宣言型に変換され、成功時に record を
  返します。マッチしない場合 **または** 変換に失敗した場合（例: `status` が非数値）、
  `null` を返します — スマートキャストや elvis 演算子と合成可能です。
- `Access::parseAll(text: String): List[Access]` — `text` を行に分割し、
  各行をパースして `null` を落とし、マッチしたものを返します。

サポートされるコンポーネント型は `String`（恒等）と、ラッパーでパース可能な
プリミティブ `Int`、`Long`、`Double`、`Float`、`Boolean`、`Short`、`Byte` です。
キャプチャグループ数はコンポーネント数と等しく、正規表現は有効である必要があります —
両方ともコンパイル時にチェックされます（グループ/数の不一致は **E0060**、
不正な正規表現は **E0059**）。サポートされていないコンポーネント型は **E0061** です。

#### `derive!` — record serde 導出

record のコンポーネントリストの後（または `from re"..."` 節の後、`<:` スーパータイプの前）に
`derive!(Format, ...)` を追加すると、コンパイラは record の形状からシリアライズメソッドを
合成します。`!` 接尾辞は、型クラス制約ではなくマクロスタイルの展開を示します。

```onion
record User(name: String, age: Int) derive!(Json)
record Config(host: String, port: Int, debug: Boolean) derive!(Json, Yaml)
record Point(x: Int, y: Int) from re"(-?\d+),(-?\d+)" derive!(Json, Yaml)
  <: Printable
```

サポートされるマーカーは **`Json`** と **`Yaml`** です。認識できないマーカーは **E0063** です。
各マーカーについて、コンパイラは record に2つの static メソッドを合成します。

| マーカー | 合成されるメソッド | 合成されるメソッド |
|--------|-------------------|-------------------|
| `Json` | `R::fromJson(s: String): R?` | `R::toJson(v: R): String` |
| `Yaml` | `R::fromYaml(s: String): R?` | `R::toYaml(v: R): String` |

4つの形式はすべて、単一の内部 `toMap` / `fromMap` コアを共有します。`toJson` と `toYaml` は
両方とも `toMap` を呼び出し、結果の `Map` を `Json::stringify` または `Yaml::stringify` に
渡します。`fromJson` と `fromYaml` はテキストを中間 `Map` にパースし（`Json::parse` または
`Yaml::parse` 経由）、それを `fromMap` に渡します。つまり、コアシリアライズロジックは
一度だけ記述され、新しい形式を追加するには stdlib でその `parse` / `stringify` ペアを
登録するだけで済みます — マクロ自体は変更する必要がありません。

`from-R?` メソッドは、パース失敗または型変換エラー時に `null` を返し、例外は投げません。
`to-R` メソッドは常に整形式の文字列を生成します。
スカラー成分に対してはラウンドトリップが成立します: `fromJson(toJson(v)) == v`。

サポートされるコンポーネント型は8つのスカラー primitive です: `String`、`Int`、
`Long`、`Double`、`Float`、`Boolean`、`Short`、`Byte`。それ以外の型の成分は **E0062** です。
`derive!` は `from re"..."` と同じ record で共存できます。2つの機能は独立しています。

#### `law` / `example` — コンパイル時仕様チェック

record 宣言の後に `law` 節と `example` 節を続けることができます。コンパイラは
これらをビルド時に実行します（型チェック後に実行される `LawCheckPhase`）。
失敗したチェックは**コンパイルエラー**であり、実行時エラーではありません。

```onion
record Pt(x: Int, y: Int) from re"(-?\d+),(-?\d+)"
  law roundtrip(p: Pt) { Pt::parse(Pt::format(p)) == p }
  example { Pt::parse("3,4") == new Pt(3, 4) }

record User(name: String, age: Int) derive!(Json)
  law jsonRoundtrip(u: User) { User::fromJson(User::toJson(u)) == u }
  example { new User("ko", 3).name() == "ko" }
```

**`example { boolExpr }`** — 具体的なアサーション。コンパイラは `boolExpr` を評価し、
`true` を要求します。`false` の場合、**E0065** でコンパイルが失敗し、`false` を返した
式を示すメッセージが表示されます。

**`law name(p: T) { boolExpr }`** — 性質。コンパイラはパラメータ `p` のサンプル値のセット
（境界値、負数、ランダムケースをカバー）を生成し、それぞれで `boolExpr` をチェックします。
サンプルのいずれかが law を反証した場合、**E0064** でコンパイルが失敗し、
反例を含むレポートが表示されます。パラメータは `derive!` と同じスカラー型
（`String`、`Int`、`Long`、`Double`、`Float`、`Boolean`、`Short`、`Byte`）または
 record 型そのものにできます。

複数の `law` と `example` 節を1つの record に任意の順序で記述できます。これらは
`from re"..."` や `derive!(...)` と自由に共存します。

```onion
record R(x: Int, y: Int) from re"(-?\d+),(-?\d+)" derive!(Json)
  law textRoundtrip(r: R) { R::parse(R::format(r)) == r }
  law jsonRoundtrip(r: R) { R::fromJson(R::toJson(r)) == r }
  example { R::parse("0,0") == new R(0, 0) }
```

`law` / `example` の目的は、仕様 — 単なるドキュメントではなく、機械検証された
契約 — を言語そのものの中に持ち込むことです。上記の `parse∘format == id` という
不変条件は、すべてのコンパイル時にチェックされるため、ラウンドトリップを破壊する
文法変更は即座にビルドを失敗させます。

### Enums

```onion
enum Color { RED, GREEN, BLUE }

enum Planet(mass: Double) {
  MERCURY(3.3e23),
  EARTH(5.97e24)
public:
  def heavierThan(other: Planet): Boolean = this.mass() > other.mass()
}
```

Enums は JVM enum にコンパイルされます: `name()`、`ordinal()`、`values()`、
`valueOf(String)` が動作します。record スタイルのパラメータは final フィールドになり、
アクセサが生成されます。定数リストの後に `public:` などのセクションを書いてメソッドを
宣言できます。enum に対する `select` は、`else` がない場合に網羅性をチェックします。

### 拡張

```onion
extension String {
  def shout(): String { return this.toUpperCase() + "!" }
}
```

### 型エイリアス

```onion
type Names = List[String]
```

### 委譲

```onion
class MyClass <: Interface {
  forward val member: Interface
  ...
}
```

`forward` は、インターフェースのメソッドをメンバーに自動的に委譲します。

## 文と式

制御フロー構造は式です。ブロックは最後の式を評価し、`if`/`select`/`try` は値を生成し、
ループは `void` を評価し、`return`/`throw`/`break`/`continue` はボトム型です。

### 条件分岐

```onion
if cond { ... } else if cond2 { ... } else { ... }
val label = if ok { "yes" } else { "no" }
```

括弧付き条件内の代入が許可されます:
`while (line = reader.readLine()) != null { ... }`。

### ループ

```onion
while cond { ... }
do { ... } while cond                  // 本体優先
for var i = 0; i < n; i += 1 { ... }
foreach x: Type in collection { ... }
foreach i: Int in 0..<n { ... }
foreach (k, v) in map { ... }
break / continue
```

### Select（パターンマッチング）

```onion
select expr {
case 1, 2:                ...    // 値ケース（任意の式）
case Color::RED:          ...    // enum 定数（網羅性チェック）
case s is String:         ...    // 型パターン、s を狭めて束縛
case Circle(r) when r > 9: ...   // record 分解 + ガード
case Rect(w, _):          ...    // _ はコンポーネントを無視
case re"(\w+)@(\w+)" (u, h): ... // 正規表現パターン: キャプチャグループを束縛
else:                     ...
}
```

`sealed` インターフェースと enum に対するマッチングは、`else` がない場合に
網羅性をチェックします（E0042）。網羅的なマッチは式として使えます。

正規表現パターンは、`String` の対象に対して **アンカー付き** の正規表現リテラルで
マッチし（対象全体がマッチする必要がある）、キャプチャグループを `String` のローカルに
束縛します。ガードは束縛されたグループを使えます。パターンはリテラルなので、
コンパイル時に検証されます。不正な正規表現は E0059、キャプチャグループ/束縛数の
不一致は E0060 です。

### 例外

```onion
try { ... }
catch e: IllegalArgumentException | IllegalStateException { ... }  // 複数キャッチ
catch e: Exception { ... }
finally { ... }

try (val r = open(); val w = openOther()) { ... }   // try-with-resources
throw new IllegalStateException("message")
```

リソースは宣言の逆順で自動的にクローズされます。

### ラムダと関数値

```onion
val f: Int -> Int = x -> x * 2          // 裸パラメータ、式本体
val g = (a: Int, b: Int) -> a + b
list.map { x => x * 2 }                 // 末尾ラムダ
Future::async(() -> { return compute() })
val r: Runnable = () -> IO::println("hi")   // SAM 変換
```

### Do 記法

```onion
do[Option] { a <- getA(); b <- getB(); ret a + b }
do[Future] { x <- fetch(); ret x.size() }
do[List]   { x <- [1, 2]; y <- ["a", "b"]; ret x + y }   // 内包表記
```

`x <- e; rest` は `e.bind((x) -> rest)` に脱糖されます。`ret e2` の直前の束縛は
`e.map((x) -> e2)` になります。`bind`/`map` を持つ（インスタンスまたは拡張メソッド）
任意の型が do 記法で使えます: `Option`、`Result`、`Future`、`List`（内包表記）、
およびユーザー型。スクリプトのトップレベルでも動作します。

### スキーム接頭辞リテラル

```onion
val p    = re"\d+-\d+"                        // コンパイル済み Pattern（RAW: \\ エスケープなし）
val text = file"notes.txt".text()             // FileResource: text/lines/csv/csvRows/json/write/append
val rows = file"data.csv".csvRows()           // Map（ヘッダ -> 値）の List、RFC 4180
val body = http"https://api.example.com".get() // HttpResource: get/getJson/post/postJson/put/delete
```

`prefix"raw"` は、デフォルトの static import（`onion.Resources`）を通じて解決される
修飾なし呼び出し `prefix("raw")` の糖衣です。したがって、リテラル形式と
関数形式（動的値の場合の `file(path)`）は等価です。本体は raw です — バックスラッシュは
そのまま通過します。`\"` はバックスラッシュを消費せずに引用符をエスケープします。

### パイプライン演算子

```onion
5 |> double               // double(5)
5 |> add(3)               // add(5, 3) — 第1引数注入
xs.map { x => x * 2 }
  |> println              // |> の前の改行でパイプラインを継続
```

`e |> f(args...)` は `e` を第1引数として注入します。右辺は裸の関数名、
修飾なし呼び出し、またはメソッド/static 呼び出しにできます。優先順位は
代入と `||` の間です。

### 演算子

優先順位（高い順）:

1. メンバーアクセス / static アクセス / インデックス: `.` `?.` `::` `[]` `?[]`
2. 後置: `++` `--` `!!` / キャスト `as`、`$`
3. 単項: `!` `-` `+` `~`
4. 乗算: `*` `/` `%`
5. 加算: `+` `-`
6. シフト: `<<` `>>` `>>>`（コレクションでの `<<` は追加）
7. 関係: `<` `>` `<=` `>=` `is`
8. 等価: `==` `!=`（値）、`===` `!==`（参照）
9. ビット演算: `&` `^` `|`
10. 論理: `&&` `||`
11. Elvis: `?:`
12. 代入: `=` および複合代入 `+=` `-=` `*=` `/=` `%=` `&=` `|=` `^=` `<<=` `>>=` `>>>=`

オブジェクトの `==` は値の等価性（`equals`）。`===` は参照を比較します。
オペランドのいずれかが `String` の場合、`+` は文字列連結を行います。

## インポートとモジュール

```onion
module my.pkg

import {
  java.util.*
  java.lang.Long as JLong;
}
```

`onion.*`、`java.lang.*`、`java.io.*`、`java.util.*` はデフォルトでインポートされます
（`onion.*` が優先）。存在しないクラスの単一クラスインポートはコンパイルエラーです。

## エントリーポイント

`onion`/`runScript` のエントリーポイントは、順に: `main` メソッドを持つ明示的なクラス、
トップクラスの `main` メソッド、それ以外の場合は最初のトップレベル文です。
トップレベルの文、`def` 関数、`val`/`var` 宣言は暗黙のスクリプトを形成します。
`args: String[]` が利用可能です。

### Auto-CLI

パラメータが CLI 変換可能な型（`String`、`Int`、`Long`、`Double`、`Float`、
`Boolean`、`Short`、`Byte`）であるトップレベル `main` は、コマンドラインから
パースされた引数で自動的に呼び出されます。

```onion
def main(name: String, count: Int = 3, loud: Boolean = false): void { ... }
// $ onion greet.on world --count 5 --loud
```

必須パラメータは位置引数です。デフォルト付きパラメータは `--name` フラグになります
（`Boolean` のデフォルトはプレゼンススイッチになります）。値は宣言型に変換され、
フラグがない場合はデフォルト式が評価され、エラー時はシグネチャから使用法の行が
導出されます。`onion` コマンドラインでは、スクリプトファイル以降のすべてが
そのままスクリプトに渡されます。

## 警告

`--warn off|on|error` でレベルを設定します。`--Wno codes` で特定の警告を抑制します。
注目: W0006 未使用パラメータ、W0012 null リテラルが非 nullable 型に流れる。

## 現在の制限

1. ジェネリクスは消去ベース（変性や reified 型情報はなし）。ただし、型パラメータの
   null 性はコンパイル時に追跡される
2. 末尾呼び出し最適化は直接自己再帰をカバー（相互再帰最適化パスもある）。一般的な
   CPS は対象外
3. 一部の診断メッセージは理想的にはパイプラインのより早い段階で報告されるべき

## 文法リファレンス

完全な文法は `grammar/JJOnionParser.jj`（JavaCC）にあります。

## 次のステップ

- [標準ライブラリ](stdlib.md) - 組み込み関数とクラス
- [コンパイラアーキテクチャ](compiler-architecture.md) - コンパイラの仕組み
- [例](../examples/index.md) - コード例
