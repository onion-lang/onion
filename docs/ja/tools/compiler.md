# コンパイラ（onionc）

`onionc` コマンドは、OnionのソースファイルをJVMの `.class` ファイルにコンパイルします。

## 使い方

```bash
onionc [オプション] ソースファイル...
```

## オプション

### `-classpath <classpath>`

コンパイル時のクラスパスを設定します。外部のJavaライブラリや、他のコンパイル済みOnionクラスを参照する場合に使います。

```bash
onionc -classpath lib/mylib.jar:lib/other.jar MyProgram.on
```

### `-encoding <encoding>`

ソースファイルの文字エンコーディングを指定します。未指定時はプラットフォーム依存のデフォルトが使われます。

```bash
onionc -encoding UTF-8 MyProgram.on
```

### `-d <出力ディレクトリ>`

生成されたクラスファイルの出力先を設定します。未指定時はカレントディレクトリに出力されます。

```bash
onionc -d build/classes MyProgram.on
```

クラスファイルはモジュール名に応じて整理されます。
- Unix系: `org/onion_lang/MyClass.class`
- Windows: `org\onion_lang\MyClass.class`

### `-maxErrorReports <count>`

報告するコンパイルエラーの最大数を制限します。大量のエラーがある大規模プロジェクトで便利です。

```bash
onionc -maxErrorReports 10 MyProgram.on
```

### `--dump-ast`

構文解析後のASTを標準エラー出力に出力します。構文やパースのデバッグに便利です。

```bash
onionc --dump-ast MyProgram.on
```

### `--dump-typed-ast`

型付きASTの概要（クラス、フィールド、メソッド）を標準エラー出力に出力します。

```bash
onionc --dump-typed-ast MyProgram.on
```

### `--profile-compile`

各フェーズの所要時間、ソース数、クラスパスサイズ、生成クラス数を含むコンパイルプロファイルを出力します。

```bash
onionc --profile-compile MyProgram.on
```

### `--profile-format <text|json>`

コンパイルプロファイルの出力形式を選択します。

```bash
onionc --profile-compile --profile-format json MyProgram.on
```

### `--profile-output <target>`

コンパイルプロファイルを `stderr`、`stdout`、またはファイルパスに出力します。

```bash
onionc --profile-compile --profile-format json \
       --profile-output target/profile.json \
       MyProgram.on
```

### `--warn <off|on|error>`

警告の扱いを制御します。`error` を指定すると警告をコンパイルエラーとして扱います。

```bash
onionc --warn error MyProgram.on
```

### `--Wno <codes>`

特定の警告カテゴリをコードまたは名前で抑制します。

```bash
onionc --Wno W0001,unused-parameter MyProgram.on
```

## 例

### 基本的なコンパイル

1つのファイルをコンパイルします。

```bash
onionc Hello.on
```

これにより、カレントディレクトリに `Hello.class` が作成されます。

### 複数ファイルのコンパイル

複数のソースファイルを同時にコンパイルします。

```bash
onionc Person.on Employee.on Manager.on
```

### 出力ディレクトリの指定

出力を整理します。

```bash
onionc -d out/classes src/Main.on src/Utils.on
```

クラスファイルは `out/classes/` に出力されます。

### クラスパス付きのコンパイル

外部ライブラリを参照します。

```bash
onionc -classpath lib/gson-2.8.jar:lib/commons-lang.jar \
       src/JsonParser.on
```

### 完全な例

```bash
onionc \
  -d build/classes \
  -classpath lib/external.jar \
  -encoding UTF-8 \
  -maxErrorReports 20 \
  src/*.on
```

## コンパイル後の実行

コンパイル後は、Javaで実行します。

```bash
# コンパイル
onionc -d build Main.on

# Javaで実行
java -cp build Main
```

またはJARで実行します。

```bash
# コンパイル
onionc -d build Main.on Helper.on

# JARの作成
jar cvfe program.jar Main -C build .

# JARの実行
java -jar program.jar
```

## モジュール構成

OnionはJavaと同様のモジュール名（パッケージ）を使用します。

**MyClass.on:**
```onion
module com.example.myapp

class MyClass {
  public:
    static def main(args :String[]): void {
      println("Hello")
    }
}
```

コンパイル:
```bash
onionc -d build MyClass.on
```

出力:
```
build/com/example/myapp/MyClass.class
```

実行:
```bash
java -cp build com.example.myapp.MyClass
```

## コンパイルエラー

### よくあるエラー

**型の不一致:**
```
Error: Type mismatch
  Expected: Int
  Found: String
  at MyProgram.on:10
```

**未定義の変数:**
```
Error: Undefined variable 'count'
  at MyProgram.on:15
```

**メソッドが見つからない:**
```
Error: Method 'getValue()' not found in class Person
  at MyProgram.on:23
```

## インクリメンタルコンパイル

`onionc` は指定したファイルを毎回すべてコンパイルします。大規模プロジェクトでは以下を検討してください。

1. 変更されたファイルのみをコンパイルする
2. ビルドツールを使う（Make、SBT、Gradleなど）
3. コードをモジュールに分割する

## ビルドツールとの統合

### Makefileの例

```makefile
SRC_DIR = src
OUT_DIR = build/classes
SOURCES = $(wildcard $(SRC_DIR)/*.on)

all: compile

compile:
	mkdir -p $(OUT_DIR)
	onionc -d $(OUT_DIR) $(SOURCES)

clean:
	rm -rf $(OUT_DIR)

run: compile
	java -cp $(OUT_DIR) Main
```

### シェルスクリプトの例

```bash
#!/bin/bash

SRC_DIR="src"
OUT_DIR="build/classes"
CLASSPATH="lib/*"

mkdir -p "$OUT_DIR"

echo "Compiling Onion sources..."
onionc -d "$OUT_DIR" -classpath "$CLASSPATH" "$SRC_DIR"/*.on

if [ $? -eq 0 ]; then
    echo "Compilation successful"
    echo "Running program..."
    java -cp "$OUT_DIR:$CLASSPATH" Main
else
    echo "Compilation failed"
    exit 1
fi
```

## コンパイラ出力

### 成功時のコンパイル

通常、成功時は何も出力されません。

```bash
$ onionc Hello.on
$ ls
Hello.class  Hello.on
```

### コンパイルエラー

エラーは標準エラー出力に書き出されます。

```bash
$ onionc BadProgram.on
Error: Type mismatch at BadProgram.on:5
Error: Undefined variable at BadProgram.on:10
Compilation failed with 2 errors
```

## 次のステップ

- [スクリプトランナー](script-runner.md) - Onionスクリプトを直接実行
- [REPLシェル](repl.md) - 対話型プログラミング
- [ソースからビルド](../contributing/building.md) - コンパイラをビルド
