# 基本例

Onionの基本的な機能を示すシンプルなプログラムです。

## Hello World

最もシンプルなOnionプログラム:

**ファイル: `Hello.on`**
```onion
println("Hello")
```

**実行:**
```bash
onion Hello.on
```

**出力:**
```
Hello
```

## 配列

配列の扱い方:

**ファイル: `Array.on`**
```onion
val array: String[] = new String[3]
array[0] = "A"
array[1] = "B"
array[2] = "C"

for var i: Int = 0; i < array.length; i = i + 1 {
  println(array[i])
}
```

**トピック:**
- 配列の宣言と初期化
- インデックスによるアクセス
- `.length` プロパティを使ったforループ

## 文字列の連結

文字列操作とリストの反復:

**ファイル: `StringCat.on`**
```onion
val list: List = ["a", "b", "c", "d", "e", "f", "g"];
for var i: Int = 0; i < list.size; i = i + 1 {
  System::out.println("list[" + i + "] = " + list[i]);
}
```

**トピック:**
- 配列リテラル
- `+` による文字列連結
- `.size` プロパティ
- 標準出力

## ユーザ入力

ユーザ入力の読み取り:

**ファイル: `ReadLine.on`**
```onion
val name: String = IO::readln("What's your name? ")
println("Hello, " + name + "!")
```

**実行:**
```bash
onion ReadLine.on
```

**対話:**
```
What's your name? Alice
Hello, Alice!
```

**トピック:**
- 入力読み取りの `IO::readln()`
- 文字列連結
- 対話型プログラム

## 動的リスト

`<<` 追加演算子を使ったArrayListの利用:

**ファイル: `List.on`**
```onion
val list: List = [];

list << "a";
list << "b";
list << "c";
list << "d";

for var i: Int = 0; i < list.size; i = i + 1 {
  System::out.println(list[i]);
}
```

**トピック:**
- Javaクラスのインポート
- ArrayListの作成
- `<<` 追加演算子
- コレクションのインデックスアクセス

## Foreach ループ

コレクションを使った拡張forループ:

**ファイル: `Foreach.on`**
```onion
import { java.util.ArrayList; }

val list: ArrayList[String] = new ArrayList[String]
list << "HELLO";
list << "WORLD";
list << "ONION";

foreach object :String in list {
  println(object.toLowerCase())
}
```

**出力:**
```
hello
world
onion
```

**トピック:**
- `foreach` ループ構文
- ループ変数の型注釈
- ループ変数に対するメソッド呼び出し

## パターンマッチング

`select` 文の利用:

**ファイル: `Select.on`**
```onion
val value: Int = (Math::random() * 10) as Int

select value {
  case 0, 1, 2, 3:
    println("Low: " + value)
  case 4, 5, 6:
    println("Medium: " + value)
  case 7, 8, 9:
    println("High: " + value)
  else:
    println("Out of range: " + value)
}
```

**トピック:**
- `select` 文
- 1つのcaseに複数の値
- `else` デフォルト節
- `as` による型キャスト
- `Math::random()` 関数

## 総合例: 配列処理

複数の概念を組み合わせた例:

```onion
import { java.util.ArrayList; }

// 数値リストの作成
val numbers: java.util.List = [10, 20, 30, 40, 50]

// 偶数のフィルタリング
val evens: ArrayList = new ArrayList
foreach num :Int in numbers {
  if num % 2 == 0 {
    evens << num
  }
}

// 結果の出力
println("Even numbers:")
foreach even :Object in evens {
  println((even as Int))
}
```

**出力:**
```
Even numbers:
10
20
30
40
50
```

## 総合例: 簡単な電卓

```onion
val x: Int = 10
val y: Int = 3

println("Addition: " + (x + y))
println("Subtraction: " + (x - y))
println("Multiplication: " + (x * y))
println("Division: " + (x / y))
println("Modulo: " + (x % y))
```

**出力:**
```
Addition: 13
Subtraction: 7
Multiplication: 30
Division: 3
Modulo: 1
```

## 次のステップ

- [オブジェクト指向の例](oop.md) - オブジェクト指向プログラム
- [関数型の例](functional.md) - ラムダと高階関数
- [言語ガイド](../guide/overview.md) - 機能の詳細解説
