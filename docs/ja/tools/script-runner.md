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

ソースファイルの文字エンコーディングを指定します。未指定時は UTF-8 です。

```bash
onion -encoding UTF-8 MyScript.on
```

### `-maxErrorReport <count>`

報告するコンパイルエラーの最大数を制限します。

```bash
onion -maxErrorReport 10 MyScript.on
```

### `-super <super class>`

トップレベルのスクリプトが生成するクラスの親クラスを指定します。明示的なクラス宣言が
ない場合にのみ意味を持ちます。

```bash
onion -super java.lang.Object MyScript.on
```

### `--verbose`

スクリプト実行前に、各コンパイルフェーズ（構文解析、書き換え、型検査、コード生成）の
所要時間を表示します。

```bash
onion --verbose MyScript.on
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

### `--no-check-laws`

レコードの `law` / `example` 句を実行しません。

```bash
onion --no-check-laws MyScript.on
```

### `--law-seed <n>` / `--law-samples <n>`

`law` のサンプル生成を制御します。反例が出たときは、それを生んだ設定がメッセージに出ます。

```bash
onion --law-samples 500 MyScript.on
```

### `--effects`

コンパイルされた各メソッドの推論済みエフェクト集合（`read write net exec env clock rand console unknown`。空はpureを意味する）を標準エラー出力に表示します。

```bash
onion --effects MyScript.on
```

### `--stacktrace`

未捕捉のランタイムエラーについて、整形された診断レポートの代わりに生のJVMスタックトレースを表示します。

```bash
onion --stacktrace MyScript.on
```

### `--watch`

スクリプトを実行した後、ファイルが変更されるたびに自動的に再実行します。コンパイルエラーやランタイム例外が発生してもウォッチループは止まりません。Ctrl-Cで停止します。

```bash
onion --watch MyScript.on
```

## 依存ライブラリ（`//> using dep`）

スクリプトは、必要な Maven ライブラリを `-classpath` で受け取る代わりに、自身の
ヘッダーで scala-cli と同じ構文を使って宣言できます。

```onion
//> using dep "org.apache.poi:poi-ooxml:5.5.1"

import { org.apache.poi.xssf.usermodel.XSSFWorkbook }

tool book(dst: String): Int requires { write(dst), unknown } {
  val wb = new XSSFWorkbook()
  wb.createSheet("report").createRow(0).createCell(0).setCellValue("hello")
  val bytes = new java.io.ByteArrayOutputStream()
  wb.write(bytes)
  wb.close()
  Files::writeBytes(dst, bytes.toByteArray())   // tool が宣言する `write(dst)`
  return 0
}
```

```bash
onion book.on report.xlsx --plan
```

Maven Central にないライブラリには、そのリポジトリも指定します。

```onion
//> using repository "https://nexus.example.com/repository/maven-public"   // 任意
//> using dep "com.example.internal:ledger:2.3.0"
```

- `using dep` には `"group:artifact:version"` 形式の座標を1つ以上、`using repository`
  には `http`・`https`・`file` の絶対URLを1つ以上書きます。値はクォートしてもしなくても
  よく、行末に `// コメント` を置けます。複数形の `deps`・`repositories` も使えます。
- リポジトリはプロジェクトの `[[repositories]]` と同じく、書いた順に Maven Central
  **より先に**検索されます。解決にはプロジェクトと同じ coursier のリゾルバーを使います。
- 解決された jar（推移的依存を含む）はコンパイル時と実行時の両方で classpath に載ります。
  `--effects`、ツールの `--plan`/`--help`、`--watch`、`ONION_DAEMON=1`（デーモンも同じ
  jar でコンパイルします）でも同様です。
- ディレクティブは**先頭のコメントブロック**、つまり任意の `#!` 行に続く空行とコメント
  だけから、コードより前でのみ読み取られます。そこにある `//>` 行は正しい形式の
  ディレクティブでなければならず、コードの後ろの `//> using` 行は無視されるコメントでは
  なくエラーになります。不正なディレクティブはスクリプト名・行・列を示し、コンパイル前に
  実行を止めます。
- バージョンは厳密指定が必要です。範囲（`[1.0,2.0)`）、`latest.*`、`LATEST`、`RELEASE`、
  `1.+` は拒否され、同じモジュールを2つのバージョンで宣言することもできません。これは
  `onion.toml` の `[dependencies]` と同じコードによる同じ規則で、プロジェクトが受け付ける
  座標はスクリプトでも受け付けます。Scala の `group::artifact` 形式には対応していないので、
  アーティファクト名を完全に書いてください。
- ダウンロードの進捗は標準エラー出力に表示されます。
- `onionc` も、コンパイルするファイルから同じディレクティブを、同じパーサーと
  キャッシュで読み取ります（[コンパイラ](compiler.md)を参照）。解決される classpath は
  `onionc --print-classpath` で表示できます。
- 言語サーバーも読み取ります。単独のスクリプトは、ディレクティブが解決する jar に対して
  検証されます（[言語サーバー](language-server.md)を参照）。

**スクリプトにはロックファイルがありません。** ディレクティブは直接の依存を固定しますが、
推移的依存のバージョンはその時点の解決結果次第なので、同じスクリプトでもマシンによって
異なる推移的 jar で動くことがあります。それが問題になるなら、`onion.toml` の
`[dependencies]` とコミットした `onion.lock` を持つ[プロジェクト](project-cli.md)に
してください。

coursier による解決は、すべてダウンロード済みでも1秒ほどかかります。そこで解決済みの
classpath をディレクティブの組ごと（ソートした依存と、書いた順のリポジトリのハッシュ）に
Onion のキャッシュディレクトリ配下の `script-deps/` にキャッシュし、記録した jar が
すべて存在する限り再利用します。キャッシュディレクトリは `$ONION_CACHE_DIR`
（または `-Donion.cache.dir`）が設定されていればそこ、なければ Windows では
`%LOCALAPPDATA%\onion\cache`、macOS では `~/Library/Caches/onion`、それ以外では
`$XDG_CACHE_HOME/onion`（`~/.cache/onion`）です。いつ削除しても安全で、次の実行で
改めて解決されます。

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
