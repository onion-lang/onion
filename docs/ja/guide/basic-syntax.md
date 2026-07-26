# 基本構文

このガイドでは、Onionプログラミング言語の基本的な構文要素を説明します。

## コメント

Onionは現在、C言語スタイルのコメントをサポートしています：

```onion
// 単一行コメント

/*
 * 複数行コメント
 * 複数行にまたがります
 */
```

## 変数

### 変数宣言

変数は`val`（不変）または`var`（可変）で宣言します。ローカル宣言では初期化子がある場合、型を省略できます：

```onion
val name = "Alice"
var count = 0
val age: Int = 30
val price: Double = 19.99
val isActive: Boolean = true
```

### 命名規則

- 変数名はキャメルケース: `firstName`, `totalCount`
- クラス名はパスカルケース: `Person`, `Calculator`
- フィールドは`this.field`でアクセス

## プリミティブ型

Onionは標準的なJVMプリミティブ型をサポートします：

| 型 | 説明 | 例 |
|------|-------------|---------|
| `Int` | 32ビット整数 | `42` |
| `Long` | 64ビット整数 | `42L` |
| `Double` | 64ビット浮動小数点 | `3.14` |
| `Float` | 32ビット浮動小数点 | `3.14f` |
| `Boolean` | true または false | `true`, `false` |
| `Char` | 単一文字 | `'A'` |
| `Byte` | 8ビット整数 | `127` |
| `Short` | 16ビット整数 | `1000` |

## リテラル

### 文字列リテラル

```onion
val greeting: String = "Hello, World!"
val multiline: String = "Line 1\nLine 2\nLine 3"
val withQuotes: String = "She said \"Hello\""
```

### 数値リテラル

```onion
val decimal: Int = 42
val hex: Int = 0xFF
val longValue: Long = 100L
val doubleValue: Double = 3.14
val floatValue: Float = 2.5f       // f/F で Float、d/D で Double（大文字・小文字どちらも可）
val scientific: Double = 1.23e10
```

## 演算子

### 算術演算子

```onion
val a: Int = 10
val b: Int = 3

val sum: Int = a + b        // 13
val diff: Int = a - b       // 7
val product: Int = a * b    // 30
val quotient: Int = a / b   // 3
val remainder: Int = a % b  // 1
```

### 比較演算子

```onion
val x: Int = 5
val y: Int = 10

x == y  // false（等しい）
x != y  // true（等しくない）
x < y   // true（より小さい）
x > y   // false（より大きい）
x <= y  // true（以下）
x >= y  // false（以上）
```

### 論理演算子

```onion
val a: Boolean = true
val b: Boolean = false

a && b  // false（論理積）
a || b  // true（論理和）
!a      // false（論理否定）
```

### 型キャスト演算子

`as`キーワードで型キャストを行います。メソッドチェインの場合は括弧が必要です：

```onion
val x: Double = 3.14
val y: Int = x as Int  // Intにキャスト (3)

val random: Double = Math::random()
val randomInt: Int = (random * 100) as Int

// メソッドチェインの場合は括弧が必要
val btn: Object = getButton()
val text: String = (btn as JButton).getText()
```

### リスト追加演算子

`<<`演算子でリストに追加します：

```onion
import { java.util.ArrayList; }

val list: ArrayList[String] = new ArrayList[String]()
list << "最初"
list << "2番目"
list << "3番目"
```

## リストとマップ

リストは角括弧で書きます。標準ライブラリはリストを受け取り・返すので、普段使うのは
ほとんどこちらです。配列ではありません。

```onion
val colors = ["赤", "緑", "青"]
val scores = [95.5, 87.3, 91.0]
val empty: List[String] = []          // 推論の手がかりが無いときは型を書く
```

要素の読み書きは `[]`、要素数は `.size` です。

```onion
val first = colors[0]      // "赤"
val n = colors.size        // 3
```

マップリテラルはキーと値を対にします。

```onion
val ages = ["alice": 30, "bob": 25]
val lookup = ages["alice"]            // 30
val nothing: Map[String, Int] = [:]   // 空のマップ
```

リストにはパイプラインが組み込まれているので、添字を使う場面はほとんどありません。

```onion
val odds = [1, 2, 3, 4, 5].filter { n => n % 2 == 1 }   // [1, 3, 5]
val tens = odds.map { n => n * 10 }                     // [10, 30, 50]
```

全体は [コレクション](collections.md) を参照してください。

### 配列

配列もありますが、必要になるのは Java と話すときだけです。`main(args: String[])`、
バイナリ I/O の `byte[]`、可変長引数がその典型です。

```onion
val buffer: String[] = new String[3]
buffer[0] = "a"
val length: Int = buffer.length   // 配列は .size ではなく .length
```

パイプラインを使いたくなったら `Colls::toList(args)` でリストに渡せます。

## スコープと可視性

### ローカル変数

メソッドやブロック内で宣言された変数は、そのスコープ内でのみ有効：

```onion
def method {
  val local: Int = 10
  // localはここでのみ有効
}
```

### フィールド

フィールドは`val`/`var`で宣言し、`this.field`でアクセス：

```onion
class Example {
  var count: Int
  val name: String = "default"

  public:
    def increment {
      this.count = this.count + 1
    }
}
```

### アクセス修飾子

デフォルトではメンバーはprivateです。`public:`で公開メンバーをマーク：

```onion
class Person {
  val ssn: String = "000-00-0000"  // デフォルトでprivate

  public:
    val name: String = "Alice"  // public

    def getName: String = this.name  // publicメソッド
}
```

## インターフェース

インターフェースは本体を持たないメソッドシグネチャを宣言します。インターフェース全体を1行で書くことができ、本体のないメソッドの直後に閉じ `}` を同じ行に置くこともできます：

```onion
interface Shape { def area(): Double }

class Circle : Object <: Shape {
  val r: Double
public:
  def this(r: Double) { this.r = r }
  override def area(): Double = 3.14 * this.r * this.r
}

val s: Shape = new Circle(2.0)
println(s.area())   // 12.56
```

## モジュールシステム

### Import文

JavaクラスをOnionコードで使用するためにインポート。エイリアスも使用可能：

```onion
import {
  java.util.ArrayList;
  java.util.HashMap;
  java.io.File;
  java.lang.Long as JLong;
}

val list: ArrayList[String] = new ArrayList[String]()
val map: HashMap[String, String] = new HashMap[String, String]()
val file: File = new File("data.txt")
val str: String = JLong::toString(42L)
```

### 完全修飾名

インポートなしでも完全修飾名を使用できます：

```onion
val list: java.util.ArrayList[String] = new java.util.ArrayList[String]()
```

## 次のステップ

- [標準ライブラリ](../reference/stdlib.md) - 組み込みモジュールとユーティリティ
