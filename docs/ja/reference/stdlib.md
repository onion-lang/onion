# 標準ライブラリ

Onionの標準ライブラリは、一般的な機能のための組み込みモジュールとインターフェースで構成されています。

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

val list: ArrayList = new ArrayList
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
- `opt.getOrElse(defaultValue)`
- `opt.map(f)` / `opt.flatMap(f)` / `opt.filter(predicate)`

## Result モジュール

`onion.Result`で提供。

- `Result::ok(value)` / `Result::err(error)`
- `Result::ofNullable(value, errorIfNull)` / `Result::trying(operation)`
- `res.map(f)` / `res.mapError(f)` / `res.flatMap(f)` / `res.toOption()`

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

## 次のステップ

- [基本構文](../guide/basic-syntax.md) - 言語構文の詳細
