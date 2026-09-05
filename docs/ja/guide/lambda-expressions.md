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

### トレイリングラムダ

ラムダが呼び出しの最後の引数なら、呼び出しの後ろに波括弧ブロックとして書けます。
パラメータは波括弧の中の `->` の前に置き、パラメータがなければ矢印も不要です。
ブロック最後の式がラムダの値になります。インスタンス呼び出し・static 呼び出し・
非修飾呼び出しのいずれにも、間に引数リストがあってもなくても付けられます：

```onion
list.map { x -> x * 2 }
list.fold(0) { acc, x -> acc + x }
val f: Future[Int] = Future::async { compute() }
val g: Future[Int] = Future::async { -> compute() }   // 同じ意味（矢印を明示）
Timing::measure("step") { expensiveOperation() }
```

条件位置 ── `if`・`while`・`do ... while` の条件、`foreach ... in` の反復対象、
`select` の被検査式、`for` ヘッダ ── では裸の `{` は従来どおり文ブロックです。
`if flag { ... }` や `foreach x in xs { ... }` の意味は変わりません。そこでラムダに
なるのは矢印付きの形だけで、矢印なしのラムダを条件の中で渡したいときは呼び出しを
括弧で囲みます：

```onion
if xs.any { x -> x > 0 } { println("positive") }   // 矢印付き：ラムダ
if (once { 1 }) == 1 { println("one") }             // 括弧付き：ラムダ
if ready { println("go") }                          // if のブロック
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

関数値は直接、関数呼び出し構文で呼べます：

```onion
val double = (x: Int) -> x * 2
println(double(21))       // 42
```

内部的には、関数値は `onion.Function0` 〜 `onion.Function10` を実装したオブジェクトで、
唯一のメソッドが `call` です。`double(21)` は `double(21)` にコンパイルされます。
自分で `.call` と書く必要があるのは、その名前で呼び出すことを期待する Java コードに
関数値を渡すときだけです。

## 関数型

矢印型構文 `(A, B) -> R` でラムダの型を書きます。引数が1つの場合は括弧を省略できます（`A -> R`）：

```onion
val func0: () -> Int = () -> { return 42; }
val func1: Int -> Int = (x: Int) -> { return x * 2; }
val func2: (Int, Int) -> Int = (x: Int, y: Int) -> { return x + y; }
```

### void/Unit を返す関数型

副作用のみのラムダでは、返り型を `void` または `Unit` と書けます。実行時には `Object` に消去され、ラムダ本体は `null` を返します：

```onion
def repeat(n: Int, block: () -> Unit): void {
  for var i: Int = 0; i < n; i = i + 1 {
    block()
  }
}

repeat(3, () -> { println("tick") })
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

### プリミティブな型引数

Onionはジェネリックインターフェースの型引数をボックス化するため、
`Comparator[Int]` は内部的に `Comparator[Integer]` として表現されます。
それでもラムダのパラメータはプリミティブ型のまま書けます：

```onion
import { java.util.Comparator }

val cmp: Comparator[Int] = (a: Int, b: Int) -> a - b
Collections::sort(xs, cmp)

// 同じラムダを引数の位置に直接書くこともできます。
Collections::sort(xs, (a: Int, b: Int) -> a - b)
```

コンパイラはプリミティブなパラメータ型をボックス化されたインターフェース
シグネチャと照合し、必要なブリッジメソッドを自動生成します。戻り値型が
プリミティブな場合も同様です：

```onion
import { java.util.function.Supplier }

val s: Supplier[Int] = () -> 42
```

## クロージャ

ラムダはエンクロージングスコープの変数をキャプチャできます：

```onion
val multiplier: Int = 10
val multiply: (Int) -> Int = (x: Int) -> { return x * multiplier; }

println(multiply(5))  // 50
```

可変変数もキャプチャして変更できます：

```onion
var count: Int = 0
val increment: () -> Int = () -> {
  count = count + 1
  return count;
}

println(increment())  // 1
println(increment())  // 2
```

## 高階関数

ラムダを引数として受け取る関数の例です：

```onion
import {
  java.util.ArrayList;
  java.util.List;
}

def filter(items: List[String], predicate: (String) -> Boolean): List[String] {
  val result: ArrayList[String] = new ArrayList[String]()

  foreach item: String in items {
    if predicate(item) {
      result << item
    }
  }

  return result
}

val lines: List[String] = [
  "INFO: System started",
  "ERROR: Connection failed",
  "ERROR: Timeout"
]

val isError: (String) -> Boolean = (line: String) -> { return line.startsWith("ERROR"); }
val errors: List[String] = filter(lines, isError)
```

## リストのパイプライン

`List` と `Iterable` にはトレイリングラムダ（`{ x -> ... }` 構文）でチェインできるヘルパーメソッドがあります：

```onion
val lengths = lines
  .filter { s -> s.contains("ERROR") }
  .map { s -> s.length() }
```

## 次のステップ

- [関数](functions.md) - 名前付き関数とメソッド
- [コレクション](collections.md) - リスト・マップとパイプライン
- [クラスとオブジェクト](classes-and-objects.md) - オブジェクト指向プログラミング
