# 標準ライブラリ

Onionの標準ライブラリは、一般的な機能のための組み込みモジュールとインターフェースで構成されています。

## モジュール一覧

| 領域 | モジュール |
|------|-----------|
| **I/O・システム** | `IO`（コンソール）, `Files`（ファイル・パス）, `System`, `Proc`（サブプロセス）, `Args`（CLI） |
| **ネットワーク** | `Http`（HTTPクライアント）, `Net`（TCPソケット）, `Server`（HTTPサーバ） |
| **データストア** | `Db`（JDBC経由のSQL） |
| **アーカイブ** | `Archive`（zip・gzip） |
| **並行処理** | `Future`, `Concurrent`（プール・カウンタ・ロック・チャネル） |
| **コレクション** | `Colls`（リスト: map/filter/fold, chunked/windowed, sumBy/maxBy）, `Iterables`, `Maps`, `Sets` |
| **テキスト** | `Strings`（大小文字・分割・パディング・パース）, `Text`（wrap/indent/table）, `Regex` |
| **数値** | `Math`, `OnionMath`（双曲線関数, `clamp`, `hypot`, 範囲付き`randomInt`）, `Stats`（sum/average/median/stddev）, `Format`（桁区切り・bytes・duration） |
| **データ形式** | `Json`, `Yaml`, `Csv`, `Config`（ドット記法での設定値アクセス） |
| **エンコード** | `Codec`（base64/hex/url）, `Hash`（md5/sha256/…） |
| **関数型** | `Option`, `Result`, `Future`, `Outcome`・`Defect`（外部データの読み取り） |
| **位置情報** | `Origin`（値がどのテキストのどこから来たか） |
| **境界** | `Shape`・`Shapes`（テキスト↔型付き値）, `Scalars` |
| **日時・乱数** | `DateTime`, `Rand`（choice/shuffle/sample/uuid） |
| **テスト・計測** | `Assert`, `Timing` |

ほとんどのヘルパーは静的な `Module::` 呼び出しだけでなくメソッドチェインでも書けます——
コレクション（`list.filter { ... }.map { ... }`, `m.mapValues { ... }`）、文字列
（`"s".capitalize()`）、ハッシュ・エンコード（`"pw".sha256()`, `"x".base64Encode()`）、
テキスト整形（`text.wrap(40)`）、数値集計（`nums.sum()`, `nums.average()`）、数値フォーマット
（`(1536L).bytes()`, `(21L).ordinal()`）。

## IO モジュール

コンソール入出力操作。

### IO::println

標準出力に改行付きで出力：

```onion
IO::println("Hello, World!")
IO::println("値: " + value)
```

### IO::print

改行なしで出力：

```onion
IO::print("名前を入力: ")
val name: String = IO::readln()
```

### IO::readln

ユーザーから1行の入力を読み取り：

```onion
val name: String = IO::readln("名前は？ ")
IO::println("こんにちは、" + name)
```

`IO::input(prompt)` はこの名前で直接呼び出せる同じ操作 -- `readln(prompt)` は
内部で `input` を呼び出す実装：

```onion
val name: String = IO::input("名前は？ ")
```

### IO::readLine

標準入力から1行読み取り、入力の終端では `null` を返す。プロンプトなしの
`IO::readln()` はこのメソッドの別名：

```onion
val line: String? = IO::readLine()
```

### IO::readAll

残りの標準入力全体を1つの文字列として読み取り：

```onion
val everything: String = IO::readAll()
```

### フォーマット出力

```onion
IO::printf("%s is %d\n", "age", 30)
val s: String = IO::format("%.2f", 3.14159)
```

### エラー出力（stderr）

```onion
IO::eprint("warning: ")
IO::eprintln("disk almost full")
IO::eprintf("failed after %d retries\n", 3)
```

### 型安全な入力

1行を指定の型として読み取ってパースし、不正な入力なら例外を投げる。各メソッドには
先にプロンプトを表示するオーバーロードがある：

```onion
val age: Int = IO::readInt("Age: ")
val price: Long = IO::readLong("Price: ")
val ratio: Double = IO::readDouble("Ratio: ")
val ok: Boolean = IO::readBoolean("Continue? ")  // true/yes/1、false/no/0 を受け付け
```

### 安全な入力

上記の型安全な読み取りと同様だが、不正な入力や入力終端では例外を投げず
`null` を返す：

```onion
val n: Int? = IO::tryReadInt("N: ")
val d: Double? = IO::tryReadDouble("D: ")
val l: Long? = IO::tryReadLong("L: ")
```

### 行単位の入出力

```onion
val lines: List = IO::readLines()          // 入力の終端まで読み取り
IO::eachLine { line -> IO::println(line) } // 残りの各行にコールバックを適用
IO::printLines(["a", "b", "c"])            // 1項目1行で出力
IO::printAll("a", "b", "c")                // printLines の可変長引数版
```

### ユーティリティ

```onion
IO::flush()    // 標準出力をフラッシュ
IO::newline()  // 空行を出力
IO::clear()    // ターミナル画面をクリア（ANSIエスケープコード）
```

## Math モジュール

Javaの`Math`クラス経由の数学演算。

### Math::random

0.0から1.0の乱数を生成：

```onion
val r: Double = Math::random()
val randomInt: Int = (Math::random() * 100) as Int
```

### Math::sqrt

平方根：

```onion
val result: Double = Math::sqrt(16.0)  // 4.0
```

### Math::pow

累乗：

```onion
val result: Double = Math::pow(2.0, 3.0)  // 8.0
```

### Math::abs

絶対値：

```onion
val abs1: Int = Math::abs(-10)     // 10
val abs2: Double = Math::abs(-3.14)  // 3.14
```

### Math::max / Math::min

最大値と最小値：

```onion
val max: Int = Math::max(10, 20)    // 20
val min: Int = Math::min(10, 20)    // 10
```

### Math::floor / Math::ceil / Math::round

丸め処理：

```onion
val floor: Double = Math::floor(3.7)  // 3.0
val ceil: Double = Math::ceil(3.2)    // 4.0
val round: Long = Math::round(3.5)    // 4
```

### Math::sin / Math::cos / Math::tan

三角関数（ラジアン）：

```onion
val sine: Double = Math::sin(Math::PI / 2)    // 1.0
val cosine: Double = Math::cos(0.0)           // 1.0
val tangent: Double = Math::tan(Math::PI / 4) // 1.0
```

### Math Constants

```onion
val pi: Double = Math::PI       // 3.14159...
val e: Double = Math::E         // 2.71828...
```

## OnionMath モジュール

JDKの`Math`とは別の`onion.*`数値モジュールで、双曲線関数、安全な丸め・クランプ、範囲付きの
乱数整数を提供します。標準ライブラリの他のモジュールと同様デフォルトでインポートされるため、
明示的なインポートは不要です。

### OnionMath::sin / OnionMath::cos / OnionMath::tan / OnionMath::asin / OnionMath::acos / OnionMath::atan / OnionMath::atan2

三角関数と逆三角関数（ラジアン）：

```onion
val sine: Double = OnionMath::sin(OnionMath::PI / 2)     // 1.0
val angle: Double = OnionMath::atan2(1.0, 1.0)           // pi/4
```

### OnionMath::sinh / OnionMath::cosh / OnionMath::tanh

双曲線関数：

```onion
val h: Double = OnionMath::sinh(1.0)
```

### OnionMath::exp / OnionMath::log / OnionMath::log10

指数関数と対数関数：

```onion
val e2: Double = OnionMath::exp(1.0)     // e
val l: Double = OnionMath::log(OnionMath::E)   // 1.0
val l10: Double = OnionMath::log10(100.0)      // 2.0
```

### OnionMath::pow / OnionMath::sqrt / OnionMath::cbrt

累乗と平方根・立方根：

```onion
val cube: Double = OnionMath::pow(2.0, 3.0)  // 8.0
val root: Double = OnionMath::sqrt(16.0)     // 4.0
val croot: Double = OnionMath::cbrt(27.0)    // 3.0
```

### OnionMath::abs / OnionMath::absFloat / OnionMath::absInt / OnionMath::absLong

プリミティブ型ごとの絶対値：

```onion
val a1: Double = OnionMath::abs(-3.14)
val a2: Int = OnionMath::absInt(-10)      // 10
val a3: Long = OnionMath::absLong(-10L)   // 10
```

### OnionMath::min / OnionMath::minInt / OnionMath::minLong / OnionMath::max / OnionMath::maxInt / OnionMath::maxLong

プリミティブ型ごとの最小値・最大値：

```onion
val lo: Int = OnionMath::minInt(10, 20)   // 10
val hi: Int = OnionMath::maxInt(10, 20)   // 20
```

### OnionMath::floor / OnionMath::ceil / OnionMath::round / OnionMath::roundFloat

丸め処理：

```onion
val f: Double = OnionMath::floor(3.7)     // 3.0
val c: Double = OnionMath::ceil(3.2)      // 4.0
val r: Long = OnionMath::round(3.5)       // 4
val rf: Int = OnionMath::roundFloat(3.5f) // 4
```

### OnionMath::random / OnionMath::randomInt

乱数生成。`Math::random`と異なり、`randomInt`は範囲を直接指定でき、エフェクトチェッカーに
よって`Rand`エフェクトとして追跡されます：

```onion
val r: Double = OnionMath::random()          // [0.0, 1.0)
val n: Int = OnionMath::randomInt(1, 10)     // [1, 10]（両端含む）
```

### OnionMath::signum / OnionMath::signumFloat

数値の符号（`-1.0`、`0.0`、または`1.0`）：

```onion
val s: Double = OnionMath::signum(-5.0)   // -1.0
```

### OnionMath::toRadians / OnionMath::toDegrees

角度単位の変換：

```onion
val rad: Double = OnionMath::toRadians(180.0)  // pi
val deg: Double = OnionMath::toDegrees(OnionMath::PI)  // 180.0
```

### OnionMath::clamp / OnionMath::clampInt

値を範囲内に収める：

```onion
val c1: Double = OnionMath::clamp(15.0, 0.0, 10.0)  // 10.0
val c2: Int = OnionMath::clampInt(-5, 0, 10)        // 0
```

### OnionMath::hypot

オーバーフロー・アンダーフローを避けた斜辺の長さ：

```onion
val h: Double = OnionMath::hypot(3.0, 4.0)  // 5.0
```

### OnionMath Constants

```onion
val pi: Double = OnionMath::PI  // 3.14159...
val e: Double = OnionMath::E    // 2.71828...
```

## Origin

値がどのテキストのどこから来たかを表します。コンパイラが持つソース位置の、実行時版です。
12行目で失敗したと分かっているパーサが、`null` を返す代わりにそう言えるようになります。

`source` は自由形式です——ファイルパス、URL、`"<stdin>"`、`"<literal>"` など。行と列は
1始まりです。列が `0` の場合は「行までしか分からない」ことを意味します。行単位のパーサが
正直に報告できるのはそこまでだからです。

```onion
import { onion.Origin; }

val o = Origin::at("access.log", 12, 5)
println(o.describe())          // access.log:12:5

val lineOnly = Origin::atLine("data.json", 4)
println(lineOnly.describe())   // data.json:4
println(lineOnly.hasColumn())  // false
```

### Origin::at / Origin::atLine / Origin::spanning

`at(source, line, column)` は1文字分、`atLine(source, line)` は列なしの行、
`spanning(source, line, column, span)` は `span` 文字分を表します。

### origin.onLine / origin.inSource

文書を行単位でパースすると、各行のパース結果はその行を基準にした位置を報告します。
`onLine` はそれを文書全体の位置に持ち上げ、`inSource` は別のソースに付け替えます。

```onion
Origin::at("log.txt", 1, 3).onLine(40).describe()   // log.txt:40:3
```

### origin.describe

`file:line:column`、行しか分からない場合は `file:line` を返します。コンパイラもエディタも
既に解釈できる形式です。`toString` も同じ結果を返します。

## Outcome と Defect

外部データを読んだ結果です。値か、あるいは**読めなかった理由すべて**を表します。
`Defect` は「1つの不具合」、`Outcome[T]` は「値、またはその一覧」です。

`Defect` は呼び出し側が実際に知りたい3点に答えます——テキストのどこか（`origin`、無い場合
もある）、値のどこか（`path`）、そして何を期待して何があったか。

```onion
import { onion.Outcome; onion.Defect; onion.Origin; }

val d = Defect::at(Origin::atLine("config.json", 4), "port", "Int", "\"http\"")
println(d.describe())     // config.json:4: port: expected Int, found "http"

val missing = Defect::of("name", "String", "absent")
println(missing.describe())   // name: expected String, found absent
```

### なぜ Result ではないのか

`zip` のためです。`Result` はモナドで `bind` は短絡するため、最初の不正フィールドが残りを
隠してしまいます。3つのフィールドが同時に壊れているなら、1回で3件報告すべきです。

```
Ok(f)   zip Ok(x)   = Ok(f(x))
Bad(d1) zip Ok(_)   = Bad(d1)
Ok(_)   zip Bad(d2) = Bad(d2)
Bad(d1) zip Bad(d2) = Bad(d1 ++ d2)     <- この型が存在する理由
```

```onion
val a: Outcome[JInteger] = Outcome::bad(Defect::of("x", "Int", "p"))
val b: Outcome[JInteger] = Outcome::bad(Defect::of("y", "Int", "q"))
println(a.zip(b) { p, q -> p + q }.defects().size)   // 1 ではなく 2
```

`bind` は短絡したままです——後続の計算が前の値に依存しうる以上、そうでなければなりません。
両方使えます。`do[Outcome]` は `bind` を使います。

### まとめて読む

`all` は全部か無かで、全 defect を集約します。部分的な結果に価値がある場合——良い行が
意味を持つログファイルなど——は `values` と `defects` で両方を取れます。

```onion
val os: List[Outcome[JInteger]] =
  [Outcome::ok(1), Outcome::bad(Defect::of("a", "Int", "x")), Outcome::ok(3)]

println(Outcome::values(os).size)    // 2
println(Outcome::defects(os).size)   // 1
println(Outcome::all(os).isOk())     // false
```

### ネストや行単位の読み取り

`under` は各 defect の path に接頭辞を付け、`onLine` はある行を基準に報告された位置を
文書全体の位置に持ち上げます。

```onion
o.under("address")     // "city" が "address.city" になる
o.onLine(40)           // 断片の1行目の defect が、ファイルの40行目になる
```

## Shape

外部テキストと型付き値の、部分的かつ（可能なら）双方向の対応です。`Shape[T]` はテキストを
`T` として読み、対応が可逆なら書き戻せます。

```onion
import { onion.Shape; onion.Shapes; onion.Outcome; }

val r = pointShape.parse("3,4")
if r.isOk() { println(r.get()) }
println(pointShape.print(pt))
```

### 意図的に区別している2つの法則

```
L1  往復    parse(print(v)) == Ok(v)     print がある限り保証される
L2  正規化  print(parse(t)) == t         一般には成り立たない
```

L2 が破れるのはごく普通の理由です——`"007"` は正しい `Int` ですが、書き戻すと `"7"` になります。
L2 も満たす shape を *lossless* と呼び、これは稀で、lens が必要とするものです。多くの shape は
L1 のみで、どちらなのかを明言することが「可逆な言語」と「可逆だと主張する言語」の差です。

### canPrint

すべての shape が書き戻せるわけではありません。`\s+` を区切りに使う正規表現には一意な
書き戻し方が無いため read-only になり、`canPrint()` が `print` を呼ぶ前にそう伝えます——
メソッドが黙って存在しない、という形にはしません。

### 成分の失敗は蓄積する

`"abc,def"` から `Int` を2つ読むと defect は**2件**報告されます。最初の1件ではありません。
`Outcome` の蓄積する `zip` はそのためにあります。

### Lossless shape と lens

L2 も満たす shape を *lossless* と呼びます——`isLossless()` がそれを伝え、
`parseLossless(text[, origin])` は素の `T` の代わりに `Lossless[T]` を読みます:値と、
その周辺すべての `Residue`（コメント、空白、キーの順序、元の値の書き方）です。
`printLossless(value, residue)` はその residue を通して書き戻します——変更していない
部分はバイト単位で再現され、意図的に変更した値だけが書き直されます。`Residue` は
不透明な値で、生成した shape にだけ渡し戻してください。

`Lossless[T]` そのものが lens です:`value()`/`residue()` でペアを読み、
`withValue(v)` は residue を保ったまま値だけ差し替え、`edit { v -> ... }` は更新を
値にフォーカスします。`render()` がテキストを再構成します:

```onion
val r   = configShape.parseLossless(file"app.conf".text()).get()
val out = r.edit { v -> v.copy(port = 9090) }.render()
// diff app.conf out  ->  1行だけ変わる
```

`Shapes::regex` と `Shapes::json` は `shape name = re"..."` / `shape name = json` の
裏にある shape を、`Shapes::config` と `Shapes::yaml` は `shape name = config` /
`shape name = yaml` の糖衣構文の裏にある lossless shape を、それぞれ `Shape[T]` の値として
直接組み立てます。

### コンビネータ

- `eachLine(text[, origin])` — 1行ごとに `Outcome[T]` を返し、読めた行と読めなかった
  行の defect の両方を保持します（`Outcome::values`/`Outcome::defects` で分離できます）。
  ログファイルのように大半の行が読めるケースなど、部分的な結果に意味がある場合は
  `lines()` よりこちらを使います。
- `lines()` — 1行1値、全部読めるか失敗するかの `Shape[List[T]]` です。
- `sepBy(separator)` — リテラルな区切り文字で分割する、全部読めるか失敗するかの
  `Shape[List[T]]` です。
- `xmap(forward, backward)` — 同型写像に沿って shape を運びます。`print` が黙って
  壊れないよう、両方向の関数が必要です。
- `orElse(other)` — この shape、読めなければ `other`。どちらも読めない場合は両方の
  defect を報告します。印字はこの shape で行います。

## Scalars モジュール

境界（外部データを読み込む場所）向けの、厳格なスカラー変換です。`record ... from
re"..."` や `shape` が生成するコードから使われるほか、直接呼び出すこともできます。
JDK 自身のパーサが緩すぎてそのままでは使えない場面のためのものです。

### なぜ `Boolean::parseBoolean` ではないのか

`java.lang.X.parseX` はどれも不正な入力を例外で拒否します -- `Boolean::parseBoolean`
だけが例外で、`"true"` 以外のすべてを `false` に変換してしまいます。これはパーサが
絶対にやってはいけない失敗の仕方です。`"maybe"`・`"yes"`・`"1"` がすべて `false` に
なり、データが不正だったことを示すものが何も残りません。

```onion
Scalars::toBoolean("TRUE")     // true
Scalars::toBoolean("false")    // false
Scalars::toBoolean("yes")      // IllegalArgumentException が発生
Scalars::isBoolean("yes")      // false -- toBoolean を呼ぶ前に確認できる
```

`toBoolean` は `IllegalArgumentException` を投げます。これは数値パーサが投げる
`NumberFormatException` の親クラスなので、派生コードは両方を同じ方法で捕捉できます。

### Scalars::read

`text` を `tag` で指定したスカラー種別（`String`・`Int`・`Long`・`Double`・`Float`・
`Boolean`・`Short`・`Byte` のいずれか）として読み取り、例外ではなく位置情報付きの
`Defect` として報告します。

```onion
import { onion.Scalars; onion.Outcome; }

val port: Outcome[Object] = Scalars::read("Int", "8080", null, "port")
println(port.get())                                    // 8080

val bad: Outcome[Object] = Scalars::read("Int", "http", null, "port")
println(bad.defects().get(0).describe())                // port: expected Int, found "http"
```

`origin`（`Origin` または `null`）は defect をソーステキスト上の位置に結び付け、
`path` は構築中の値のどこに該当するフィールドかを示します。

### Scalars::coerce

`Json`/`Yaml` などで既にパース済みのドキュメント値を、`tag` で指定したスカラー種別に
変換します。`read` と異なり値は既に型付きで渡ってくるため -- JSON の数値はすでに
`Number` になっている -- これはパースではなく絞り込みです。形そのものが違う値
（`Int` が必要な場所に文字列がある等）は、黙って `null` になるのではなく defect に
なります。

```onion
Scalars::coerce("Int", 8080, null, "port")        // Outcome::ok(8080)
Scalars::coerce("Int", "8080", null, "port")      // Outcome::ok(8080) -- 数値文字列も読める
Scalars::coerce("Int", [1, 2], null, "port")      // defect: expected Int, found an array
```

`read` と `coerce` はどちらもコンパイラ自身のスカラー変換テーブルと同じタグの語彙を
使うため、`shape`/`from re"..."` による導出と `Scalars` を直接使う手書きコードは、
同じ方法で defect を報告します。

## 関数インターフェース

ラムダとクロージャのための組み込み関数型。`f(args)`の代わりに`f(args)`として呼び出せます。

### Function0

パラメータなしの関数：

```onion
val func: Function0[Int] = () -> { return 42; }
val result: Int = func()
```

### Function1

1パラメータの関数：

```onion
val double: Function1[Int, Int] = (x: Int) -> { return x * 2; }
val result: Int = double(5)
```

### Function2

2パラメータの関数：

```onion
val add: Function2[Int, Int, Int] = (x: Int, y: Int) -> { return x + y; }
val result: Int = add(3, 7)
```

### Function3 から Function10 まで

3〜10パラメータの関数も同じパターンです。

## ラッパークラス

プリミティブ型に対応するJavaのラッパークラス（文脈によっては`J`接頭辞でアクセス）。

### JInteger

Integer操作:

```onion
val i: Int = JInteger::parseInt("42")
val s: String = JInteger::toString(42)
val max: Int = JInteger::MAX_VALUE
val min: Int = JInteger::MIN_VALUE
```

### JLong

Long操作:

```onion
val l: Long = JLong::parseLong("1234567890")
val s: String = JLong::toString(1234567890L)
```

### JDouble

Double操作:

```onion
val d: Double = JDouble::parseDouble("3.14")
val s: String = JDouble::toString(3.14)
```

### JBoolean

Boolean操作:

```onion
val b: Boolean = JBoolean::parseBoolean("true")
val s: String = JBoolean::toString(true)
```

## よく使うJavaクラス

よく使われるJava標準ライブラリのクラス。

### String

文字列操作（自動的に利用可能）:

```onion
val text: String = "Hello, World!"
val upper: String = text.toUpperCase()
val lower: String = text.toLowerCase()
val length: Int = text.length()
val sub: String = text.substring(0, 5)
val contains: Boolean = text.contains("World")
val starts: Boolean = text.startsWith("Hello")
val ends: Boolean = text.endsWith("!")
```

### StringBuilder

効率的な文字列構築:

```onion
import { java.lang.StringBuilder; }

val builder: StringBuilder = new StringBuilder()
builder.append("Hello")
builder.append(" ")
builder.append("World")
val result: String = builder.toString()
```

### ArrayList

動的配列:

```onion
import { java.util.ArrayList; }

val list: ArrayList[String] = new ArrayList[String]
list.add("First")
list << "Second"  // <<演算子を使用
val size: Int = list.size()
val item: Object = list.get(0)
list.remove(0)
val empty: Boolean = list.isEmpty()
```

### HashMap

キーバリューマップ:

```onion
import { java.util.HashMap; }

val map: HashMap[String, String] = new HashMap[String, String]
map.put("key1", "value1")
map.put("key2", "value2")
val value: Object = map.get("key1")
val has: Boolean = map.containsKey("key1")
val size: Int = map.size()
```

### File

ファイル操作:

```onion
import { java.io.File; }

val file: File = new File("data.txt")
val exists: Boolean = file.exists()
val isFile: Boolean = file.isFile()
val isDir: Boolean = file.isDirectory()
val name: String = file.getName()
val path: String = file.getPath()
val length: Long = file.length()
```

### BufferedReader

テキストの読み取り:

```onion
import {
  java.io.BufferedReader;
  java.io.FileReader;
}

val reader: BufferedReader = new BufferedReader(
  new FileReader("file.txt")
)

var line: String = null
while (line = reader.readLine()) != null {
  IO::println(line)
}

reader.close()
```

### BufferedWriter

テキストの書き込み:

```onion
import {
  java.io.BufferedWriter;
  java.io.FileWriter;
}

val writer: BufferedWriter = new BufferedWriter(
  new FileWriter("output.txt")
)

writer.write("Hello, World!")
writer.newLine()
writer.close()
```

## Rand モジュール

`onion.Rand`による乱数生成ユーティリティ。

### Rand::nextInt / nextLong / nextDouble / nextBoolean

乱数を生成：

```onion
val randomInt: Int = Rand::nextInt()            // ランダムなInt
val randomLong: Long = Rand::nextLong()         // ランダムなLong
val randomDouble: Double = Rand::nextDouble()   // 0.0から1.0
val randomBool: Boolean = Rand::nextBoolean()   // ランダムなBoolean
```

### Rand::nextInt（範囲指定）

範囲内の乱数整数を生成：

```onion
val dice: Int = Rand::nextInt(6) + 1      // 1から6
val percent: Int = Rand::nextInt(100)     // 0から99
val d20: Int = Rand::nextInt(1, 21)       // 1から20（min, 排他的max）
```

### Rand::nextLong（範囲指定）

範囲内の乱数longを生成：

```onion
val bigId: Long = Rand::nextLong(1000000L)   // 0から999999
```

### Rand::nextDouble（範囲指定）

```onion
val small: Double = Rand::nextDouble(10.0)         // 0.0から10.0
val ranged: Double = Rand::nextDouble(1.0, 2.0)    // 1.0から2.0
```

### Rand::choice

リストからランダムに1要素を選ぶ：

```onion
val colors: List[String] = ["red", "green", "blue"]
val picked: String = Rand::choice(colors)
```

### Rand::shuffle

リストをその場でシャッフル：

```onion
import { java.util.ArrayList; }

val list: ArrayList[String] = new ArrayList[String]()
list.add("A")
list.add("B")
list.add("C")
Rand::shuffle(list)  // その場でシャッフル
```

### Rand::sample

リストから重複なくn個の要素をランダムに選ぶ：

```onion
val deck: List[String] = ["A", "B", "C", "D", "E"]
val hand: List[String] = Rand::sample(deck, 3)   // 重複しない3枚
```

### Rand::uuid

ランダムなUUID文字列を生成：

```onion
val id: String = Rand::uuid()   // 例: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
```

## Assert モジュール

`onion.Assert`によるテストアサーション。失敗時に`AssertionError`をスロー。

### 基本アサーション

```onion
Assert::isTrue(x > 0)
Assert::isFalse(list.isEmpty())
Assert::equals(expected, actual)
Assert::notEquals(a, b)
```

### Nullアサーション

```onion
Assert::notNull(result)
Assert::isNull(errorMessage)
```

### 明示的な失敗

```onion
if invalidState {
  Assert::fail("ここに到達すべきではない")
}
```

## Timing モジュール

`onion.Timing`による時間計測ユーティリティ。

### 現在時刻の取得

```onion
val startNanos: Long = Timing::nanos()     // 高精度 (System.nanoTime)
val startMillis: Long = Timing::millis()   // 壁時計 (System.currentTimeMillis)
```

### 経過時間の計測

```onion
val start: Long = Timing::nanos()
// ... 何らかの処理 ...
val elapsedNs: Long = Timing::elapsedNanos(start)      // ナノ秒での経過時間
val elapsedMs: Double = Timing::elapsedMs(start)       // ミリ秒での経過時間（サブミリ秒精度のdouble）
val elapsedMillis: Long = Timing::elapsedMillis(start) // Timing::millis()起点のミリ秒での経過時間
```

### 時間のフォーマット

```onion
val nanos: Long = 1234567890L
val formatted: String = Timing::formatNanos(nanos)   // "1.23s"
// 出力形式: "123ns", "45.67μs", "12.34ms", "1.23s"

val millis: Long = 125000L
val formattedMs: String = Timing::formatMillis(millis)  // "2m5s"
// 出力形式: "500ms", "1.23s", "2m30s"
```

### スリープ

```onion
Timing::sleep(1000L)        // 1000ミリ秒スリープ
Timing::sleepNanos(500000L) // 500,000ナノ秒スリープ
```

### 関数実行時間の計測

```onion
// 実行時間を計測して表示し、結果を返す
val result: Int = Timing::measure(() -> { return expensiveOperation(); })
// 出力: "Elapsed: 123.45ms"
val result2: Int = Timing::measure("task", () -> { return expensiveOperation(); })
// 出力: "task: 123.45ms"

// 戻り値のない関数版
Timing::measureVoid(() -> { expensiveOperation(); })
// 出力: "Elapsed: 123.45ms"
Timing::measureVoid("task", () -> { expensiveOperation(); })
// 出力: "task: 123.45ms"

// 表示なしで実行時間（ナノ秒）を取得
val timeNanos: Long = Timing::time(() -> { return expensiveOperation(); })
```

## Option モジュール

`onion.Option`で提供。

- `Option::some(value)` / `Option::none()` / `Option::of(value)`
- `opt.isDefined()` / `opt.isEmpty()` / `opt.get()` — `get()` は `None` の場合 `NoSuchElementException` を投げる
- `opt.getOrElse(defaultValue)` / `opt.orElseGet(() -> default)` / `opt.orNull()`
- `opt.orElseThrow()` / `opt.orElseThrow(() -> customException)`
- `opt.orElse(otherOption)`
- `opt.map(f)` / `opt.flatMap(f)` / `opt.filter(predicate)` / `opt.forEach(action)`
- `opt.contains(value)` / `opt.exists(predicate)`
- `opt.fold(() -> ifEmpty, v -> ifPresent)` — 単一の値へ畳み込む
- `opt.toList()` — 0個または1個の要素のリスト

## Result モジュール

`onion.Result`で提供。

- `Result::ok(value)` / `Result::err(error)`
- `Result::ofNullable(value, errorIfNull)` / `Result::trying(operation)`
- `res.isOk()` / `res.isErr()` / `res.get()` / `res.getError()` — `get()` は `Err` で、`getError()` は `Ok` で例外を投げる
- `res.map(f)` / `res.mapError(f)` / `res.flatMap(f)` / `res.toOption()`
- `res.getOrElse(default)` / `res.orElseGet(() -> default)` / `res.orNull()`
- `res.getOrThrow()` / `res.getOrThrow(e -> customException)` — エラーを（`Throwable` でなければラップして）投げる、またはマッピングした例外を投げる
- `res.forEach(action)` / `res.forEachError(action)`
- `res.fold(e -> ifErr, v -> ifOk)` — 単一の値へ畳み込む
- `res.recover(e -> value)` / `res.recoverWith(e -> otherResult)` — `Err` を回復
- `res.exists(predicate)` / `res.toList()`

## Future モジュール

`onion.Future`で提供。非同期計算を表現。

### Futureの作成

```onion
// 値で完了済み
val done: Future[Int] = Future::successful(42)

// 失敗で完了済み
val fail: Future[Int] = Future::failed(new RuntimeException("error"))

// バックグラウンドスレッドで非同期実行
val async: Future[String] = Future::async(() -> { return compute(); })

// 例外処理付きの非同期実行
val safe: Future[Int] = Future::asyncThrowing(() -> {
  return riskyOperation();
})

// 遅延
val delayed: Future[Void] = Future::delay(1000L)  // 1秒
```

### 変換メソッド

```onion
val f: Future[Int] = Future::successful(10)

// 値を変換
f.map((x: Int) -> { return x * 2; })  // Future[Int] = 20

// 非同期操作をチェイン
f.flatMap((x: Int) -> { return Future::successful(x + 1); })

// フィルタ（述語が false なら失敗）
f.filter((x: Int) -> { return x > 0; })

// flatMap の別名（do記法が使用）
f.bind((x: Int) -> { return Future::successful(x); })
```

### エラーハンドリング

```onion
val f: Future[Int] = Future::failed(new RuntimeException("oops"))

// 値で回復
f.recover((e: Throwable) -> { return 0; })

// 別の Future で回復
f.recoverWith((e: Throwable) -> { return Future::successful(42); })

// エラーを変換
f.mapError((e: Throwable) -> { return new CustomException(e); })
```

### コールバック

```onion
val f: Future[String] = Future::async(() -> { return "result"; })

f.onSuccess((value: String) -> { IO::println(value); })
f.onFailure((error: Throwable) -> { IO::println(error); })
f.onComplete(
  (value: String) -> { IO::println("ok: " + value); },
  (error: Throwable) -> { IO::println("err: " + error); }
)
```

### ブロッキング操作

```onion
val f: Future[Int] = Future::successful(42)

f.await()              // ブロックして結果を取得（失敗時は例外）
f.awaitTimeout(5000L)  // タイムアウト付きでブロック（ミリ秒）
f.getOrElse(0)         // 結果を取得、失敗時はデフォルト値
```

### ステータス照会

```onion
f.isCompleted()  // 完了していれば true（成功・失敗いずれも）
f.isSuccess()    // 成功で完了していれば true
f.isFailure()    // エラーで完了していれば true
```

これらは**非ブロッキング**です——future の*現在の*状態を報告するだけなので、まだ実行中の
future は `isSuccess()` と `isFailure()` の両方が `false` を返します。結果を待ちたい場合は、
`isFailure()` をポーリングするのではなく `await()`/`getOrElse()`（あるいは
`onSuccess`/`onFailure`/`recover`）を使ってください。

### Futureの結合

```onion
val f1: Future[Int] = Future::successful(1)
val f2: Future[Int] = Future::successful(2)

// タプルのような配列へまとめる
f1.zip(f2)  // Future[List[Object]] = [1, 2]

// レース: 先に完了した方が勝つ
f1.race(f2)

// すべての完了を待つ
Future::all(f1, f2, f3)  // Future[List[Object]] = [1, 2, 3]

// 最初に完了したもの
Future::first(f1, f2, f3)
```

### 変換

```onion
val f: Future[Int] = Future::successful(42)

f.toOption()   // Option[Int] - Some(42) または None（ブロックする）
f.toResult()   // Result[Int, Throwable]（ブロックする）
f.underlying() // 相互運用のための Java CompletableFuture

// 逆方向: Java の CompletableFuture を Future でラップする
val cf: java.util.concurrent.CompletableFuture[Int] = someJavaApi()
val wrapped: Future[Int] = Future::fromCompletableFuture(cf)
```

### Do記法サポート

Futureは順次非同期合成のためのdo記法で動作：

```onion
val result: Future[Int] = do[Future] {
  x <- Future::async(() -> { return fetchA(); })
  y <- Future::async(() -> { return fetchB(x); })
  ret x + y
}
```

## Json モジュール

JSON のパースとシリアライズ。中間表現は Java の Map / List / scalar（String / Long / Double / Boolean / null）です。

### Json::parse / Json::stringify

```onion
val obj = Json::parse("{\"name\":\"ko\",\"age\":3}")   // Object（実体は Map）
val name = Json::getString(obj, "name")                // "ko"
val age = Json::getInt(obj, "age")                     // 3

val m = Json::object()                                  // 空の Map
m.put("x", 1)
val text = Json::stringify(m)                           // {"x":1}
val pretty = Json::stringifyPretty(m)                   // インデント付きで整形
val a = Json::array()                                   // 空の List（JSON 配列値の構築に使う）
```

`getString` / `getInt` / `getLong` / `getDouble` / `getFloat` / `getBoolean` / `getShort` / `getByte` でキーから型別に取得します（見つからない・型不一致のときは null）。

これらはボックス化された値を返すため、見つからない場合の null をそのまま非 null なプリミティブへ代入すると NullPointerException になります。`getStringOr` / `getIntOr` / `getLongOr` / `getDoubleOr` / `getFloatOr` / `getBooleanOr(obj, key, default)` はフォールバック値付きでプリミティブを返すので、キーが無くても NPE になりません:

```onion
val obj = Json::parse("{}")
Json::getIntOr(obj, "missing", 42)      // 42（NPE にならない）
Json::getStringOr(obj, "name", "anon")  // "anon"
```

### Json::value（ナビゲート可能なラッパー）

`Json::value(text)` は `[]` でインデックスアクセスできるラッパー値を返します。オブジェクトのキーには文字列、
配列の要素には整数でアクセスでき、値の取り出しには `asString()` / `asInt()` / `asLong()` / `asDouble()` /
`asBoolean()` を使います:

```onion
val v = Json::value(jsonText)
v["users"][0]["name"].asString()
```

キーが存在しない・添字が範囲外のときは null を保持する `Value` を返すので、途中の欠損があっても例外にはなりません
（末尾で `asString()` 等を呼ぶと `null` になります）。`isNull()` で null かどうか、`size()` で配列・オブジェクトの
要素数（それ以外は `0`）を調べられ、`raw()` で内部表現（`Map`/`List`/scalar/`null`）を直接取り出せます。

`Json::parseOrNull(json)` は `Json::parse(json)` と同じですが、不正な入力に対して例外
`Json.JsonParseException` を投げる代わりに `null` を返します。パース失敗を別扱いのエラーではなく
単なる「値が無い」ケースとして扱いたいときに便利です:

```onion
val obj = Json::parseOrNull("not json")   // 例外を投げず null
```

不正入力の失敗をその場で処理したい場合、`Json.JsonParseException` は通常の `message()` に加えて
`getPosition()`（パースを諦めた位置の文字オフセット）を持っています:

```onion
try {
  Json::parse("{bad json")
} catch e: Json.JsonParseException {
  IO::println(e.message() + " at offset " + e.getPosition())
}
```

`Json::asObject(obj)` と `Json::asArray(obj)` は素の `Map`/`List` 表現に対する型安全なキャストです。
実行時の型が一致していれば `Map`/`List` にキャストした値を、そうでなければ `null` を返します。
`Json::get`・`Json::parse`・`Json::parseOrNull` が `Object` を返した後、Map/List として
イテレートしたいときに使います:

```onion
val obj = Json::parse("{\"tags\": [\"a\", \"b\"]}")
val tags = Json::asArray(Json::get(obj, "tags"))   // List。"tags" が配列でなければ null
```

## Yaml モジュール

flat block mapping ドキュメント限定の YAML パースとシリアライズ（`onion.Yaml`）。
Json と同じ中間表現を共有しており（scalar は同じ Java 型にマップされる）、
`derive!(Yaml)` は `derive!(Json)` とまったく同じ `toMap` / `fromMap` の土台の上に
構築されています。

対象範囲: flat block mapping のみ（ネストした map、シーケンス、アンカーは非対応）。

### Yaml::parse

YAML の flat block-mapping 文字列を `LinkedHashMap` にパースします:

```onion
val data = Yaml::parse("name: Alice\nage: 30\n")
// data は LinkedHashMap；scalar の型推論は Json::parse と同じ
```

scalar の型推論規則（Json と同一）:
- `""` または `null` → `null`
- `true` / `false` → `Boolean`
- 整数リテラル（`-?\d+` にマッチ）→ `Long`
- 浮動小数点パターンや `.`/`e`/`E` を含む数値 → `Double`
- クォートされた `"..."` → `String`（エスケープ解除のみ、それ以上の変換なし）
- それ以外 → `String`

不正な入力に対しては `Yaml.YamlParseException` を投げます。`derive!(Yaml)` の
`fromYaml` はこれを捕捉して代わりに `null` を返します。

不正入力の失敗をその場で処理したい場合、`Yaml.YamlParseException` は通常の `message()` に加えて
`getLine()`（パースを諦めた行番号、1始まり）を持っています:

```onion
try {
  Yaml::parse("no colon here")
} catch e: Yaml.YamlParseException {
  IO::println(e.message() + " at line " + e.getLine())
}
```

### Yaml::stringify

`Map`（または scalar）を YAML の flat block-mapping 文字列にシリアライズします:

```onion
val m = ["name": "Alice", "age": 30L]
val yaml = Yaml::stringify(m)
// "name: Alice\nage: 30\n"
```

パースし直したときに誤読される可能性のある文字列値（`:`、`#`、改行を含む、または
数値・真偽値に見えるもの）は自動的にダブルクォートされます。数値と真偽値はそのまま
出力されます。Map の**キー**も同じ規則でクォートされます — `:` や前後の空白を含む
キーは `key: value` の区切りと衝突しないようダブルクォートされます。

### round-trip の保証

`Yaml::parse` が生成した任意の `Map` について、`Yaml::parse(Yaml::stringify(m))` は
等しい map を返します。同様に、`derive!(Yaml)` を付けたレコードでは、scalar 成分の
みを持つすべての値について `fromYaml(toYaml(v)) == v` が成り立ちます。

### `derive!(Yaml)` の利用

`derive!(Yaml)` は scalar 成分のみを持つ任意のレコードに対して `fromYaml` と
`toYaml` を合成します。

```onion
record ServerConfig(host: String, port: Int, debug: Boolean) derive!(Yaml)

val cfg = new ServerConfig("localhost", 8080, false)
val yaml = ServerConfig::toYaml(cfg)
// "host: localhost\nport: 8080\ndebug: false\n"

val cfg2 = ServerConfig::fromYaml(yaml)   // ServerConfig? — パース/変換失敗時は null
```

`derive!(Json, Yaml)` も有効です。両フォーマットは内部の `toMap` / `fromMap` を
共有するため、重複はありません:

```onion
record User(name: String, age: Int) derive!(Json, Yaml)

val u = new User("ko", 3)
val viaJson = User::fromJson(User::toJson(u))   // == u
val viaYaml = User::fromYaml(User::toYaml(u))  // == u
```

## Config モジュール

パース済み JSON に対するドット記法アクセスと設定読み込み（`onion.Config`）。内部では `Json::parse` をそのまま使うので、object / array / scalar の形は Json と同じです。YAML や `.env` 形式には対応せず、あくまで JSON とドット区切りパス、環境変数によるオーバーライドを提供します。

```onion
val config = Config::loadJson("config.json")          // ファイルを読んでパース
val config2 = Config::parseJson("{\"port\": 8080}")   // JSON 文字列を直接パース

Config::get(config, "database.host")                   // 生の値、見つからなければ null
Config::getString(config, "database.host", "localhost")
Config::getInt(config, "database.port", 5432)
Config::getLong(config, "database.maxConnections", 10L)
Config::getDouble(config, "database.timeout", 30.0)
Config::getBoolean(config, "database.ssl", false)
```

パスはドット区切りで、object と array の両方をたどれます。数字のセグメントは array のインデックスとして扱われます:

```onion
val config = Config::parseJson("{\"users\": [{\"name\": \"Alice\"}, {\"name\": \"Bob\"}]}")
Config::getString(config, "users.0.name", "unknown")   // "Alice"
```

キーが見つからない場合・array の添字が範囲外の場合・値を要求された型へ変換できない場合は、例外を投げず指定したデフォルト値にフォールバックします。数値系のゲッターは JSON の数値だけでなく数値文字列も受け付けます。`hasPath` はデフォルト値なしで存在確認だけ行います:

```onion
Config::hasPath(config, "database.host")   // true / false
```

環境変数へのアクセスも用意されています。`getEnv` はそのまま環境変数を読み、`getWithEnvOverride` は設定パスを読みつつ、対応する環境変数がセットされていればそちらを優先します（デプロイ時に設定ファイルの値を上書きするのに便利です）:

```onion
Config::getEnv("PORT", "3000")
Config::getWithEnvOverride(config, "database.host", "DB_HOST", "localhost")
```

## Strings モジュール

文字列ユーティリティ（`onion.Strings`、自動 import）：

```onion
Strings::split("a,b,c", ",")          // List[String] ["a","b","c"]
Strings::splitRegex("a1b2c", "[0-9]") // List[String] ["a","b","c"]
Strings::join(parts, "-")             // 配列・List どちらも可
Strings::upper(s) / Strings::lower(s) / Strings::trim(s)
Strings::replace(s, "a", "b") / Strings::replaceRegex(s, "[0-9]+", "#")
Strings::startsWith(s, p) / Strings::endsWith(s, p) / Strings::contains(s, sub)
Strings::padLeft(s, 8, '0') / Strings::padRight(s, 8, ' ') / Strings::repeat(s, 3)
```

大文字小文字と検査のヘルパー：

```onion
Strings::capitalize("hello")             // "Hello"
Strings::decapitalize("Hello")           // "hello"
Strings::capitalizeWords("a b c")        // "A B C"
Strings::containsIgnoreCase(s, sub) / Strings::equalsIgnoreCase(a, b)
Strings::count("banana", "a")            // 3
Strings::isEmpty("") / Strings::isBlank("   ")   // true / true
Strings::reverse("abc")                  // "cba"
Strings::lines("a\nb\r\nc")              // List[String] ["a","b","c"]
Strings::removePrefix("unhappy", "un")   // "happy"
Strings::removeSuffix("running", "ing")  // "runn"
Strings::truncate("hello world", 8, "...")   // "hello..."
Strings::center("hi", 6, '*')            // "**hi**"
Strings::ifBlank("   ", "default")       // "default"
Strings::words("  a  b  c ")             // List[String] ["a","b","c"]
Strings::chars("abc")                    // List ["a","b","c"]
Strings::substring("hello", 1) / Strings::substring("hello", 1, 3)  // "ello" / "el"
Strings::indexOf("hello", "l") / Strings::lastIndexOf("hello", "l")   // 2 / 3
// null 安全なパース（例外を投げずに null/フォールバックを返す）
Strings::toIntOrNull("42") / Strings::toLongOrNull("100") / Strings::toDoubleOrNull("3.14")
Strings::toIntOr("nope", 0)              // 0
```

`Strings` の大半のメソッド（`upper`、`lower`、`capitalize`、`reverse`
など）は拡張メソッドのメソッドチェーン（`s.upper()`、`s.reverse()`
など）としても静的呼び出しと同じ挙動で使えます。**例外は
`split`・`substring`・`lines`・`chars`・`repeat` の5つ**です。
`java.lang.String` にはすでに同名のメソッドが定義されており、同名の
インスタンスメソッドは常に拡張メソッドより優先されるため、
`s.split(",")`、`s.substring(1)`、`s.lines()`、`s.chars()`、`s.repeat(3)`
は `onion.Strings` 側ではなく **JDK 標準の同名メソッド** を暗黙のうちに
呼び出します。そのため `s.split(",")` は `List` ではなく `String[]` を
返し、`s.substring(10)` は範囲外の開始位置で `""` を返す代わりに例外を
投げ、`s.lines()` / `s.chars()` は `List` ではなく JDK の `Stream` /
`IntStream` を返し、`s.repeat(-1)` は `""` を返す代わりに例外を投げます。
これら5つのメソッドについて `onion.Strings` の List を返す・例外を
投げない挙動を得るには、`Strings::` の静的呼び出し形式（例:
`Strings::split(...)`、`Strings::substring(...)`）を使ってください。

**`join` も遮蔽されますが、JDK ではなく `onion.Colls` によるものです**:
`String` に `join` というインスタンスメソッドはありませんが、
`onion.Colls` も同じ消去シグネチャの `join(List, String)` 拡張メソッド
（`mkString` の別名。後述の Colls モジュールを参照）を宣言しており、
組み込み拡張コンテナのリストで `Colls` が `Strings` より先に登録されて
いるため、`parts.join(sep)` は常に **`onion.Colls`** 側の実装に到達し、
`onion.Strings` の `join` は拡張呼び出し構文からは一切到達できません。
両者は `null` 要素の扱いが異なります: `Colls::join` は文字列
`"null"` をそのまま連結しますが、`Strings::join` は
`NullPointerException` を投げます。例外を投げる挙動が必要なら
`Strings::join(...)` の静的呼び出し形式を使ってください（あるいは
`null` 要素で例外を投げない `Colls` の `xs.join(sep)` /
`xs.mkString(sep)` をそのまま使う手もあります）。

**`contains` と `isEmpty` も遮蔽されますが、プラットフォーム型の
`null` を渡したときだけ挙動差が見えます**: `java.lang.String` には
すでに `contains`・`isEmpty` というインスタンスメソッドが定義されて
いるため、`s.contains(x)` と `s.isEmpty()` も上の5つと同様に
`onion.Strings` 側ではなく **JDK 標準の同名メソッド** を暗黙のうちに
呼び出します。`null` でない `String` に対しては両者は同じ JDK ロジック
に行き着くため通常のコードでは見分けが付きません。差が表面化するのは、
型引数を持たない Java 相互運用から読み戻した値 -- コンパイル時の
null 安全性チェックが及ばない「プラットフォーム」型の値 -- が実行時に
`null` だった場合です: このとき `s.isEmpty()` / `s.contains(x)` は
JDK 側のメソッドから `NullPointerException` を投げますが、
`onion.Strings` の版は null 安全（`Strings::isEmpty(null) == true`、
`Strings::contains(null, x) == false`）です。受け手が未検査の
プラットフォーム `null` になり得る場合は `Strings::contains(...)` /
`Strings::isEmpty(...)` の静的呼び出し形式を使ってください。

**`trim`・`startsWith`・`endsWith`・`indexOf` も同様に遮蔽されます**:
`java.lang.String` にはすでに `trim()`・`startsWith(String)`・
`endsWith(String)`・`indexOf(String)` というインスタンスメソッドが
定義されているため、`s.trim()`・`s.startsWith(x)`・`s.endsWith(x)`・
`s.indexOf(x)` も `onion.Strings` 側ではなく **JDK 標準の同名メソッド**
を暗黙のうちに呼び出します。上の `contains`/`isEmpty` と同様、`null`
でない `String` では見分けが付かず、差が表面化するのはプラットフォーム型の
`null` を受け手にした場合だけです: このとき JDK 側のメソッドは
`NullPointerException` を投げますが、`onion.Strings` の版は null 安全です
（`Strings::trim(null) == ""`、`Strings::startsWith(null, x) == false`、
`Strings::endsWith(null, x) == false`、`Strings::indexOf(null, x) ==
-1`）。受け手が未検査のプラットフォーム `null` になり得る場合は
`Strings::trim(...)` / `Strings::startsWith(...)` / `Strings::endsWith(...)`
/ `Strings::indexOf(...)` の静的呼び出し形式を使ってください。

**`isBlank` も遮蔽されますが、`null` を渡さない通常の `String` でも
挙動差が表面化します**: `java.lang.String` には Java 11 以降
`isBlank()` というインスタンスメソッドが定義されているため、
`s.isBlank()` も上記と同様に `onion.Strings` 側ではなく **JDK 標準の
同名メソッド** を暗黙のうちに呼び出します。`contains`/`isEmpty` とは
異なり、この差はプラットフォーム型の `null` がなくても現れます:
`onion.Strings::isBlank` は `str.trim().isEmpty()` として実装されて
おり、`String::trim` は `U+0020` 以下の文字しか取り除きません。一方
ネイティブの `String::isBlank` は `Character.isWhitespace` を満たす
すべての文字を空白とみなすため、EM SPACE（U+2003）のような `trim()`
では取り除かれない Unicode の空白文字も対象になります。そのため
EM SPACE だけからなる文字列は `s.isBlank()`（拡張呼び出し構文）では
空白扱いになりますが、`Strings::isBlank(s)`（静的呼び出し構文）では
空白扱いになりません。`trim()` ベースの ASCII 空白の意味論が必要な
場合は `Strings::isBlank(...)` の静的呼び出し形式を使ってください。

## Maps モジュール

Map ユーティリティ（`onion.Maps`）。結果 Map は挿入順を保持（`LinkedHashMap`）。

**拡張呼び出しのシャドーイング**: `onion.Colls` も `keys(Map)` / `values(Map)` /
`mapValues(Map, Function1)` を同じ消去シグネチャで拡張メソッドとして宣言しており、
組み込み拡張コンテナリストで `Colls` が `Maps` より先に登録されています -- ただし
この登録順が効くのは `keys()` と `mapValues()` だけです。`m.keys()` /
`m.mapValues(...)` は常に *`onion.Colls`* 側の実装に到達し、`onion.Maps` 側の
この2つには拡張呼び出し構文からは一切到達できません。`Colls` の結果は
**変更不可**（`ks.add(...)` は `UnsupportedOperationException` を投げる）です。

`m.values()` は事情が異なり、どちらの拡張コンテナにも到達しません。
`Map[K, V]` の実体は `java.util.Map` であり、これは引数なしのインスタンス
メソッド `values()` を既に宣言しています。レシーバ自身の型に適用可能な
インスタンスメソッドは、拡張メソッドへのフォールバック（`Colls` を含む）
より常に先に試されるため、`m.values()` は **ネイティブの
`java.util.Map.values()`** に到達します。これはスナップショットではなく
map を裏で参照する *ライブビュー* を返すため、そのビューの iterator 経由で
削除すると `m` 自体からエントリが削除され、あとから `m` を変更すると
以前取得した `values()` ビューにもその変更が反映されます。このビューに
対する `.add(...)` も `UnsupportedOperationException` を投げます（ビューは
挿入をサポートしないため）が、これは `Colls` の変更不可な挙動とたまたま
一致しているだけで理由は別物です -- このライブビューのエイリアシングは
`Colls` の変更不可スナップショットにも `Maps::values` の変更可能な
`ArrayList` スナップショットにも存在しません。直接呼んだ
`Maps::keys`/`values`/`mapValues` は通常の **変更可能**な
`ArrayList`/`LinkedHashMap` を返します。`getOrDefault` も `values()` と
同様にシャドーイングされます -- `java.util.Map` も同じ2引数の
`getOrDefault` を宣言しているため、`m.getOrDefault(k, d)` はどちらの
拡張メソッドでもなくネイティブメソッドに解決されます。レシーバが
非 null であればこれは見えません（ネイティブメソッドと両方の拡張実装が
一致するため）。実行時には `null` だがコンパイル時にはチェックされて
いなかったレシーバ（CLAUDE.md でいう「プラットフォーム型」-- 型引数の
無い Java 相互運用から読み戻された値はコンパイル時の null 許容性
追跡を持たない）に対しては、この違いが表面化します: `onion.Maps::getOrDefault`
は null 安全（map が `null` ならデフォルト値を返す）ですが、同じ
null な map に対する `m.getOrDefault(k, d)` はネイティブメソッドに
到達し、代わりに `NullPointerException` を投げます -- null かもしれない
値には `Maps::getOrDefault(...)` を直接呼んで null 安全な挙動を得て
ください。

```onion
val m: Map[String, Int] = Maps::newMap()
Maps::getOrDefault(m, "a", 0)                 // あればその値、無ければデフォルト
Maps::getOrElse(m, "x", () -> compute())      // 遅延デフォルト
Maps::keys(m) / Maps::values(m)               // 変更可能なリスト; m.keys() / m.values() では到達不可
m.getOrDefault("x", 0)                        // 拡張メソッドとしても呼べる（上と同じ）
m.keys() / m.mapValues(f)                     // onion.Colls 側の実装 -- 変更不可
m.values()                                    // ネイティブ java.util.Map.values() -- ライブビュー
Maps::mapValues(m, (v: Int) -> v * 2) / Maps::mapKeys(m, (k: String) -> k.toUpperCase())
Maps::filterValues(m, (v: Int) -> v > 0) / Maps::filterKeys(m, (k: String) -> k.startsWith("a"))
Maps::filter(m, (k: String, v: Int) -> v > 0) // キー+値の述語
Maps::invert(m)                               // キーと値を入れ替え
Maps::toList(m, (k: String, v: Int) -> k + "=" + v)  // エントリ -> List
Maps::forEach(m, (k: String, v: Int) -> println(k))
Maps::count(m, p) / Maps::anyEntry(m, p) / Maps::allEntries(m, p)
Maps::groupBy(items, keyOf)                   // Map[K, List]
Maps::countBy(items, keyOf)                   // 頻度 Map[K, Integer]
val merged = Maps::merge(a, b)                // 衝突時は b が優先
Maps::mergeWith(a, b, (x: Int, y: Int) -> x + y)  // 衝突を結合
Maps::update(m, "a", (v: Int) -> v + 1)       // 関数的更新
```

**拡張メソッドとしてのシャドーイング:** `onion.Colls` も `onion.Maps` と同じ消去
シグネチャで `groupBy(List, Function1)` 拡張メソッドを宣言しており、組み込み拡張
コンテナリストで `Colls` が `Maps` より先に登録されているため、`xs.groupBy(f)` は
常に *onion.Colls 側の `groupBy`* に到達します -- `onion.Maps` 側は拡張メソッド
構文からは到達できません。`null` と変更可能性の扱いが異なります:
`Colls::groupBy` は `null` の List に対して `NullPointerException` を投げ、内側の
List も含めて**変更不可**な `Map` を返しますが、`Maps::groupBy` は `null` の
List に対して空の変更可能な `Map` を返し、それ以外は変更可能な `ArrayList` を
バケツとして使います。`onion.Maps` 側の変更可能な結果が欲しい場合は
`xs.groupBy(...)` ではなく `Maps::groupBy(...)` を直接呼び出してください。

## Sets モジュール

Set ユーティリティ（`onion.Sets`）。結果 Set は挿入順を保持し、集合演算は null 安全。

どのメソッドも `Set` の組み込み拡張メソッドとして呼び出せます（例: `Sets::union(a, b)` は `a.union(b)`）
-- **ただし `union`/`intersection`/`difference` は例外**: `onion.Colls` も同じ
`(Set, Set)` 消去シグネチャでこの3つの名前の拡張メソッドを宣言しており、組み込み拡張コンテナ
リストで `Colls` が `Sets` より先に登録されているため、`a.union(b)` / `a.intersection(b)` /
`a.difference(b)` は常に *`onion.Colls`* 側の実装に到達し、`onion.Sets` 側のこの3つには拡張呼び出し
構文からは一切到達できません。`Colls` の結果は **変更不可**（`Collections.unmodifiableSet`）、
直接呼んだ `Sets::union`/`intersection`/`difference` は通常の **変更可能**な `LinkedHashSet` を
返します。変更可能な結果が必要な場合は `Sets::` 形式を使ってください。

**`toList` は `a.toList()` としては一切コンパイルできません -- シャドーイングでさえない**:
`onion.Colls` も同じ `(Set)` 消去シグネチャで `toList` 拡張メソッドを宣言しており、本来なら
下記の `union`/`intersection`/`difference`/`map` と同様に単純にシャドーイングされるだけの
はずです。ところが `Set` は `Iterable` にも適合しており、`onion.Iterables` は別途
`toList(Iterable)` 拡張メソッドを宣言しています -- これは消去シグネチャとして本当に異なる
ものですが、`Set` 型の引数にも同じく適用可能です。そのため `a.toList()` は
`onion.Colls` 側と `onion.Iterables` 側のどちらにも解釈できてしまい **曖昧**となり、
どちらかへ黙って解決される代わりに `E0006`（"method call is applicable for both
Colls.toList() and Iterables.toList()"）でコンパイルエラーになります -- このモジュールで
「組み込み拡張メソッドとしても使える」と説明している他のすべてのメソッドとは異なります。
さらに静的呼び出し形どうしも挙動が異なります: `Colls::toList` はコピーを
`Collections.unmodifiableList` でラップし、`null` の Set には `NullPointerException` を
投げますが、`Sets::toList` は通常の変更可能な `ArrayList` を返し、`null` は空として扱います。
必ず `Sets::toList(a)`（変更不可・例外を投げる版が必要なら `Colls::toList(a)`）を明示的に
呼び出してください -- `a.toList()` は `Set` レシーバに対しては絶対にコンパイルできません。

```onion
a.toList()                             // [E0006] 曖昧: Colls.toList() vs Iterables.toList()
Sets::toList(a)                        // 変更可能; null 安全（null には空リスト）
Colls::toList(a)                       // 変更不可; null には NullPointerException
```

```onion
Sets::of(1, 2, 3) / Sets::newSet[Int]() / Sets::fromList([1, 1, 2]) / Sets::toList(a)
Sets::union(a, b) / Sets::intersection(a, b) / Sets::difference(a, b)  // 変更可能; a.union(b) 等では到達不可
a.union(b) / a.intersection(b) / a.difference(b)  // onion.Colls 側の実装 -- 変更不可
Sets::symmetricDifference(a, b)               // どちらか一方だけに含まれる
a.symmetricDifference(b)
Sets::containsAll(a, b)                       // a が b の要素をすべて含む -- a.containsAll(b) では到達不可、下記参照
a.containsAll(b)                              // java.util.Set のネイティブメソッド -- null で NullPointerException
Sets::isSubsetOf(a, b) / Sets::isSupersetOf(a, b) / Sets::isDisjoint(a, b)
a.isSubsetOf(b) / a.isSupersetOf(b) / a.isDisjoint(b)
Sets::map(a, f)                               // NOT a.map(f) で到達不可 -- 下記参照
Sets::filter(a, p) / Sets::find(a, p)
a.filter(p) / a.find(p)
Sets::forEach(a, (x: Int) -> println(x))
a.forEach((x: Int) -> println(x))
Sets::count(a, p) / Sets::any(a, p) / Sets::all(a, p)
a.count(p) / a.any(p) / a.all(p)
```

**拡張呼び出しのシャドーイング:** `onion.Iterables` も同じ `(Set, Function1)` 消去
シグネチャで `map` 拡張メソッドを宣言しており、組み込み拡張コンテナリストで
`Iterables` が `Sets` より先に登録されているため、`a.map(f)` は常に
*`onion.Iterables`* 側の `map` に到達し、`onion.Sets` 側の `map` には拡張呼び出し
構文からは一切到達できません。両者は順序と `null` の扱いが異なります:
`Iterables::map` は結果を通常の `HashSet` に集約する（反復順序は不定で、上記の
「挿入順を保持する」という約束が破れる）うえ `null` の Set に対して
`NullPointerException` を投げますが、`Sets::map` は `LinkedHashSet`（挿入順保持）
に集約し、`null` の Set には空の Set を返します。挿入順が保たれた null 安全な
結果が必要な場合は `a.map(...)` ではなく `Sets::map(...)` を直接呼んでください。

**`containsAll` のシャドーイング:** このリストの他のメソッドと異なり、
`a.containsAll(b)` は `onion.Sets::containsAll` には一切到達しません --
`java.util.Set`（`java.util.Collection` 由来）がすでにインスタンスメソッド
`containsAll(Collection)` を宣言しており、拡張メソッドのフォールバック経路が
参照される前に、インスタンスメソッドが常に優先されるためです。両者は `null`
の扱いが異なります: `onion.Sets::containsAll` は null 安全（`null` の subset は
「すべて含む」、`null` の container は「何も含まない」として扱う）ですが、
ネイティブの `Set.containsAll` は `null` を渡すと `NullPointerException` を
投げます。null 安全な結果が必要な場合は `a.containsAll(...)` ではなく
`Sets::containsAll(...)` を直接呼んでください。

## Hash モジュール

暗号学的ハッシュ・チェックサム（`onion.Hash`）。文字列の UTF-8 バイトをハッシュ化し、小文字 hex のダイジェストを返します。

```onion
Hash::sha256("password")   // 64文字 hex
Hash::sha512(text)         // 128文字 hex
Hash::md5(text) / Hash::sha1(text)   // チェックサム・互換用（衝突耐性なし）
```

いずれも `String` の組み込み拡張メソッドとしても使え、静的呼び出しの代わりに
メソッドチェーンで書けます:

```onion
"password".sha256()        // Hash::sha256("password") と同じ
"x".base64Encode().sha256().substring(0, 8)   // 下の Codec とチェーン可能
```

## Codec モジュール

テキストのエンコード・デコード（`onion.Codec`）: Base64・hex・URL/パーセント形式。

```onion
val enc = Codec::base64Encode("Hello")    // "SGVsbG8="
Codec::base64Decode(enc)                  // "Hello"
Codec::hexEncode("Hi") / Codec::hexDecode("4869")
Codec::urlEncode("a b&c") / Codec::urlDecode(s)
```

これらも `String` の組み込み拡張メソッドです:

```onion
"Hello".base64Encode().base64Decode()   // "Hello"
"Hi".hexEncode() / "4869".hexDecode()
"a b&c".urlEncode() / s.urlDecode()
```

## Stats モジュール

数値リストの集計（`onion.Stats`）。汎用集計は `List[Int]`/`List[Long]`/`List[Double]` を受け付け倍精度で計算。`sumInt`/`sumLong` は整数精度を保持。

```onion
val xs: List[Int] = [10, 20, 30, 40]
Stats::sum(xs)       // 100.0      Stats::sumInt(xs)   // 100
Stats::average(xs)   // 25.0       Stats::median(xs)   // 25.0
Stats::min(xs) / Stats::max(xs)    // 10.0 / 40.0
Stats::variance(xs) / Stats::stddev(xs)

val ys: List[Long] = [10L, 20L, 30L, 40L]
Stats::sumLong(ys)   // 100L   （Long: 整数精度を保持）
```

メソッド呼び出しの形でも使えます（実際のコードではこちらが自然です）。ただし
**メソッド形式も同じく倍精度**なので、`Int` のリストでも合計は `Double` になります。
`Int` で受け取りたい場合は `Stats::sumInt` を使ってください。

```onion
val xs: List[Int] = [10, 20, 30, 40]
xs.sum()             // 100.0  （Double: 汎用集計）
Stats::sumInt(xs)    // 100    （Int）
```

**`min`/`max` は例外です**: `onion.Colls` にも消去後シグネチャが同じ
`min(List)`/`max(List)` 拡張メソッドがあり、`onion.Stats` より先に登録される
ため、`xs.min()` と `xs.max()` は常に *`onion.Colls`* 側に到達し、
`onion.Stats` 側には到達しません。つまり `xs.min()` はリストの要素型そのまま
（`List[Int]` なら精度の落ちない `Int`）を返し、**空リストでは `0.0` を返す
代わりに `NoSuchElementException` を投げます**。`Double` の結果と空リストに
安全な `0.0` フォールバックが必要な場合は `Stats::min`/`Stats::max` の静的
呼び出し形式を使ってください:

```onion
val xs: List[Int] = [10, 20, 30, 40]
xs.min()             // 10   （Int: onion.Colls::min であって onion.Stats::min ではない）
Stats::min(xs)       // 10.0 （Double。空リストでも例外ではなく 0.0）
```

`Int` を返す `sum()` のオーバーロードが無いのは型消去のためです。実行時には要素型が
消えるので、`sum(List[Int])` と `sum(List[Double])` は同じ JVM シグネチャになります。

## Format モジュール

locale 非依存の人間可読フォーマット（`onion.Format`）——桁区切り・小数・サイズ・時間。

```onion
Format::integer(1234567)          // "1,234,567"
Format::number(1234.5678, 2)      // "1,234.57"
Format::fixed(3.14159, 2)         // "3.14"
Format::percent(0.756, 1)         // "75.6%"
Format::bytes(1536)               // "1.5 KB"（1024基準）
Format::duration(3661)            // "1h 1m 1s"
Format::ordinal(21)               // "21st"
```

いずれも数値レシーバの組み込み拡張メソッドとしても使えます（`integer`/`bytes`/
`duration`/`ordinal` は `Long`、`number`/`fixed`/`percent` は `Double`）:

```onion
(1536L).bytes()                   // "1.5 KB"
(3661L).duration()                // "1h 1m 1s"
(21L).ordinal()                   // "21st"
(0.756).percent(1)                // "75.6%"
(3.14159).fixed(2)                // "3.14"
```

## Text モジュール

コンソールのテキストレイアウト（`onion.Text`）——折返し・インデント・整列テーブル。

```onion
Text::wrap("長い文章 ...", 40)          // 折り返した行のリスト
Text::indent("a\nb", "> ")              // "> a\n> b"
Text::dedent("    a\n    b")            // "a\nb"

Text::table([["Name", "Dept"], ["Alice", "Eng"], ["Bob", "Sales"]])
// Name   Dept
// Alice  Eng
// Bob    Sales
```

いずれもレシーバの組み込み拡張メソッドとしても使えます（`wrap`/`indent`/`dedent`
は `String`、`table` は `List`）:

```onion
"長い文章 ...".wrap(40)            // 折り返した行のリスト
"a\nb".indent("> ")                // "> a\n> b"
[["Name", "Dept"], ["Alice", "Eng"]].table()
```

## System モジュール

Java の `System` クラスを介したシステムレベル操作へのアクセス。

### System::out

標準出力ストリーム:

```onion
System::out.println("直接システム出力")
System::out.print("改行なし")
```

### System::in

標準入力ストリーム:

```onion
import {
  java.io.BufferedReader;
  java.io.InputStreamReader;
}

val reader: BufferedReader = new BufferedReader(
  new InputStreamReader(System::in)
)
```

### System::currentTimeMillis

現在時刻をミリ秒で取得:

```onion
val time: Long = System::currentTimeMillis()
IO::println("現在時刻: " + time)
```

### System::getProperty

システムプロパティを取得:

```onion
val os: String = System::getProperty("os.name")
val user: String = System::getProperty("user.name")
val home: String = System::getProperty("user.home")
```

### System::exit

プログラムを終了:

```onion
System::exit(0)  // 成功
System::exit(1)  // エラー
```

## Iterables モジュール

`onion.Iterables`（Java インターフェース）で提供。

コレクションや配列向けのイテレーションユーティリティ:

- `Iterables::map(list|iterable|set, f)`
- `Iterables::mapMap(map, f)` - 各 `Map.Entry` を `f` で変換した新しい `Map` を返す
- `Iterables::toList(iterable)` - 任意の `Iterable`（範囲を含む）を `List` に実体化する
- `Iterables::filter(list|iterable, predicate)`
- `Iterables::foldl(iterable, init, f)`
- `Iterables::reduce(list, initial, reducer)`
- `Iterables::exists(iterable, predicate)`
- `Iterables::forAll(iterable, predicate)`
- `Iterables::listOf(elements...)`
- `Iterables::newList(size)` - `size` 個分の容量を確保した空の `List`
- `Iterables::first(list)` / `Iterables::last(list)` - リストが空なら `null`
- `Iterables::reverse(list)`
- `Iterables::take(list, n)` / `Iterables::drop(list, n)`
- `Iterables::sort(list, comparator)` / `Iterables::sort(list)` - 後者は要素が `Comparable` であることが必要

上記のメソッド（`listOf` と `newList` を除く -- これらは `List` を操作するの
ではなく生成するため）は、いずれも組み込みの拡張メソッドとしても呼び出せ、
第一引数に対するメソッドチェーンとして書ける:

```onion
xs.map { x -> x * 2 }             // Set / Iterable レシーバでも同様
m.mapMap((e) -> Colls::entry(e.getKey(), e.getValue() * 2))
(1..5).toList()                   // 範囲(Range)も対象
xs.filter { x -> x > 0 }
xs.foldl(0, (acc, x) -> acc + x)
xs.reduce(0, (acc, x) -> acc + x)
xs.exists { x -> x > 2 }
xs.forAll { x -> x > 0 }
xs.first() / xs.last()
xs.reverse()
xs.take(2) / xs.drop(1)
xs.sort() / xs.sort(comparator)
```

**`map`/`filter`/`take`/`drop`/`reverse` は例外です**: `onion.Colls` にも消去後
シグネチャが同じ `map(List, Function1)`/`filter(List, Function1)`/
`take(List, int)`/`drop(List, int)`/`reverse(List)` 拡張メソッドがあり、
`onion.Iterables` より先に登録されるため、`xs.map(f)`・`xs.filter(p)`・
`xs.take(n)`・`xs.drop(n)`・`xs.reverse()` は常に *`onion.Colls`* 側に到達し、
`onion.Iterables` 側には到達しません。両者はエッジケースで挙動が異なります:

- `xs.map(f)` は**変更不可 (unmodifiable)** なリストを返す。`Iterables::map`
  は変更可能なコピーを返す
- `xs.filter(p)` は `p` を呼び出した後にその `Boolean` 結果をアンボックスする
  ため、`p` が `null` を返すと **`NullPointerException`** を投げる。
  `Iterables::filter` は `null` を「保持しない」として扱い、例外を投げない
- `n` が負の場合 -- `xs.take(-1)` / `xs.drop(-1)` は空/変更なしの結果を返す
  が、`Iterables::take`/`Iterables::drop` は例外を投げる
- `n` がリストのサイズ以上の場合 -- `xs.take(n)` / `xs.drop(n)` はコピーで
  はなく**同じリスト参照そのもの**を返す。`Iterables::take`/`Iterables::drop`
  は必ずコピーする
- `xs.reverse()` は**変更不可 (unmodifiable)** なリストを返す。
  `Iterables::reverse` は変更可能なコピーを返す

変更可能なコピーや、`null` を許容しサイズに関して安全な挙動が必要な場合は
`Iterables::map`/`Iterables::filter`/`Iterables::take`/`Iterables::drop`/
`Iterables::reverse` の静的呼び出し形式を使ってください:

```onion
val xs: List[Int] = [1, 2, 3]
xs.map { x -> x * 2 }.add(8)   // UnsupportedOperationException を投げる（onion.Colls::map）
Iterables::map(xs, (x) -> x * 2).add(8)  // OK、変更可能なコピー（onion.Iterables::map）
xs.filter { x -> null }        // NullPointerException を投げる（onion.Colls::filter）
Iterables::filter(xs, (x) -> null)       // []（onion.Iterables::filter。null は「保持しない」扱い）
xs.take(-1)                    // []      （onion.Colls::take。例外ではなく空リストにクランプ）
xs.take(xs.size() + 1) === xs  // true    （onion.Colls::take。同じリスト参照）
xs.reverse().add(4)            // UnsupportedOperationException を投げる（onion.Colls::reverse）
Iterables::take(xs, -1)        // 例外を投げる（onion.Iterables::take。クランプしない）
```

**`reduce` もシャドーイングされますが、実行時ではなくコンパイル時に現れます**:
`onion.Colls` にも消去後シグネチャが同じ3引数の `reduce(List, Object, Function2)`
拡張メソッドがあり、`xs.reduce(initial, f)` は常に *`onion.Colls`* 側の実装に
到達します -- `onion.Iterables` の3引数 `reduce` は拡張呼び出し構文からは
一切到達できません。上記の `map`/`filter`/`take`/`drop`/`reverse` と違い、
両者は実行時のエッジケースだけでなく型で異なります: `Colls::reduce` の宣言
シグネチャは `initial` と戻り値を実際のジェネリック型 `U` として型付けする
ため、アキュムレータには具体的な型（例えば `Int`）が推論されますが、
`Iterables::reduce` の宣言シグネチャは両方とも単なる `Object` に消去します。
そのため上のコード例の `xs.reduce(0, (acc, x) -> acc + x)` がコンパイルでき
`Int` を合計しているのは、このリストでの見た目とは異なり `onion.Iterables::reduce`
ではなく `onion.Colls::reduce` に静かに到達しているからです -- `Iterables::reduce`
を明示的に呼び出すとコンパイルが失敗します:

```onion
val xs: List[Int] = [1, 2, 3]
xs.reduce(0, (acc, x) -> acc + x)                    // 6  （onion.Colls::reduce。acc は Int）
Iterables::reduce(xs, 0, (acc, x) -> acc + x)         // [E0001] operator + is not applicable for type Object, Int
```

**`first`/`last` もシャドーイングされますが、無害です**: `onion.Colls` にも
消去後シグネチャが同じ `first(List)`/`last(List)` 拡張メソッドがあり、
`xs.first()`/`xs.last()` は常に *`onion.Colls`* 側に到達します --
`onion.Iterables` 側の `first`/`last` は拡張呼び出し構文からは一切到達でき
ません。このセクションの他の名前と違い、両者の実装は**同一**です
（`list.isEmpty() ? null : list.get(0)`、末尾側も同様）。そのためシャドー
イングされていても挙動に影響はなく、`xs.first()` は常に `Iterables::first(xs)`
と等しく、`last` も同様です。

**`sort` の2引数版もシャドーイングされますが、これまでのどれよりも深刻です**:
`java.util.List` は既にインスタンスメソッド `sort(Comparator)`（Java 8 以降の
デフォルトメソッド）を宣言しており、インスタンスメソッドは同名の拡張メソッド
に常に優先するため、`xs.sort(comparator)` は `onion.Iterables::sort(List,
Comparator)` に一切到達しません。これは実行時のエッジケースにとどまりません:
ネイティブの `List.sort` はレシーバを**破壊的に（in place で）**ソートして
`void` を返しますが、`Iterables::sort` はレシーバに触れず**新しい**ソート済み
`List` を返します。そのため `xs.sort(comparator)` は使い捨ての文としては問題
なくコンパイルできます（`xs` を破壊的にソートするだけです）が、その結果を値
として使おうとした瞬間にコンパイルエラーになります:

```onion
val xs: List[Int] = [3, 1, 2]
xs.sort((a, b) -> a - b)              // xs は [1, 2, 3] に -- ネイティブ List.sort、in place
val ys = xs.sort((a, b) -> a - b)     // [E0000] type Object is expected, but type void is used
Iterables::sort(xs, (a, b) -> a - b)  // ok -- 新しいソート済みコピー、xs は変化しない
```

引数無しの形は影響を受けません: `List` は引数無しの `sort()` を宣言していない
ため、`xs.sort()` は常に `Iterables::sort(List)` に到達し、上記の通り新しい
ソート済みコピーを返します。

## Files モジュール

ファイル I/O（`onion.Files`）:

```onion
Files::readText("path.txt")            // ファイル全体を String として
Files::readLines("path.txt")           // List[String]
Files::writeText("out.txt", content)
Files::writeLines("out.txt", lines)    // List[String] を1行ずつ書き込む
Files::appendText("out.txt", content)  // 追記。ファイルが無ければ新規作成
Files::readBytes(path) / Files::writeBytes(path, bytes)
Files::list("dir")                     // エントリ名の List
Files::listFiles("dir")                // java.io.File エントリの List
Files::glob("dir", "*.on")             // glob にマッチしたエントリ名
Files::delete(path) / Files::exists(path)
Files::isFile(path) / Files::isDirectory(path)
Files::mkdirs(path)                    // ディレクトリと不足する親ディレクトリを作成
Files::size(path)                      // Long。バイト数（存在しなければ0）
Files::copy(src, dst)                  // dst が既にあれば置き換える
Files::move(src, dst)                  // 移動（リネーム）。dst が既にあれば置き換える
Files::copyDir(src, dst)               // ディレクトリを再帰的にコピー
```

パス操作ヘルパー——ファイル名・親ディレクトリ・結合・拡張子:

```onion
Files::getFileName("a/b/c.txt")        // "c.txt"
Files::getParent("a/b/c.txt")          // "a/b"
Files::getAbsolutePath("a/b/c.txt")    // カレントディレクトリ基準の絶対パス
Files::joinPath("a/b", "c.txt")        // "a/b/c.txt"
Files::ext("report.txt")               // "txt"（拡張子。予約語を避けた名前）
Files::stem("report.txt")              // "report"
Files::withExtension("report.txt", "md")   // "report.md"
```

## Csv モジュール

RFC 4180 準拠の自己完結型 CSV パース・シリアライズ（`onion.Csv`、自動インポート済み）——引用フィールド・カンマ/改行を含む値・二重引用符に対応。

```onion
val rows = Csv::parse(text)                  // List of List of String
val recs = Csv::parseWithHeader(text)        // List of Map（ヘッダー -> 値）

Csv::column(rows, 0)                          // 位置指定で1列取得
Csv::columnByName(recs, "age")                // ヘッダー名で1列取得

val out  = Csv::stringify(rows)               // rows -> CSV テキスト
val out2 = Csv::stringifyWithHeader(recs)     // records -> CSV（parseWithHeader の逆）
```

## Proc モジュール

スクリプト向けのプロセス実行（`onion.Proc`）:

```onion
val r = Proc::capture("git", "status")  // r.status() / r.stdout() / r.stderr() / r.succeeded() / r.failed()
Proc::run("ls", "-la")                  // stdout を String で取得（失敗時は例外）
Proc::exec("make", "build")             // 終了コード、出力はそのまま素通し
Proc::captureIn("/tmp", "ls")           // ...In 系は作業ディレクトリを指定
Proc::runIn("/tmp", "ls")               // run と同様だが、指定した作業ディレクトリで実行
Proc::execIn("/tmp", "make", "build")   // exec と同様だが、指定した作業ディレクトリで実行
```

## Args モジュール

コマンドライン引数のパース（`onion.Args`）:

```onion
val parsed = Args::parse(args)
parsed.flag("verbose")                  // --verbose
parsed.option("out", "a.out")           // --out path（デフォルト値付き）
parsed.intOption("level", 3)
parsed.positional()                     // オプション以外の引数の List
```

## Colls モジュール

コレクションのファクトリとパイプライン（`onion.Colls`）:

```onion
Colls::listOf("a", "b", "c")            // 不変の List
Colls::mutableListOf(1, 2, 3)           // ArrayList
Colls::range(0, 5)                      // List [0,1,2,3,4]
Colls::rangeWithStep(0, 10, 2)          // List [0,2,4,6,8]
Colls::sortedBy(people) { p -> p.age() }
// map/filter/reduce/fold のパイプラインは List/Iterable/配列の拡張メソッド:
// xs.map { x -> x * 2 }.filter { x -> x > 0 }
```

### 追加のファクトリ: Set・Map・空のコレクション

```onion
Colls::setOf("a", "b", "c")             // 不変の Set（反復順序は保証されない）
Colls::mutableSetOf(1, 2, 3)            // HashSet

Colls::entry("name", "Alice")           // mapOf/mutableMapOf 用の Map.Entry
Colls::mapOf(Colls::entry("name", "Alice"), Colls::entry("age", "30"))   // 不変の Map、挿入順を保持
Colls::mutableMapOf(Colls::entry("x", 1))                               // HashMap

Colls::emptyList()                      // []
Colls::emptySet()                       // 空の Set
Colls::emptyMap()                       // 空の Map
```

### List・Set・Map のユーティリティ

`Colls` の他のメソッドと同様、最初の引数（list/map）に対する拡張メソッドとしても
呼び出せ、パイプラインとして連結できる:

```onion
xs.concat(ys)                     // xs の要素に続けて ys の要素
[[1, 2], [3, 4]].flatten()        // [1, 2, 3, 4] - ネストを1段階解消
xs.flatMap { x -> [x, x] }        // 各要素をリストに写してから1段階平坦化する
                                   // （bind はその別名で、do[List] { x <- xs; ... } が使う）
xs.partition { x -> x > 1 }       // [matching, nonMatching] - 2つの List
xs.toSet()                        // xs の要素から作った Set
xs.distinct()                     // 重複を除去、最初に出現した順を保持
xs.slice(0, 2)                    // [0, 2) の部分リスト、範囲内にクランプ
xs.sorted()                       // 昇順の新しい List（要素は Comparable である必要がある）
xs.sortedByDescending { x -> x }  // sortedBy と同様だが降順
xs.head()                         // 先頭要素、空ならnull（first の別名）
xs.tail()                         // 先頭要素を除いた残り（空リストでは例外）
xs.takeWhile { x -> x < 3 }       // 述語を満たす先頭の連続部分
xs.dropWhile { x -> x < 3 }       // その先頭の連続部分を取り除いた残り
xs.zip(ys)                        // [[x0, y0], [x1, y1], ...] - ペアのList、短い方に合わせて切り詰め
xs.groupBy { x -> x % 2 }         // キーごとの要素の List を値に持つ Map
xs.mkString(", ")                 // "1, 2, 3" - 要素を文字列として連結（join は別名）
Colls::isNotEmpty(xs)             // true - isEmpty の否定
m.filterMap { k, v -> k == "name" }   // 条件に合うエントリだけの Map
xs.any { x -> x > 1 }             // いずれかの要素が条件を満たせば true
xs.all { x -> x > 0 }             // すべての要素が条件を満たせば true
xs.none { x -> x > 5 }            // どの要素も条件を満たさなければ true
xs.find { x -> x > 1 }            // 最初に条件を満たす要素、なければ null
xs.forEach { x -> println(x) }    // 各要素に対して処理を実行、戻り値なし
xs.count { x -> x > 1 }           // 条件を満たす要素の数
xs.reverse()                      // 要素を逆順にした新しい List
xs.contains(2)                    // いずれかの要素が2と等しければ true
Colls::toList(args)               // Java配列（例: main の String[]）を List に変換
```

### バッチ化・ウィンドウ化・セレクタ集計

これらも `Colls::` の静的呼び出しとして、また `Colls` の他のメソッドと同様に
パイプラインとして連結できる List の拡張メソッドとして利用できる:

```onion
xs.chunked(3)                     // [[1,2,3],[4,5,6],[7]] - 最大3件のバッチ、最後は少なくなることがある
xs.windowed(3)                    // [[1,2,3],[2,3,4],[3,4,5]] - 1要素ずつスライドする窓
ps.sumBy((p) -> p.age())          // Double - 各要素にセレクタを適用した合計
ps.averageBy((p) -> p.age())      // Double - セレクタの平均、空なら0.0
ps.maxBy((p) -> p.age())          // セレクタの値が最大の要素、空ならnull
ps.minBy((p) -> p.age())          // セレクタの値が最小の要素、空ならnull

xs.chunked(2).map { b -> (b as List).size() }   // 他のパイプライン段と同様に連結できる
```

## Http

HTTPクライアントユーティリティ（Java 11+ の HttpClient を使用）。

### GET リクエスト

```
Http::get(url): String
Http::get(url, headers): String    // headers: ["Name1", "Value1", ...]
```

### POST リクエスト

```
Http::post(url, body): String
Http::postJson(url, jsonBody): String    // Content-Type: application/json を設定
Http::post(url, body, headers): String   // headers は get と同じ
```

### Response オブジェクト

```
Http::getResponse(url): Response                  // ボディだけでなく status/body/headers を返す
Http::postResponse(url, body): Response
```

`Response` は `status: Int`、`body: String`、`headers: List` のフィールドと、
`isOk(): Boolean`（2xx）・`isError(): Boolean`（4xx/5xx）のヘルパーを持つ。
ボディだけでなくステータスコードやヘッダーが必要なときに使う。

### その他のメソッド

```
Http::put(url, body): String
Http::delete(url): String
```

### URL ユーティリティ

```
Http::encodeUrl(str): String
Http::decodeUrl(str): String
Http::buildQuery(params): String        // params: キーと値を交互に並べる
Http::buildUrl(baseUrl, params): String // "?"/"&" + buildQuery(params) を付加
```

### 例

```
val response: String = Http::get("https://api.example.com/data");
val data: Object = Json::parse(response);

val postResponse: String = Http::postJson(
  "https://api.example.com/users",
  "{\"name\": \"Bob\"}"
);
```

---

## DateTime

エポックミリ秒を使った日時ユーティリティ。

### 現在時刻

```
DateTime::now(): Long              // 現在のエポックミリ秒
DateTime::nowString(): String      // ISO 形式（ローカルタイムゾーン）
DateTime::nowString(pattern): String
```

### パース

```
DateTime::parse(isoString): Long
DateTime::parse(dateTime, pattern): Long
```

### フォーマット

```
DateTime::format(epochMillis): String
DateTime::format(epochMillis, pattern): String
```

### 構成要素

```
DateTime::year(epochMillis): Int
DateTime::month(epochMillis): Int       // 1-12
DateTime::day(epochMillis): Int         // 1-31
DateTime::hour(epochMillis): Int        // 0-23
DateTime::minute(epochMillis): Int      // 0-59
DateTime::second(epochMillis): Int      // 0-59
DateTime::dayOfWeek(epochMillis): Int   // 1=月曜, 7=日曜
DateTime::dayOfYear(epochMillis): Int   // 1-366
```

### 演算

```
DateTime::addDays(epochMillis, days): Long
DateTime::addHours(epochMillis, hours): Long
DateTime::addMinutes(epochMillis, minutes): Long
DateTime::addSeconds(epochMillis, seconds): Long
DateTime::addMonths(epochMillis, months): Long
DateTime::addYears(epochMillis, years): Long
```

### 比較

```
DateTime::diff(time1, time2): Long        // ミリ秒単位の差分
DateTime::diffDays(time1, time2): Int
DateTime::diffHours(time1, time2): Long   // hours / minutes / seconds は整数値
DateTime::diffMinutes(time1, time2): Long
DateTime::diffSeconds(time1, time2): Long
DateTime::isBefore(time1, time2): Boolean
DateTime::isAfter(time1, time2): Boolean
DateTime::dayName(epochMillis): String    // "Friday"（英語、ロケール非依存）
DateTime::monthName(epochMillis): String  // "March"
```

### ファクトリ

```
DateTime::of(year, month, day): Long
DateTime::of(year, month, day, hour, minute, second): Long
DateTime::startOfDay(epochMillis): Long
DateTime::endOfDay(epochMillis): Long
```

### 例

```
val now: Long = DateTime::now();
IO::println("今日: " + DateTime::format(now, "yyyy-MM-dd"));

val tomorrow: Long = DateTime::addDays(now, 1);
IO::println("明日: " + DateTime::format(tomorrow));

val birthday: Long = DateTime::of(1990, 5, 15);
val age: Int = DateTime::diffDays(now, birthday) / 365;
```

---

## Net

TCP ソケット。`Http` がリクエストを送る側なのに対し、こちらは任意のプロトコルを話し、接続を受けられます。

### Net::connect

```onion
val conn = Net::connect("example.com", 80)
conn.writeLine("GET / HTTP/1.0")
conn.writeLine("Host: example.com")
conn.writeLine("")
IO::println(conn.readAll())
conn.close()
```

`Net::connect(host, port, timeoutMillis)` はタイムアウトを指定できます。OS 既定のままだと、
パケットが落ちた場合に 1 分以上待つことがあります。

接続は `readLine()`（終端で null）、`readAll()`（UTF-8、相手が閉じるまで）、`readBytes()` で読み、
`write(text)`、`writeLine(text)`（CRLF を付加。行指向プロトコルが期待する形）、`writeBytes(bytes)`
で書きます。書き込みは毎回フラッシュするので、バッファに溜まったまま送られないことはありません。
`timeout(millis)` はブロックする読み込みの上限、`closeWrite()` は読みを続けたまま書き側だけ閉じて
EOF を通知します。`close()` は冪等で、`conn.isClosed()` で既に閉じているかを確認できます。

### Net::listen

```onion
val listener = Net::listen("localhost", 0, 4)   // 0 で OS に空きポートを選ばせる
IO::println("listening on " + listener.port())

val peer = listener.accept()
peer.writeLine("hello " + peer.remoteAddress())
peer.close()
listener.close()
```

ポート 0 は OS に空きポートを選ばせ、`port()` が実際のポートを返します。番号を決め打ちして祈らずに
サーバをテストできるのはこのためです。`"localhost"` を指定するとネットワークからは到達できません。
ホストに `null` を渡すとすべてのローカルアドレスにバインドします。`accept()` でブロックしている
スレッドを解除するにはリスナーを閉じます。閉じたかどうかは `listener.isClosed()` で分かります。

`Net::listen(port)` は最後のケースの省略形で、バックログのデフォルト値 50 ですべてのローカル
アドレスにバインドします。`Net::listen(null, port, 50)` と同等です。

失敗時は失敗したアドレスがメッセージに入るので、Onion の `catch` で「connection refused」だけでなく
どのホストかが分かります。

---

## Server

HTTP サーバ。JDK 同梱の実装を使うので依存は増えません。

### Server::start

```onion
val server = Server::start("localhost", 8080)
server.handle("/hello", (req) -> Server::text("hi " + req.method()))
server.await()
```

`Server::start(port)` はすべてのローカルアドレス、`Server::start(host, port)` は 1 つだけに
バインドします。ポート 0 なら OS が空きポートを選び、`port()` が返します。`await()` はプロセスが
終わるまでブロックし、`stop()` は受付を止めて処理中のリクエストを 1 秒待ちます。

### ルーティング

`handle(path, handler)` は完全一致、`handleAll(handler)` はそれ以外すべてを受けます。Onion 側で
ルーティングを書くならこちらです。

```onion
server.handleAll((req) -> select req.path() {
  case re"/users/(\d+)" (id): Server::json("{\"id\":" + id + "}")
  case "/health":             Server::text("ok")
  else:                       Server::notFound()
})
```

ハンドラが例外を投げた場合は 500 を返します。サーバが落ちたり、クライアントが応答のないソケットで
待ち続けたりすることはありません。

### Request

`method()`、`path()`（クエリ文字列を含まない）、`query()`（生のクエリ文字列。無ければ `""`）、
`body()`（ハンドラ実行前に全部読み込み済み）、`header(name)`、`headers()`、`params()`。
最後の 2 つは記述順を保った `Map` を返します。

### Response

`Server::text`・`Server::json`・`Server::html` は対応する Content-Type 付きの 200、
`Server::notFound()` は 404、`Server::status(code, body)` はそれ以外です。レスポンスは不変なので、
`withStatus` と `withHeader` は新しい値を返します。

```onion
val r = Server::json("{\"a\":1}").withStatus(201).withHeader("X-Test", "yes")
```

レスポンスの構築はソケットに一切触れません。ハンドラを単体でテストできるのはこのためです。

---

## Archive

zip と gzip。tar は依存が必要になるため入れていません。この 2 つが JDK 単体でできる範囲です。

```onion
Archive::zip("out.zip", ["a.txt", "b.txt"])
Archive::zipDir("site.zip", "site")          // "site" からの相対パスを保つ
val names = Archive::entries("out.zip")      // 展開せずに一覧
val written = Archive::unzip("out.zip", "extracted")

Archive::gzipFile("big.log", "big.log.gz")   // 全部をメモリに読まずストリーミング
Archive::gunzipFile("big.log.gz", "big.log") // その逆
val bytes = Archive::gunzip(Archive::gzip(text.getBytes()))
```

**展開は、対象ディレクトリの外に書き出すことを拒否します。** `../../.ssh/authorized_keys` という
名前のエントリは「zip slip」と呼ばれる古典的な攻撃で、エントリ名を素直に解決する展開処理は
言われたとおりの場所に書き込んでしまいます。ここではエントリ名を示して例外を投げます。

エントリのタイムスタンプは固定値で書き込むので、同じ入力を 2 回 zip すると同じバイト列になります。
実行のたびに変わる成果物はチェックサムもキャッシュもできません。

---

## Concurrent

スレッドと、それを安全に使うための部品。`Future` は既に「1 つの処理を別スレッドで走らせる」ことは
できましたが、同時実行数を制限する手段、スレッド間でカウンタを共有する手段、ロックを取る手段、
処理を受け渡す手段がありませんでした。

仮想スレッドは意図的に入れていません。Java 21 が必要で、Onion のターゲットは 17 です。

### Pool

```onion
val pool = Concurrent::pool(4)                     // Concurrent::pool() で CPU 数
val bodies = pool.mapAll(urls, (u) -> Http::get(u))
pool.close()
```

`mapAll` は結果を**入力の順序**で返します。完了順ではありません——タイミングに依存する出力は
テストできない出力です。失敗した要素は全タスクが決着してから報告されるので、1 つの不正な入力の
せいで、諦めた呼び出し元の裏でワーカーが走り続けることはありません。単発の処理には
`submit(f)` が `Future` を返します。

プールのスレッドはデーモンなので、閉じ忘れたプールが `main` の後も JVM を生かし続けることは
ありません。とはいえ `close()` は呼ぶべきですし、処理中のものを待つなら `awaitClose(millis)` です。

API 一覧:

- `Concurrent::cpus()` - このマシンで使えるプロセッサ数（引数なしの `Concurrent::pool()` が
  採用するサイズ）
- `pool.size()` - このプールのスレッド数
- `pool.submit(f)` - `f` をワーカーで実行し、`Future` を返す
- `pool.mapAll(items, f)` - 上記の通り
- `pool.close()` - 新規の受け付けを止め、実行中のものを中断する。冪等
- `pool.awaitClose(timeoutMillis)` - 新規の受け付けを止め、実行中のものを待つ。
  タイムアウト内に全て終わったかを返す

### Counter・Lock・Channel

```onion
val hits = Concurrent::counter()
hits.increment()

val lock = Concurrent::lock()
lock.withLock(() -> { /* … */ })     // 本体が例外を投げても解放される

val chan = Concurrent::channel(16)   // 意図的に上限つき
chan.send("work")
val item = chan.receiveTimeout(1000) // 永久にブロックせず null を返す
```

`acquire`/`release` の手動ペアより `withLock` を使ってください。ペアの間で例外が投げられると
ロックが漏れ、他のスレッドが永久に待ちます。チャネルに上限があるのは、無制限だと生産者が
消費者を追い越していることがメモリ枯渇まで見えないからです。`null` の送信は拒否します——
受信側で「何も来なかった」と区別できなくなるためです。

API 一覧:

- `Concurrent::counter()` / `Concurrent::counter(initial)`
- `counter.get()` / `counter.increment()` / `counter.decrement()` / `counter.add(delta)` /
  `counter.set(next)`
- `counter.compareAndSet(expected, next)` - 値がまだ `expected` と等しい場合のみ設定する
- `Concurrent::lock()`
- `lock.withLock(body)` - 上記の通り
- `lock.acquire()` / `lock.release()` - `withLock` が避けるための手動ペア
- `lock.tryAcquire()` - ロックが空いているときだけ取得する。取得できたかを返す
- `lock.isHeld()` - ロックが現在保持されているか
- `Concurrent::channel(capacity)`
- `chan.send(item)` / `chan.trySend(item)` - 満杯ならブロック / 満杯なら false を返す
- `chan.receive()` / `chan.receiveTimeout(timeoutMillis)` - 何か届くまでブロック /
  タイムアウト後は null を返す
- `chan.size()` / `chan.isEmpty()`
- `chan.close()` / `chan.isClosed()` - 以降の送信を拒否する。既にキューにあるものは受信できる
- `chan.drain()` - 現在キューにあるもの全てを取り出し、チャネルを空にする

---

## Db

JDBC 経由の SQL。ドライバは同梱していません。プロジェクトが宣言したものを使います。

```toml
[dependencies]
"org.postgresql:postgresql" = "42.7.3"
```

```onion
val db = Db::connect("jdbc:postgresql://localhost/app", "user", "secret")

val rows = db.query("SELECT id, name FROM users WHERE age > ?", 18)
val one  = db.queryOne("SELECT * FROM users WHERE id = ?", 7)   // 該当なしなら null
val n    = db.queryValue("SELECT COUNT(*) FROM users")          // 先頭行の先頭列
db.update("INSERT INTO users VALUES (?, ?)", 8, "ada")

db.transaction((conn) -> {
  conn.update("UPDATE accounts SET balance = balance - ? WHERE id = ?", 100, 1)
  conn.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", 100, 2)
})

db.isClosed()   // db.close() を呼ぶまでは false
db.close()
```

`Db::connect(url)` には認証情報が不要なデータベース（SQLite、H2 など）向けの引数 1 つの形式
もあります。`Db::connect("jdbc:sqlite:local.db")` は `Db::connect(url, null, null)` と同じです。

値は常に**バインド**され、SQL 文字列に埋め込まれることはありません。`WHERE name = ?` は
どんな名前でも安全で、うっかり文字列連結してしまう余地がそもそもありません。

トランザクションは本体が正常終了すればコミット、例外を投げればロールバックし、そのうえで
再スローします。`begin`/`commit` のペアを忘れる余地はありません。手動のペアの間で例外が投げられると
接続はトランザクションを開いたままになり、次の無関係な文がそれに巻き込まれます。

行は「列ラベル → 値」の `Map` で、選択した順序を保ちます。列名ではなくラベルなので
`SELECT x AS y` は `y` になります。同じラベルの列が 2 つある場合は、片方を黙って失う代わりに
拒否します。`AS` で別名を付けてください。

---

## Regex

正規表現ユーティリティ。

### マッチング

```
Regex::matches(input, pattern): Boolean   // 文字列全体がマッチ
Regex::find(input, pattern): Boolean      // どこかにパターンが見つかる
```

### 抽出

```
Regex::findAll(input, pattern): List[String]
Regex::findFirst(input, pattern): String
Regex::groups(input, pattern): List[String]   // 最初のマッチのグループ
Regex::groupsAll(input, pattern): List[List[String]]  // 全マッチのグループ
```

### 置換

```
Regex::replace(input, pattern, replacement): String
Regex::replaceFirst(input, pattern, replacement): String
```

### 分割

```
Regex::split(input, pattern): List[String]
Regex::split(input, pattern, limit): List[String]
```

### ユーティリティ

```
Regex::quote(literal): String    // 特殊文字をエスケープ
Regex::isValid(pattern): Boolean
```

### アンカー付きマッチ

```
Regex::matchGroups(input, pattern): List[String]
```

`input` **全体**が `pattern` にマッチしたときだけマッチしたとみなし（`find`/`findAll`
のようにどこかにマッチすれば良いのではなくアンカー付き）、マッチしなければ `null` を
返す。マッチした場合はキャプチャグループを返す(インデックス0がグループ1)。マッチに
参加しなかったグループは `null` ではなく `""` になる。これは `case re"..." (a, b):`
という select パターン（CLAUDE.md の「Regex literals」を参照）を支える基本操作で、
コンパイラはアンカー付き正規表現パターンを `matchGroups` 呼び出しと null チェックに
脱糖する。

### Pattern リテラルのオーバーロード

`re"..."` リテラルは `String` ではなく `java.util.regex.Pattern` にコンパイルされます。
上記のマッチング／抽出／置換／分割の各メソッドには、コンパイル済み `Pattern` を直接
受け取るオーバーロードも用意されており、`re"..."` リテラルを `String` パターンを
経由せずそのまま渡せます:

```
Regex::matches(input, pattern: Pattern): Boolean
Regex::find(input, pattern: Pattern): Boolean
Regex::findAll(input, pattern: Pattern): List[String]
Regex::findFirst(input, pattern: Pattern): String
Regex::groups(input, pattern: Pattern): List[String]
Regex::groupsAll(input, pattern: Pattern): List[List[String]]
Regex::replace(input, pattern: Pattern, replacement): String
Regex::replaceFirst(input, pattern: Pattern, replacement): String
Regex::split(input, pattern: Pattern): List[String]
Regex::split(input, pattern: Pattern, limit): List[String]
```

```
val p = re"[\w.]+@[\w.]+";
val emails: List[String] = Regex::findAll("alice@example.com", p);
```

### 例

```
val text: String = "Email: alice@example.com, bob@test.org";
val emails: List[String] = Regex::findAll(text, "[\\w.]+@[\\w.]+");
// ["alice@example.com", "bob@test.org"]

val masked: String = Regex::replace(text, "@[\\w.]+", "@***");
// "Email: alice@***, bob@***"

if (Regex::matches("hello123", "[a-z]+\\d+")) {
  IO::println("パターンマッチ!");
}
```

---

## 次のステップ

- [基本構文](../guide/basic-syntax.md) - 言語構文の詳細
