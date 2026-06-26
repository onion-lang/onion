# 関数

Onionでは `def` キーワードで名前付き関数（メソッド）を定義します。ラムダ式（匿名関数）もサポートしています。

## 基本的な関数定義

```onion
def greet(name: String): String = "Hello, " + name + "!"

val message: String = greet("Alice")
println(message)  // "Hello, Alice!"
```

式ボディ（`=`）と文ボディ（`{ }`）の両方が使えます：

```onion
def square(x: Int): Int = x * x           // 式ボディ

def max(a: Int, b: Int): Int {            // 文ボディ
  if a > b { return a }
  return b
}
```

## デフォルト引数と名前付き引数

パラメータにデフォルト値を設定できます。呼び出し側は省略したり名前を指定して渡せます：

```onion
def greet(name: String, greeting: String = "Hello"): String {
  return greeting + ", " + name
}

greet("kota")                         // Hello, kota
greet("kota", "Yo")                   // Yo, kota
greet(greeting = "Hi", name = "kota") // 名前付き引数（順序自由）
```

## 可変長引数（Varargs）

末尾の `Type...` パラメータで可変個の引数を配列として受け取ります：

```onion
def join(parts: String...): String {
  var r = ""
  foreach p: String in parts { r = r + p }
  return r
}

join("a", "b", "c")   // "abc"
join(existingArray)   // 配列をそのまま渡せる
```

## 拡張メソッド（Extension Methods）

`extension` ブロックで既存の型（Javaクラスを含む）にメソッドを追加できます：

```onion
extension String {
  def shout(): String { return this.toUpperCase() + "!" }
}

println("hello".shout())   // HELLO!
```

## 末尾再帰の最適化

末尾位置の自己再帰呼び出しはコンパイラが自動的にループに変換します。大きな深さでもスタックオーバーフローが起きません：

```onion
def factorialTail(n: Int, acc: Int): Int {
  if n <= 1 { return acc }
  return factorialTail(n - 1, n * acc)  // 末尾呼び出し → ループに最適化
}

def factorial(n: Int): Int = factorialTail(n, 1)

println(factorial(1000))  // スタックオーバーフローなし
```

## 高階関数

関数を引数として受け取ったり返したりできます：

```onion
def applyTwice(f: (Int) -> Int, value: Int): Int {
  val temp: Int = f.call(value)
  return f.call(temp)
}

val increment: (Int) -> Int = (x: Int) -> { return x + 1; }
val result: Int = applyTwice(increment, 5)  // 7
```

## staticメソッド

クラスレベルのメソッドは `static` で定義し、`::` でアクセスします：

```onion
class MathUtils {
  public:
    static def square(x: Int): Int = x * x
    static def cube(x: Int): Int = x * x * x
}

println(MathUtils::square(5))  // 25
println(MathUtils::cube(3))    // 27
```

## 次のステップ

- [ラムダ式](lambda-expressions.md) - 匿名関数の詳細
- [クラスとオブジェクト](classes-and-objects.md) - クラス内のメソッド
- [標準ライブラリ](../reference/stdlib.md) - 組み込みユーティリティ
