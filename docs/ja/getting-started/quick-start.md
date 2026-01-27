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
  IO::println("xは5より大きい")
} else {
  IO::println("xは5以下")
}
```

### Whileループ

```onion
var i: Int = 0
while i < 5 {
  IO::println(i)
  i = i + 1
}
```

### Forループ

```onion
for var i: Int = 0; i < 10; i = i + 1 {
  IO::println("カウント: " + i)
}
```

### Foreachループ

```onion
val names: java.util.List = ["Alice", "Bob", "Charlie"]
foreach name: String in names {
  IO::println("こんにちは、" + name)
}
```

### Select文（パターンマッチング）

```onion
val score: Int = 85

select score {
  case 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100:
    IO::println("評価: A")
  case 80, 81, 82, 83, 84, 85, 86, 87, 88, 89:
    IO::println("評価: B")
  case 70, 71, 72, 73, 74, 75, 76, 77, 78, 79:
    IO::println("評価: C")
  else:
    IO::println("評価: F")
}
```

## 関数

### 基本的な関数

```onion
def greet(name: String): String = "こんにちは、" + name + "さん！"

val message: String = greet("世界")
IO::println(message)
```

### ラムダ式

```onion
// ラムダ構文: (params) -> { body }
val add: (Int, Int) -> Int = (x: Int, y: Int) -> { return x + y; }

val result: Int = add.call(5, 3)  // 8
IO::println(result)
```

### 複数パラメータの関数

```onion
def multiply(a: Int, b: Int): Int = a * b

IO::println(multiply(6, 7))  // 42
```

## 配列とコレクション

### 配列

```onion
// 固定サイズ配列
val numbers: Int[] = new Int[5]
numbers[0] = 10
numbers[1] = 20

val colors: String[] = new String[3]
colors[0] = "赤"
colors[1] = "緑"
colors[2] = "青"

// 配列のイテレーション
foreach color: String in colors {
  IO::println(color)
}
```

### リスト（ArrayList）

```onion
import {
  java.util.ArrayList;
}

val list: ArrayList = new ArrayList
list << "最初"   // << は追加演算子
list << "2番目"
list << "3番目"

IO::println(list.size)  // 3
IO::println(list[0])     // "最初"
```

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
IO::println(person.greet)
```

ポイント：
- フィールドは`this.field`でアクセス
- メンバーはデフォルトでprivate
- `public:`で公開メンバーをマーク
- `def this`でコンストラクタを宣言

### 継承

```onion
import {
  java.util.List;
  java.util.ArrayList;
}

class MyList <: List {
  forward val internal: List;

  public:
    def this {
      this.internal = new ArrayList;
    }
}
```

構文：
- `class Child : Parent` - クラスを継承
- `class Impl <: Interface` - インターフェースを実装
- `class Multi : Parent <: Interface` - 両方

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
  IO::println("ファイルが存在します！")
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
  IO::println(value)
} catch e: NumberFormatException {
  IO::println("無効な数値形式: " + e.getMessage)
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
val list: java.util.ArrayList = new java.util.ArrayList
```

## 次のステップ

- [基本構文](../guide/basic-syntax.md) - 言語機能の詳細
- [標準ライブラリ](../reference/stdlib.md) - 組み込みモジュール
