# Hello World

最初のOnionプログラムを書いて実行してみましょう！

## 定番のHello World

`Hello.on`という名前のファイルを作成します：

```onion
IO::println("Hello, World!")
```

これだけです！この1行が完全なOnionプログラムです。

## プログラムの実行

Onionプログラムを実行するには2つの方法があります：

### 方法1: `onion`で直接実行

`onion`コマンドはプログラムをメモリ内でコンパイルして実行します：

```bash
onion Hello.on
```

出力：
```
Hello, World!
```

### 方法2: `onionc`でコンパイル

`.class`ファイルにコンパイルしてJavaで実行：

```bash
# コンパイル
onionc Hello.on

# Javaで実行
java Hello
```

## コードの解説

```onion
IO::println("Hello, World!")
```

- `IO` - 組み込みのI/Oモジュール
- `::` - 静的メソッドアクセス演算子（Javaの`.`に類似）
- `println()` - 改行付き出力関数
- `"Hello, World!"` - 文字列リテラル

## その他の例

### 変数の使用

```onion
val message: String = "Hello, Onion!"
IO::println(message)
```

### ユーザー入力の取得

```onion
val name: String = IO::readln("名前は？ ")
IO::println("こんにちは、" + name + "さん！")
```

実行：
```bash
onion greeting.on
```

出力：
```
名前は？ Alice
こんにちは、Aliceさん！
```

### Mainクラスを使用

明示的なクラス定義でプログラムを書くこともできます：

```onion
class HelloWorld {
  public:
    static def main(args: String[]) {
      IO::println("Hello, World!")
    }
}
```

これは従来のJavaのmainメソッドパターンに従います。

## コマンドライン引数

`main`メソッドを通じてコマンドライン引数にアクセス：

```onion
class ArgsDemo {
  public:
    static def main(args: String[]) {
      IO::println("引数の数: " + args.length)
      for var i: Int = 0; i < args.length; i = i + 1 {
        IO::println("引数 " + i + ": " + args[i])
      }
    }
}
```

引数付きで実行：
```bash
onion ArgsDemo.on hello world 123
```

出力：
```
引数の数: 3
引数 0: hello
引数 1: world
引数 2: 123
```

## 次のステップ

最初のOnionプログラムを書いたので、次に進みましょう：

- [クイックスタートガイド](quick-start.md) - 基本的な言語機能を学ぶ
- [基本構文](../guide/basic-syntax.md) - 変数、型、演算子
