# クイックスタート

このガイドでは、Onionの基本機能を説明して、すぐに使えるようにします。

## 変数と型

Onionは静的型付け言語です。ローカルの`val`/`var`宣言では、型注釈を含めるか、初期化子がある場合は省略できます：

```onion
// 型注釈付き
val name: String = "Alice"
val age: Int = 30
val price: Double = 19.99

// 型推論（ローカルのみ）
val count = 42         // Intと推論
var greeting = "Hello" // Stringと推論
```

## 制御フロー

### If-Else

```onion
val x: Int = 10

if x > 5 {
  println("xは5より大きい")
} else {
  println("xは5以下")
}
```

### Whileループ

```onion
var i: Int = 0
while i < 5 {
  println(i)
  i = i + 1
}
```

### Forループ

```onion
for var i: Int = 0; i < 10; i = i + 1 {
  println("カウント: " + i)
}
```

### Foreachループ

```onion
val names: java.util.List[String] = ["Alice", "Bob", "Charlie"]
foreach name: String in names {
  println("こんにちは、" + name)
}
```

### Select文（パターンマッチング）

```onion
val score: Int = 85

select score {
  case 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100:
    println("評価: A")
  case 80, 81, 82, 83, 84, 85, 86, 87, 88, 89:
    println("評価: B")
  case 70, 71, 72, 73, 74, 75, 76, 77, 78, 79:
    println("評価: C")
  else:
    println("評価: F")
}
```

## 関数

### 基本的な関数

```onion
def greet(name: String): String = "こんにちは、" + name + "さん！"

val message: String = greet("世界")
println(message)
```

### ラムダ式

```onion
// ラムダ構文: (params) -> { body }
val add: (Int, Int) -> Int = (x: Int, y: Int) -> { return x + y; }

val result: Int = add.call(5, 3)  // 8
println(result)
```

### 複数パラメータの関数

```onion
def multiply(a: Int, b: Int): Int = a * b

println(multiply(6, 7))  // 42
```

## リストとマップ

```onion
val colors = ["赤", "緑", "青"]
val ages = ["alice": 30, "bob": 25]

println(colors[0])        // "赤"
println(colors.size)      // 3
println(ages["alice"])    // 30

foreach color: String in colors {
  println(color)
}
```

パイプラインが組み込まれているので、添字を使う場面はほとんどありません。

```onion
val odds = [1, 2, 3, 4, 5].filter { n => n % 2 == 1 }   // [1, 3, 5]
val tens = odds.map { n => n * 10 }                     // [10, 30, 50]
```

標準ライブラリはリストを受け取り・返します。配列は Java と話すときのもので——
`main(args: String[])`、`byte[]` など——リストの `.size` に対して `.length` を使います。

## クラスとオブジェクト

### 基本的なクラス

```onion
class Person {
  val name: String
  var age: Int

  public:
    def this(n: String, a: Int) {
      this.name = n
      this.age = a
    }

    def greet: String = "こんにちは、私は" + this.name + "です"
}

val person: Person = new Person("Alice", 30)
println(person.greet)
```

ポイント：
- フィールドは`this.field`でアクセス
- メンバーはデフォルトでprivate
- `public:`で公開メンバーをマーク
- `def this`でコンストラクタを宣言

### 継承

```onion
interface Logger {
  def log(message: String): void
  def count(): Int
}

class BasicLogger <: Logger {
  var n: Int

  public:
    def this { this.n = 0 }
    def log(message: String): void {
      this.n = this.n + 1
      println(message)
    }
    def count(): Int = n
}

// `forward` は Logger インターフェースを `delegate` への委譲で自動実装します。
class PrefixLogger <: Logger {
  forward val delegate: Logger

  public:
    def this(delegate: Logger) {
      this.delegate = delegate
    }
}
```

構文：
- `class Child : Parent` - クラスを継承
- `class Impl <: Interface` - インターフェースを実装
- `class Multi : Parent <: Interface` - 両方
- `forward val m: Interface` - `Interface` を `m` への委譲で自動実装

## Java相互運用

OnionはJavaライブラリにシームレスにアクセスできます：

```onion
import {
  java.io.File;
  javax.swing.JFrame;
  javax.swing.JButton;
}

val frame: JFrame = new JFrame("マイウィンドウ")
frame.setSize(400, 300)
frame.setVisible(true)

val file: File = new File("data.txt")
if file.exists {
  println("ファイルが存在します！")
}
```

### よく使うJavaクラス

```onion
// Math操作
val random: Double = Math::random()
val sqrt: Double = Math::sqrt(16.0)

// 文字列操作
val upper: String = "hello".toUpperCase()
val length: Int = "hello".length

// パース
val num: Int = JInteger::parseInt("42")
```

## 例外処理

```onion
try {
  val value: Int = JInteger::parseInt("数字じゃない")
  println(value)
} catch e: NumberFormatException {
  println("無効な数値形式: " + e.getMessage)
}
```

## 型キャスト

型キャストには`as`キーワードを使用します。メソッドチェインの場合は括弧が必要です：

```onion
val random: Double = Math::random()
val randomInt: Int = (random * 100) as Int

val obj: Object = "Hello"
val str: String = obj as String

// メソッドチェインの場合は括弧が必要
val btn: Object = getButton()
val text: String = (btn as JButton).getText()
```

## モジュールシステム

```onion
// Javaクラスをインポート
import {
  java.util.ArrayList;
  java.util.HashMap;
  java.io.File;
}

// インポートなしで完全修飾名を使用
val list: java.util.ArrayList[String] = new java.util.ArrayList[String]()
```

## Shape — 雑なデータを読む

ここが、上に挙げた言語と違う部分です。`shape` 句はテキストとレコードの対応を記述し、
その一つの記述から両方向が出てきます。

```onion
record Pt(x: Int, y: Int)
  shape text = re"(-?\d+),(-?\d+)"
  shape doc  = json
  law roundtrip(p: Pt) { Pt::text().parse(Pt::text().print(p)).get() == p }
```

読み取りは `Outcome` を返します。値か、値でない理由**すべて**です。

```onion
val o = Pt::text().parse("3,x")
if o.isOk() { println(o.get().x()) }
else        { println(o.describe()) }
// <input>:1: : expected match of /(-?\d+),(-?\d+)/, found 3,x
```

失敗は、どこから来たか・何を期待したかを知っています。`null` はどちらも伝えられません。

複数行を読むときは、失敗した行を捨てずに保持します。

```onion
val each = file"points.txt".eachLine(Pt::text())
println(Outcome::values(each).size + " 行読めた, " + Outcome::defects(each).size + " 行読めなかった")
```

この `law` はコメントではありません。ビルド中にコンパイラが実行します。
詳しくは [Shape ガイド](../guide/shapes.md) を参照してください。

## 次のステップ

- [基本構文](../guide/basic-syntax.md) - 言語機能の詳細
- [標準ライブラリ](../reference/stdlib.md) - 組み込みモジュール
