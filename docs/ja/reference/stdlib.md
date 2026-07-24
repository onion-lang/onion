# 標準ライブラリ

Onionの標準ライブラリは、一般的な機能のための組み込みモジュールとインターフェースで構成されています。

## モジュール一覧

| 領域 | モジュール |
|------|-----------|
| **I/O・システム** | `IO`（コンソール）, `Files`（ファイル・パス）, `System`, `Proc`（サブプロセス）, `Args`（CLI） |
| **コレクション** | `Colls`（リスト: map/filter/fold, chunked/windowed, sumBy/maxBy）, `Iterables`, `Maps`, `Sets` |
| **テキスト** | `Strings`（大小文字・分割・パディング・パース）, `Text`（wrap/indent/table）, `Regex` |
| **数値** | `Math`, `Stats`（sum/average/median/stddev）, `Format`（桁区切り・bytes・duration） |
| **データ形式** | `Json`, `Yaml`, `Csv` |
| **エンコード** | `Codec`（base64/hex/url）, `Hash`（md5/sha256/…） |
| **関数型** | `Option`, `Result`, `Future` |
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

## Rand モジュール

`onion.Rand`による乱数生成ユーティリティ。

### Rand::int / Rand::long / Rand::double

乱数を生成：

```onion
val randomInt: Int = Rand::int()           // ランダムなInt
val randomLong: Long = Rand::long()        // ランダムなLong
val randomDouble: Double = Rand::double()  // 0.0から1.0
val randomBool: Boolean = Rand::boolean()  // ランダムなBoolean
```

### Rand::nextInt

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
Assert::assertTrue(x > 0)
Assert::assertFalse(list.isEmpty())
Assert::assertEquals(expected, actual)
Assert::assertNotEquals(a, b)
```

### Nullアサーション

```onion
Assert::assertNotNull(result)
Assert::assertNull(errorMessage)
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

scalar の型推論は Json と一致します（`3`→Long、`3.5`→Double、`true`→Boolean、`null`→null）。自分が出力した範囲を読み戻せる round-trip サブセットで、`record ... derive!(Yaml)` の土台になっています。

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
Strings::words("  a  b  c ")             // String[] {"a","b","c"}
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

## 次のステップ

- [基本構文](../guide/basic-syntax.md) - 言語構文の詳細
