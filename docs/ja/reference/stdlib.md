# 標準ライブラリ

Onionの標準ライブラリは、一般的な機能のための組み込みモジュールとインターフェースで構成されています。

## モジュール一覧

| 領域 | モジュール |
|------|-----------|
| **I/O・システム** | `IO`（コンソール）, `Files`（ファイル・パス）, `System`, `Proc`（サブプロセス）, `Args`（CLI）, `Http`（HTTPクライアント） |
| **コレクション** | `Colls`（リスト: map/filter/fold, chunked/windowed, sumBy/maxBy）, `Iterables`, `Maps`, `Sets` |
| **テキスト** | `Strings`（大小文字・分割・パディング・パース）, `Text`（wrap/indent/table）, `Regex` |
| **数値** | `Math`, `Stats`（sum/average/median/stddev）, `Format`（桁区切り・bytes・duration） |
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
println(a.zip(b) { p, q => p + q }.defects().size)   // 1 ではなく 2
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

## 関数インターフェース

ラムダとクロージャのための組み込み関数型。`f.call(args)`の代わりに`f(args)`として呼び出せます。

### Function0

パラメータなしの関数：

```onion
val func: Function0[Int] = () -> { return 42; }
val result: Int = func.call()
```

### Function1

1パラメータの関数：

```onion
val double: Function1[Int, Int] = (x: Int) -> { return x * 2; }
val result: Int = double.call(5)
```

### Function2

2パラメータの関数：

```onion
val add: Function2[Int, Int, Int] = (x: Int, y: Int) -> { return x + y; }
val result: Int = add.call(3, 7)
```

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
val elapsedNs: Long = Timing::elapsedNanos(start)    // ナノ秒での経過時間
val elapsedMs: Double = Timing::elapsedMs(start)     // ミリ秒での経過時間
```

### 時間のフォーマット

```onion
val nanos: Long = 1234567890L
val formatted: String = Timing::formatNanos(nanos)   // "1.23s"
// 出力形式: "123ns", "45.67μs", "12.34ms", "1.23s"
```

### スリープ

```onion
Timing::sleep(1000L)  // 1000ミリ秒スリープ
```

### 関数実行時間の計測

```onion
// 実行時間を計測して表示し、結果を返す
val result: Int = Timing::measure(() -> { return expensiveOperation(); })
// 出力: "Elapsed: 123.45ms"

// 表示なしで実行時間（ナノ秒）を取得
val timeNanos: Long = Timing::time(() -> { return expensiveOperation(); })
```

## Option モジュール

`onion.Option`で提供。

- `Option::some(value)` / `Option::none()` / `Option::of(value)`
- `opt.getOrElse(defaultValue)` / `opt.orElseGet(() -> default)` / `opt.orNull()`
- `opt.orElse(otherOption)`
- `opt.map(f)` / `opt.flatMap(f)` / `opt.filter(predicate)`
- `opt.contains(value)` / `opt.exists(predicate)`
- `opt.fold(() -> ifEmpty, v -> ifPresent)` — 単一の値へ畳み込む
- `opt.toList()` — 0個または1個の要素のリスト

## Result モジュール

`onion.Result`で提供。

- `Result::ok(value)` / `Result::err(error)`
- `Result::ofNullable(value, errorIfNull)` / `Result::trying(operation)`
- `res.map(f)` / `res.mapError(f)` / `res.flatMap(f)` / `res.toOption()`
- `res.getOrElse(default)` / `res.orElseGet(() -> default)` / `res.orNull()`
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
```

### コールバック

```onion
val f: Future[String] = Future::async(() -> { return "result"; })

f.onSuccess((value: String) -> { IO::println(value); })
f.onFailure((error: Throwable) -> { IO::println(error); })
```

### ブロッキング操作

```onion
val f: Future[Int] = Future::successful(42)

f.await()              // ブロックして結果を取得（失敗時は例外）
f.awaitTimeout(5000L)  // タイムアウト付きでブロック（ミリ秒）
f.getOrElse(0)         // 結果を取得、失敗時はデフォルト値
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
```

`getString` / `getInt` / `getLong` / `getDouble` / `getFloat` / `getBoolean` / `getShort` / `getByte` でキーから型別に取得します（見つからない・型不一致のときは null）。

これらはボックス化された値を返すため、見つからない場合の null をそのまま非 null なプリミティブへ代入すると NullPointerException になります。`getStringOr` / `getIntOr` / `getLongOr` / `getDoubleOr` / `getFloatOr` / `getBooleanOr(obj, key, default)` はフォールバック値付きでプリミティブを返すので、キーが無くても NPE になりません:

```onion
val obj = Json::parse("{}")
Json::getIntOr(obj, "missing", 42)      // 42（NPE にならない）
Json::getStringOr(obj, "name", "anon")  // "anon"
```

## Yaml モジュール

YAML（flat block mapping のサブセット）のパースとシリアライズ。中間表現は Json と共通です。

### Yaml::parse / Yaml::stringify

```onion
val obj = Yaml::parse("name: ko\nage: 3")    // Object（実体は Map）
val text = Yaml::stringify(obj)               // "name: ko\nage: 3\n"
```

scalar の型推論は Json と一致します（`3`→Long、`3.5`→Double、`true`→Boolean、`null`→null）。自分が出力した範囲を読み戻せる round-trip サブセットで、`record ... derive!(Yaml)` の土台になっています。`:` や前後の空白を含むキーは `key: value` の区切りと衝突しないよう自動的にダブルクォートされます(値側の quoting ルールと同じ)。

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

文字列ユーティリティ（`onion.Strings`、自動 import）。分割・結合・大文字小文字変換・パディングに加え：

```onion
Strings::capitalize("hello")             // "Hello"
Strings::capitalizeWords("a b c")        // "A B C"
Strings::containsIgnoreCase(s, sub) / Strings::equalsIgnoreCase(a, b)
Strings::count("banana", "a")            // 3
Strings::removePrefix("unhappy", "un")   // "happy"
Strings::removeSuffix("running", "ing")  // "runn"
Strings::truncate("hello world", 8, "...")   // "hello..."
Strings::center("hi", 6, '*')            // "**hi**"
Strings::ifBlank("   ", "default")       // "default"
Strings::words("  a  b  c ")             // List[String] ["a","b","c"]
Strings::chars("abc")                    // List ["a","b","c"]
// null 安全なパース（例外を投げずに null/フォールバックを返す）
Strings::toIntOrNull("42") / Strings::toLongOrNull("100") / Strings::toDoubleOrNull("3.14")
Strings::toIntOr("nope", 0)              // 0
```

## Maps モジュール

Map ユーティリティ（`onion.Maps`）。結果 Map は挿入順を保持（`LinkedHashMap`）。

```onion
Maps::getOrElse(m, "x", () -> compute())      // 遅延デフォルト
Maps::keys(m) / Maps::values(m)               // 順序を保ったリスト
Maps::mapValues(m, (v: Int) -> v * 2) / Maps::mapKeys(m, (k: String) -> k.toUpperCase())
Maps::filter(m, (k: String, v: Int) -> v > 0) // キー+値の述語
Maps::invert(m)                               // キーと値を入れ替え
Maps::count(m, p) / Maps::anyEntry(m, p) / Maps::allEntries(m, p)
Maps::groupBy(items, keyOf)                   // Map[K, List]
Maps::countBy(items, keyOf)                   // 頻度 Map[K, Integer]
Maps::mergeWith(a, b, (x: Int, y: Int) -> x + y)  // 衝突を結合
Maps::update(m, "a", (v: Int) -> v + 1)       // 関数的更新
```

## Sets モジュール

Set ユーティリティ（`onion.Sets`）。結果 Set は挿入順を保持し、集合演算は null 安全。

```onion
Sets::of(1, 2, 3) / Sets::fromList([1, 1, 2]) / Sets::toList(a)
Sets::union(a, b) / Sets::intersection(a, b) / Sets::difference(a, b)
Sets::symmetricDifference(a, b)               // どちらか一方だけに含まれる
Sets::isSubsetOf(a, b) / Sets::isSupersetOf(a, b) / Sets::isDisjoint(a, b)
Sets::map(a, f) / Sets::filter(a, p) / Sets::find(a, p)
Sets::count(a, p) / Sets::any(a, p) / Sets::all(a, p)
```

## Hash モジュール

暗号学的ハッシュ・チェックサム（`onion.Hash`）。文字列の UTF-8 バイトをハッシュ化し、小文字 hex のダイジェストを返します。

```onion
Hash::sha256("password")   // 64文字 hex
Hash::sha512(text)         // 128文字 hex
Hash::md5(text) / Hash::sha1(text)   // チェックサム・互換用（衝突耐性なし）
```

## Codec モジュール

テキストのエンコード・デコード（`onion.Codec`）: Base64・hex・URL/パーセント形式。

```onion
val enc = Codec::base64Encode("Hello")    // "SGVsbG8="
Codec::base64Decode(enc)                  // "Hello"
Codec::hexEncode("Hi") / Codec::hexDecode("4869")
Codec::urlEncode("a b&c") / Codec::urlDecode(s)
```

## Stats モジュール

数値リストの集計（`onion.Stats`）。汎用集計は `List[Int]`/`List[Long]`/`List[Double]` を受け付け倍精度で計算。`sumInt`/`sumLong` は整数精度を保持。

```onion
val xs: List[Int] = [10, 20, 30, 40]
Stats::sum(xs)       // 100.0      Stats::sumInt(xs)   // 100
Stats::average(xs)   // 25.0       Stats::median(xs)   // 25.0
Stats::min(xs) / Stats::max(xs)    // 10.0 / 40.0
Stats::variance(xs) / Stats::stddev(xs)
```

メソッド呼び出しの形でも使えます（実際のコードではこちらが自然です）。ただし
**メソッド形式も同じく倍精度**なので、`Int` のリストでも合計は `Double` になります。
`Int` で受け取りたい場合は `Stats::sumInt` を使ってください。

```onion
val xs: List[Int] = [10, 20, 30, 40]
xs.sum()             // 100.0  （Double: 汎用集計）
Stats::sumInt(xs)    // 100    （Int）
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

- `Iterables::map(list|iterable, f)`
- `Iterables::filter(list|iterable, predicate)`
- `Iterables::foldl(iterable, init, f)`
- `Iterables::exists(iterable, predicate)`
- `Iterables::forAll(iterable, predicate)`
- `Iterables::sort(list, comparator)`
- `Iterables::listOf(elements...)`

## Files モジュール

ファイル I/O（`onion.Files`）:

```onion
Files::readText("path.txt")            // ファイル全体を String として
Files::readLines("path.txt")           // List[String]
Files::writeText("out.txt", content)
Files::readBytes(path) / Files::writeBytes(path, bytes)
Files::list("dir")                     // エントリ名の List
Files::glob("dir", "*.on")             // glob にマッチしたエントリ名
Files::delete(path) / Files::exists(path)
```

パス操作ヘルパー——ファイル名・親ディレクトリ・結合・拡張子:

```onion
Files::getFileName("a/b/c.txt")        // "c.txt"
Files::getParent("a/b/c.txt")          // "a/b"
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
val r = Proc::capture("git", "status")  // r.status() / r.stdout() / r.stderr() / r.succeeded()
Proc::run("ls", "-la")                  // stdout を String で取得（失敗時は例外）
Proc::exec("make", "build")             // 終了コード、出力はそのまま素通し
Proc::captureIn("/tmp", "ls")           // ...In 系は作業ディレクトリを指定
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
Colls::sortedBy(people) { p => p.age() }
// map/filter/reduce/fold のパイプラインは List/Iterable/配列の拡張メソッド:
// xs.map { x => x * 2 }.filter { x => x > 0 }
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
