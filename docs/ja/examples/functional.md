# 関数型プログラミングの例

Onionの関数型プログラミング機能を示す例です。

## ラムダ式

基本的なラムダ構文:

```onion
// シンプルなラムダ
val double: (Int) -> Int = (x: Int) -> { return x * 2; }
println(double.call(5))  // 10

// 複数パラメータのラムダ
val add: (Int, Int) -> Int = (x: Int, y: Int) -> { return x + y; }
println(add.call(3, 7))  // 10

// パラメータなしのラムダ
val greet: () -> String = () -> { println("Hello!"); return "done"; }
greet.call()
```

## クロージャ

変数を捕捉するラムダ:

**ファイル: `LineFilter.on`**
```onion
import {
  java.io.BufferedReader;
  java.io.FileReader;
  java.io.StringReader;
}

var i: Int = 0
val filter: (String) -> String = (line: String) -> {
  i = i + 1
  return line + " (" + i + ")";
}

val reader: BufferedReader = new BufferedReader(
  new StringReader("First\nSecond\nThird")
)

var line: String = null
while (line = reader.readLine()) != null {
  println(filter.call(line))
}
```

**出力:**
```
First (1)
Second (2)
Third (3)
```

**トピック:**
- 変数 `i` を捕捉するクロージャ
- ラムダによる文字列の変更
- クロージャ内でのインクリメント

## カウンターファクトリ

独立した複数のカウンターを作成:

```onion
def makeCounter(): () -> Int {
  var count: Int = 0
  return () -> {
    count = count + 1
    return count;
  };
}

val counter1: () -> Int = makeCounter()
val counter2: () -> Int = makeCounter()

println(counter1.call())  // 1
println(counter1.call())  // 2
println(counter2.call())  // 1
println(counter1.call())  // 3
println(counter2.call())  // 2
```

各カウンターは独自の `count` 変数を保持します。

## 再帰

**ファイル: `Factorial.on`**
```onion
import { java.lang.NumberFormatException; }

def fact(n :Int) :Int {
  if n < 0 {
    println("Illegal argument")
    return 0
  }
  if n == 0 {
    return 1
  }
  return n * fact(n - 1)
}

var line: String = null
while (line = IO::readln("Enter number: ")) != null {
  try {
    val value: Int = JInteger::parseInt(line)
    val result: Int = fact(value)
    println("Factorial: " + result)
  } catch e :NumberFormatException {
    println("Invalid number")
  }
}
```

**トピック:**
- 再帰関数呼び出し
- ベースケースの処理
- エラー処理のための try-catch

## 再帰によるファイル行数カウント

**ファイル: `LineCounter.on`**（簡略版）
```onion
import {
  java.io.File;
  java.io.FileReader;
  java.io.BufferedReader;
}

def countLines(file :File) :Int {
  if file == null {
    return 0
  }

  if file.isDirectory() {
    var total: Int = 0
    val files: File[] = file.listFiles()

    if files != null {
      foreach f :File in files {
        total = total + countLines(f)
      }
    }

    return total
  }

  if !file.name.endsWith(".on") {
    return 0
  }

  val reader: BufferedReader = new BufferedReader(
    new FileReader(file)
  )

  var count: Int = 0
  var line: String = null
  while (line = reader.readLine()) != null {
    count = count + 1
  }

  reader.close()
  println(file.name + ": " + count)
  return count
}

// 利用例
val dir: File = new File("src")
val total: Int = countLines(dir)
println("Total lines: " + total)
```

**トピック:**
- 再帰的なディレクトリ走査
- ファイル操作
- 条件分岐

## Filter 関数

リストをフィルタリングする高階関数:

```onion
import {
  java.util.ArrayList;
  java.util.List;
}

def filter(items: List, predicate: (String) -> Boolean): ArrayList {
  val result: ArrayList = new ArrayList

  foreach item: String in items {
    if predicate.call(item) {
      result << item
    }
  }

  return result
}

// 利用例
val logs: List = [
  "INFO: Started",
  "ERROR: Failed",
  "INFO: Processing",
  "ERROR: Timeout",
  "INFO: Complete"
]

val isError: (String) -> Boolean = (line: String) -> { return line.startsWith("ERROR"); }

val errors: ArrayList = filter(logs, isError)

foreach error: String in errors {
  println(error)
}
```

**出力:**
```
ERROR: Failed
ERROR: Timeout
```

## Map 関数

コレクションの各要素を変換:

```onion
import { java.util.ArrayList; }

def map(items: java.util.List, transform: (String) -> String): ArrayList {
  val result: ArrayList = new ArrayList

  foreach item: String in items {
    result << transform.call(item)
  }

  return result
}

// 利用例
val words: java.util.List = ["hello", "world", "onion"]
val toUpper: (String) -> String = (s: String) -> { return s.toUpperCase(); }

val upper: ArrayList = map(words, toUpper)

foreach word: String in upper {
  println(word)
}
```

**出力:**
```
HELLO
WORLD
ONION
```

## Reduce 関数

値を蓄積:

```onion
import { java.util.List; }

def reduce(items: List, operation: (Int, Int) -> Int, initial: Int): Int {
  var accumulator: Int = initial

  foreach item: Int in items {
    accumulator = operation.call(accumulator, item)
  }

  return accumulator
}

// 合計
val numbers: List = [1, 2, 3, 4, 5]
val sum: (Int, Int) -> Int = (acc: Int, n: Int) -> { return acc + n; }
val total: Int = reduce(numbers, sum, 0)
println("Sum: " + total)  // 15

// 積
val product: (Int, Int) -> Int = (acc: Int, n: Int) -> { return acc * n; }
val result: Int = reduce(numbers, product, 1)
println("Product: " + result)  // 120
```

## 関数の合成

複数の操作を組み合わせる:

```onion
def compose(f: (Int) -> Int, g: (Int) -> Int): (Int) -> Int {
  return (x: Int) -> { return f.call(g.call(x)); }
}

// 関数の定義
val addTen: (Int) -> Int = (x: Int) -> { return x + 10; }
val double: (Int) -> Int = (x: Int) -> { return x * 2; }

// 合成: 2倍してから10を加える
val composed: (Int) -> Int = compose(addTen, double)

println(composed.call(5))  // (5 * 2) + 10 = 20
```

## カリー化

多引数関数を変換:

```onion
def add(x: Int): (Int) -> Int = (y: Int) -> { return x + y; }

val add5: (Int) -> Int = add(5)
val add10: (Int) -> Int = add(10)

println(add5.call(3))   // 8
println(add10.call(3))  // 13
```

## 実践: ログアナライザ

関数型の概念を組み合わせた完全な例:

```onion
import {
  java.io.BufferedReader;
  java.io.FileReader;
  java.util.ArrayList;
}

def analyzeLog(filename :String) {
  val reader: BufferedReader = new BufferedReader(
    new FileReader(filename)
  )

  var errorCount: Int = 0
  var warningCount: Int = 0
  var infoCount: Int = 0

  val isError: (String) -> Boolean = (line: String) -> { return line.startsWith("ERROR"); }
  val isWarning: (String) -> Boolean = (line: String) -> { return line.startsWith("WARNING"); }
  val isInfo: (String) -> Boolean = (line: String) -> { return line.startsWith("INFO"); }

  var line: String = null
  while (line = reader.readLine()) != null {
    if isError.call(line) {
      errorCount = errorCount + 1
    } else if isWarning.call(line) {
      warningCount = warningCount + 1
    } else if isInfo.call(line) {
      infoCount = infoCount + 1
    }
  }

  reader.close()

  println("Errors: " + errorCount)
  println("Warnings: " + warningCount)
  println("Info: " + infoCount)
}

analyzeLog("app.log")
```

## Do 記法

モナディックな操作をすっきりと繋げます。

### Option の連鎖

```onion
def parseNumber(s: String): Option[Int] {
  try {
    return Option::some(JInteger::parseInt(s));
  } catch e: NumberFormatException {
    return Option::none();
  }
}

// do 記法なし
val result1: Option[Int] = parseNumber("10").flatMap((x: Int) -> {
  return parseNumber("20").map((y: Int) -> {
    return x + y;
  });
})

// do 記法あり - はるかにすっきり！
val result2: Option[Int] = do[Option] {
  x <- parseNumber("10")
  y <- parseNumber("20")
  ret x + y
}

println(result2.getOrElse(0))  // 30
```

### Result によるエラー処理

```onion
def divide(a: Int, b: Int): Result[Int, String] {
  if b == 0 {
    return Result::err("Division by zero");
  }
  return Result::ok(a / b);
}

val calculation: Result[Int, String] = do[Result] {
  x <- Result::ok(100)
  y <- divide(x, 5)      // 20
  z <- divide(y, 4)      // 5
  ret z * 2              // 10
}

select calculation {
  case Result::ok(value):
    println("Result: " + value)
  case Result::err(msg):
    println("Error: " + msg)
}
```

### ネストした Do ブロック

```onion
val nested: Option[Int] = do[Option] {
  x <- Option::some(10)
  inner <- do[Option] {
    y <- Option::some(20)
    ret y * 2
  }
  ret x + inner  // 10 + 40 = 50
}
```

## Future による非同期プログラミング

### Future の作成

```onion
// すでに完了しているFuture
val immediate: Future[Int] = Future::successful(42)

// 非同期計算
val async: Future[String] = Future::async(() -> {
  Thread::sleep(1000L);
  return "Hello after 1 second";
})

// 例外を投げうる操作から
val risky: Future[Int] = Future::asyncThrowing(() -> {
  if Math::random() < 0.5 {
    throw new RuntimeException("Bad luck!");
  }
  return 100;
})
```

### Future の変換

```onion
val numbers: Future[Int] = Future::successful(10)

// map: 値を変換
val doubled: Future[Int] = numbers.map((x: Int) -> { return x * 2; })

// flatMap: 非同期操作を連鎖
val chained: Future[String] = numbers.flatMap((x: Int) -> {
  return Future::async(() -> {
    return "Number is: " + x;
  });
})

// filter: 述語を満たさない場合は失敗
val positive: Future[Int] = numbers.filter((x: Int) -> { return x > 0; })
```

### エラー処理

```onion
val failing: Future[Int] = Future::failed(new RuntimeException("Oops!"))

// デフォルト値で回復
val recovered: Future[Int] = failing.recover((error: Throwable) -> {
  println("Error: " + error.getMessage());
  return 0;
})

// 別のFutureで回復
val retried: Future[Int] = failing.recoverWith((error: Throwable) -> {
  return Future::successful(42);
})
```

### Future の合成

```onion
val f1: Future[Int] = Future::async(() -> { Thread::sleep(100L); return 1; })
val f2: Future[Int] = Future::async(() -> { Thread::sleep(200L); return 2; })

// すべて待つ
val all: Future[Object[]] = Future::all(f1, f2)
all.onSuccess((results: Object[]) -> {
  println("Results: " + results[0] + ", " + results[1]);
})

// 競争: 最初に完了したものが勝つ
val race: Future[Int] = f1.race(f2)
race.onSuccess((winner: Int) -> {
  println("Winner: " + winner);  // 通常 f1
})

// 2つのFutureをzip
val zipped: Future[Object[]] = f1.zip(f2)
```

### コールバック

```onion
val future: Future[String] = Future::async(() -> {
  return "Async result";
})

future
  .onSuccess((value: String) -> { println("Success: " + value); })
  .onFailure((error: Throwable) -> { println("Failed: " + error); })
```

### ブロッキング（控えめに使う）

```onion
val future: Future[Int] = Future::async(() -> { return 42; })

// 完了までブロック
val result: Int = future.await()

// タイムアウト付きでブロック（ミリ秒）
val timed: Int = future.awaitTimeout(5000L)

// 失敗時のデフォルト値付き
val safe: Int = future.getOrElse(0)
```

### Do 記法を使った Future

```onion
def fetchUser(id: Int): Future[String] {
  return Future::async(() -> { return "User" + id; });
}

def fetchProfile(name: String): Future[String] {
  return Future::async(() -> { return name + "'s profile"; });
}

val profile: Future[String] = do[Future] {
  user <- fetchUser(42)
  profile <- fetchProfile(user)
  ret profile.toUpperCase()
}

profile.onSuccess((p: String) -> { println(p); })
// 出力: USER42'S PROFILE
```

### 実践: 並列 API 呼び出し

```onion
def fetchFromApi(url: String): Future[String] {
  return Future::async(() -> {
    // ネットワークリクエストをシミュレート
    Thread::sleep((Math::random() * 1000L) as Long);
    return "Data from " + url;
  });
}

// 複数のリクエストを並列で発行
val api1: Future[String] = fetchFromApi("/users")
val api2: Future[String] = fetchFromApi("/posts")
val api3: Future[String] = fetchFromApi("/comments")

// すべて完了するまで待つ
Future::all(api1, api2, api3).onSuccess((results: Object[]) -> {
  println("Users: " + results[0])
  println("Posts: " + results[1])
  println("Comments: " + results[2])
})

// または do 記法と zip を使う
val combined: Future[String] = do[Future] {
  pair1 <- api1.zip(api2)
  data3 <- api3
  ret pair1[0] as String + " | " + pair1[1] as String + " | " + data3
}
```

## 末尾ラムダ構文

Kotlin風の末尾ラムダで、メソッド呼び出しをすっきりと書けます。

```onion
// 従来の構文
list.map((x: Int) -> { return x * 2; })

// 末尾ラムダ - より簡潔
list.map { x => x * 2 }

// 関数を最後に取る任意のメソッドで利用可能
future.onSuccess { result =>
  println("Got: " + result)
}

// 末尾ラムダの前に他の引数がある場合
api.request("GET", "/users") { response =>
  println(response.body())
}
```

## 次のステップ

- [ラムダ式ガイド](../guide/lambda-expressions.md) - ラムダの詳細
- [関数ガイド](../guide/functions.md) - 関数定義
- [標準ライブラリ](../reference/stdlib.md) - Option、Result、Futureのリファレンス
- [基本例](basic.md) - よりシンプルなプログラム
