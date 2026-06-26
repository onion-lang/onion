# ラムダ式

ラムダ式は匿名関数を簡潔に書く手段です。

## 基本的な構文

`(parameters) -> body` の形式で書きます。ボディは式でもブロックでも使えます：

```onion
// 式ボディ
val double = (x: Int) -> x * 2

// 型推論（期待される関数型が分かる場合）
val triple: Int -> Int = x -> x * 3

// 複数パラメータ
val add = (a: Int, b: Int) -> a + b

// ブロックボディ
val greet: () -> String = () -> { println("Hello!"); return "done"; }
```

## 型推論

ターゲットの関数型が分かっている場合、パラメータの型を省略できます：

```onion
val add: (Int, Int) -> Int = (x, y) -> { return x + y; }
```

型が与えられていない場合はパラメータ型を明示する必要があります：

```onion
val upper = (s: String) -> { return s.toUpperCase(); }
```

## ラムダの呼び出し

関数値は直接関数呼び出し構文で呼べます。`.call()` も使えます：

```onion
val double = (x: Int) -> x * 2
println(double(21))       // 42
println(double.call(21))  // 42
```

## 関数型

矢印型構文 `(A, B) -> R` でラムダの型を書きます。引数が1つの場合は括弧を省略できます（`A -> R`）：

```onion
val func0: () -> Int = () -> { return 42; }
val func1: Int -> Int = (x: Int) -> { return x * 2; }
val func2: (Int, Int) -> Int = (x: Int, y: Int) -> { return x + y; }
```

## JavaのSAM変換

シングルアブストラクトメソッド（SAM）を持つJavaインターフェースにラムダを渡せます：

```onion
val r: Runnable = () -> println("ran")
new Thread(r).start()

val cmp: Comparator[Integer] = (a, b) -> (b as Int) - (a as Int)
Collections::sort(xs, cmp)

// 引数の位置でも使える
Collections::sort(xs, (a, b) -> (a as Int) - (b as Int))
```

## クロージャ

ラムダはエンクロージングスコープの変数をキャプチャできます：

```onion
val multiplier: Int = 10
val multiply: (Int) -> Int = (x: Int) -> { return x * multiplier; }

println(multiply.call(5))  // 50
```

可変変数もキャプチャして変更できます：

```onion
var count: Int = 0
val increment: () -> Int = () -> {
  count = count + 1
  return count;
}

println(increment.call())  // 1
println(increment.call())  // 2
```

## 高階関数

ラムダを引数として受け取る関数の例です：

```onion
import {
  java.util.ArrayList;
  java.util.List;
}

def filter(items: List, predicate: (String) -> Boolean): List {
  val result: ArrayList = new ArrayList

  foreach item: String in items {
    if predicate.call(item) {
      result << item
    }
  }

  return result
}

val lines: List = [
  "INFO: System started",
  "ERROR: Connection failed",
  "ERROR: Timeout"
]

val isError: (String) -> Boolean = (line: String) -> { return line.startsWith("ERROR"); }
val errors: List = filter(lines, isError)
```

## リストのパイプライン

`List` と `Iterable` にはトレイリングラムダ（`{ x => ... }` 構文）でチェインできるヘルパーメソッドがあります：

```onion
val lengths = lines
  .filter { s => s.contains("ERROR") }
  .map { s => s.length() }
```

## 次のステップ

- [関数](functions.md) - 名前付き関数とメソッド
- [コレクション](collections.md) - リスト・マップとパイプライン
- [クラスとオブジェクト](classes-and-objects.md) - オブジェクト指向プログラミング
