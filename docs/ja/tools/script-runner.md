# スクリプトランナー（onion）

`onion` コマンドは、Onionのソースファイルをメモリ上でコンパイルして即座に実行します。`.class` ファイルは作成されません。

## 使い方

```bash
onion [オプション] ソースファイル... [プログラム引数]
```

## オプション

### `-classpath <classpath>`

コンパイルと実行時のクラスパスを設定します。

```bash
onion -classpath lib/mylib.jar MyScript.on
```

### `-encoding <encoding>`

ソースファイルの文字エンコーディングを指定します。

```bash
onion -encoding UTF-8 MyScript.on
```

### `-maxErrorReports <count>`

報告するコンパイルエラーの最大数を制限します。

```bash
onion -maxErrorReports 10 MyScript.on
```

### `--dump-ast`

スクリプト実行前に、構文解析後のASTを標準エラー出力に出力します。

```bash
onion --dump-ast MyScript.on
```

### `--dump-typed-ast`

スクリプト実行前に、型付きASTの概要を標準エラー出力に出力します。

```bash
onion --dump-typed-ast MyScript.on
```

### `--profile-compile`

スクリプト実行前にコンパイルプロファイルを出力します。

```bash
onion --profile-compile MyScript.on
```

### `--profile-format <text|json>`

プロファイル出力をテキストまたはJSONから選択します。

```bash
onion --profile-compile --profile-format json MyScript.on
```

### `--profile-output <target>`

コンパイルプロファイルを `stderr`、`stdout`、またはファイルパスに出力します。

```bash
onion --profile-compile --profile-format json \
      --profile-output target/script-profile.json \
      MyScript.on
```

### `--warn <off|on|error>`

警告の扱いを制御します。`error` を指定すると警告をコンパイルエラーとして扱います。

```bash
onion --warn error MyScript.on
```

### `--Wno <codes>`

特定の警告カテゴリをコードまたは名前で抑制します。

```bash
onion --Wno W0001,unused-parameter MyScript.on
```

## プログラム引数

ソースファイルの後ろに指定した引数は、プログラムに渡されます。

```bash
onion MyScript.on arg1 arg2 arg3
```

コード内では次のようにアクセスします。

```onion
class MyScript {
  public:
    static def main(args :String[]): void {
      foreach arg :String in args {
        println("Argument: " + arg)
      }
    }
}
```

## エントリーポイント

スクリプトランナーはエントリーポイントを自動的に決定します。

### 1. 明示的な main メソッド

クラスに `main` メソッドがあれば、それがエントリーポイントになります。

```onion
class MyProgram {
  public:
    static def main(args :String[]): void {
      println("Hello from main method")
    }
}
```

### 2. 最初に定義された main を持つクラス

複数のクラスに `main` メソッドがある場合、最初に定義されたものが使われます。

```onion
class First {
  public:
    static def main(args :String[]): void {
      println("This will run")
    }
}

class Second {
  public:
    static def main(args :String[]): void {
      println("This won't run")
    }
}
```

### 3. トップレベルの宣言と式

明示的な `main` メソッドがない場合、最初のトップレベルの宣言または式がエントリーポイントになります。

```onion
println("Hello, World!")

val x: Int = 10
println("x = " + x)

// これらのブロック要素は即座に実行される
```

## 例

### シンプルなスクリプト

**hello.on:**
```onion
println("Hello, World!")
```

実行:
```bash
$ onion hello.on
Hello, World!
```

### 引数付き

**greet.on:**
```onion
class Greeter {
  public:
    static def main(args :String[]): void {
      if args.length > 0 {
        println("Hello, " + args[0] + "!")
      } else {
        println("Hello, stranger!")
      }
    }
}
```

実行:
```bash
$ onion greet.on Alice
Hello, Alice!

$ onion greet.on
Hello, stranger!
```

### 簡単な計算

**calc.on:**
```onion
val a: Int = 10
val b: Int = 20
println("Sum: " + (a + b))
println("Product: " + (a * b))
```

実行:
```bash
$ onion calc.on
Sum: 30
Product: 200
```

### ファイル処理

**count_lines.on:**
```onion
import {
  java.io.BufferedReader;
  java.io.FileReader;
}

class LineCounter {
  public:
    static def main(args :String[]): void {
      if args.length == 0 {
        println("Usage: onion count_lines.on <filename>")
        return
      }

      val filename: String = args[0]
      val reader: BufferedReader = new BufferedReader(
        new FileReader(filename)
      )

      var count: Int = 0
      var line: String = null
      while (line = reader.readLine()) != null {
        count = count + 1
      }

      reader.close()
      println("Lines: " + count)
    }
}
```

実行:
```bash
$ onion count_lines.on data.txt
Lines: 42
```

## メモリ内コンパイル

`onion` コマンドは以下の流れで動作します。

1. ソースファイルをバイトコードにコンパイル
2. クラスをメモリにロード
3. エントリーポイントを実行
4. `.class` ファイルは作成されない

これにより、以下の用途に最適です。
- 簡単なスクリプト
- コードスニペットのテスト
- 自動化タスク
- 使い捨てプログラム

## 複数ソースファイル

複数のファイルをまとめてコンパイル・実行できます。

```bash
onion Main.on Utils.on Helper.on
```

すべてのファイルが一緒にコンパイルされ、エントリーポイントは最初のファイルから決定されます。

## エラー処理

### コンパイルエラー

```bash
$ onion bad_syntax.on
Error: Type mismatch at bad_syntax.on:5
Compilation failed
```

### 実行時エラー

```bash
$ onion runtime_error.on
Exception in thread "main" java.lang.ArithmeticException: / by zero
    at RuntimeError.main(runtime_error.on:10)
```

## onionc との比較

| 機能 | onion | onionc |
|------|-------|--------|
| .class ファイルを作成 | しない | する |
| 実行 | 即座 | `java` コマンドが必要 |
| 用途 | スクリプト、テスト | 本番、ライブラリ |
| 速度 | 小さなプログラムで速い | 繰り返し実行に適している |
| 配布 | ソースが必要 | .class/.jar を配布可能 |

## スクリプト作成のベストプラクティス

### Shebang 行（Unix系システム）

スクリプトを実行可能にします。

**hello.on:**
```onion
#!/usr/bin/env onion
println("Hello from script!")
```

実行可能にして実行:
```bash
chmod +x hello.on
./hello.on
```

### エラーメッセージ

分かりやすいエラーメッセージを提供します。

```onion
class Script {
  public:
    static def main(args :String[]): void {
      if args.length < 2 {
        println("Error: Missing arguments")
        println("Usage: onion script.on <input> <output>")
        return
      }

      // 引数を処理...
    }
}
```

### 終了コード

適切な終了コードを返します。

```onion
class Script {
  public:
    static def main(args :String[]): void {
      if args.length == 0 {
        System::exit(1)  // エラー
      }

      // 成功
      System::exit(0)
    }
}
```

## 次のステップ

- [コンパイラ（onionc）](compiler.md) - クラスファイルにコンパイル
- [REPLシェル](repl.md) - 対話型プログラミング
- [基本例](../examples/basic.md) - スクリプトの例
