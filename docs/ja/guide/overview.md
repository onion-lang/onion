# 言語概要

Onion は、外部の雑なデータを検査済みで可逆な道具に変えるための静的型付き言語です。
JVM 上で動き、Java を直接呼べます。

Java・Kotlin・Scala を書いたことがあれば、このページの大半は見慣れたものに感じるはずです
——クラス、インターフェース、ジェネリクス、ラムダは期待どおりに動きます。そこは手早く
済ませて、違う部分に進めるように書いてあります。

## Onion は何のための言語か

多くの言語は、境界——ログの1行、JSON のボディ、コマンドライン引数——で `String` を渡してきて、
あとは自分でやってくれ、と言います。Onion はその境界を**一度だけ記述**することを求め、
読み取り・書き戻し・失敗の報告・CLI をその記述から導出します。

```onion
record Access(ip: String, method: String, path: String, status: Int)
  shape common = re"(\S+) (\w+) (\S+) (\d+)"

val each = file"access.log".eachLine(Access::common())
val rows = Outcome::values(each)      // 読めた行
val bad  = Outcome::defects(each)     // 読めなかった行、行番号つき
```

1000行のログに5行の壊れた行があれば、995行**と**読めなかった5行の両方が手に入ります。
たいていの道具は995行を返し、残り5行が存在したことすら伝えません。

この考え方は [Shape](shapes.md) で扱います。以降はその土台となる普通の言語機能です。

## 設計目標

1. **正直な境界** — 外部データの読み取り失敗は、どこで何が起きたかを持つ値になる
2. **静的型安全** — 簡潔さを保ったままコンパイル時にエラーを検出
3. **Javaとの相互運用** — ラッパー無しで任意の Java ライブラリを使える
4. **ビルド時に検査** — `law` / `example` 句はコンパイル中に実行される
5. **JVMのパフォーマンス** — 成熟したランタイム、別途VMの導入が不要

## 主な特徴

### 静的型付き

すべての変数と式にコンパイル時に型が決まります：

```onion
val name = "Alice"    // String と推論
val age: Int = 30
val scores = [95, 87, 91]   // List[Int] と推論される
```

型の種類：
- プリミティブ型：`Int`、`Long`、`Double`、`Float`、`Boolean`、`Byte`、`Short`、`Char`
- 参照型：クラス・インターフェース
- 配列型：`Type[]`
- Null型：`null` 値の特別な扱い
- ボトム型：値を返さない式のための `Nothing`

### オブジェクト指向

クラス、継承、インターフェースを完全サポートします：

```onion
class Animal {
  val name: String

  public:
    def this(n: String) {
      this.name = n
    }

    def speak: String = "Some sound"
}

class Dog extends Animal {
  public:
    def this(n: String): (n) { }

    def speak: String = "Woof!"
}
```

### 関数型の要素

ラムダ式とクロージャも使えます：

```onion
val double = (x: Int) -> x * 2

def makeCounter(): () -> Int {
  var count: Int = 0
  return () -> {
    count = count + 1
    return count;
  };
}
```

### JVMターゲット

Onion は JVM バイトコードに直接コンパイルされます：

- コンパイルされた `.class` ファイルは標準的な JVM クラスです
- Java のクラスと一緒に JAR にまとめられます
- JVM の性能特性を引き継ぎます
- Java エコシステム全体にアクセスできます

### Javaとの相互運用

Java へ直接、シームレスにアクセスできます：

```onion
import {
  java.util.ArrayList;
  java.util.HashMap;
  javax.swing.JFrame;
}

val list: ArrayList[String] = new ArrayList[String]()
val map: HashMap[String, String] = new HashMap[String, String]()
val window: JFrame = new JFrame("Title")
```

ポイント：
- `import { }` で Java のクラスをインポートする
- `new` で Java のオブジェクトを生成する
- Java のメソッドを普通に呼び出せる
- Java のインターフェースを実装できる
- Java のクラスを継承できる
- 静的メソッドへのアクセスには `::` を使う

## コンパイルモデル

```
ソースファイル (.on)
    ↓
[構文解析] JavaCC → 型なしAST
    ↓
[書き換え] 正規化
    ↓
[型検査] 型推論・検証 → 型付きAST
    ↓
[コード生成] ASM → .class ファイル
```

実行方法は3種類：
- `onionc` — `.class` ファイルにコンパイル
- `onion` — インメモリでコンパイルしてすぐ実行
- `Shell` — インタラクティブREPL

## 構文のハイライト

### `val` / `var` によるフィールド

フィールドは `val`（不変）または `var`（可変）で宣言し、`this.field` でアクセスします：

```onion
class Counter {
  var count: Int

  public:
    def increment {
      this.count = this.count + 1
    }
}
```

### `:` による型注釈

型はコロンの後に指定します。ローカル宣言は初期化子があれば型を省略できます：

```onion
val variable: Type = value
val inferred = value
def method(param: Type): ReturnType { }
```

### `::` による静的アクセス

静的メソッド・静的フィールドには `::` を使います：

```onion
println("Hello")
Math::random()
System::out.println("Java style")
```

デフォルトの静的インポートにより、一部のクラスメンバーは `::` なしで使えます（例えば
`onion.IO` の `println("Hello")`）。このデフォルトの集合は意図的に狭く、純粋なヘルパー群と
コンソール用の例外である `onion.IO` だけに絞られています——そのため副作用のある行は見た目にも
副作用があるとわかります。`Files::readText`、`Http::get`、`DateTime::now`、`System::exit` は
修飾して書くか、`import { onion.Files::* }`（クラス全体）や `import { java.lang.System::exit }`
（メンバー1つ）で明示的にインポートする必要があります。一覧は
`src/main/resources/onion/default-static-imports.txt` にあります。

### `as` による型キャスト

キャスト式には `as` 演算子を使います：

```onion
val x: Double = 3.14
val y: Int = (x as Int)  // Int にキャスト

val obj: Object = "string"
val str: String = (obj as String)  // String にキャスト
```

### `select` によるパターンマッチング

switch 風のパターンマッチングです：

```onion
select value {
  case 1, 2, 3:
    println("Small")
  case 4, 5, 6:
    println("Medium")
  else:
    println("Large")
}
```

## Javaとの主な違い

| 機能 | Java | Onion |
|------|------|-------|
| フィールド宣言 | `Type field` | `val/var field: Type` |
| 変数宣言 | `Type variable` | `val/var variable[: Type] = value` |
| 静的アクセス | `Class.method()` | `Class::method()` |
| 型キャスト | `(Type) value` | `value as Type` |
| パターンマッチング | `switch` | `select` |
| リスト追加 | `list.add(x)` | `list << x` |

## 現在の制限

README に記載のとおり:

1. **堅牢性** - コンパイラは、変異ファザー・クラッシュ再現コーパス・コード生成正当性テストによって「クラッシュしない／誤コンパイルしない」基準を維持しています。もしクラッシュや誤コンパイルに遭遇したら、最小の再現例を報告してください
2. **消去ジェネリクス** - reified 型情報はなし。型引数は不変（変性やワイルドカードはなし）
3. **末尾呼び出し最適化** - 直接自己再帰と相互自己再帰をカバー。一般的な継続渡しスタイルは対象外
4. **診断** - 一部のエラーは理想よりパイプラインの後半で報告される

`run/` ディレクトリの例は、コンパイルおよび実行が正しく動作することを確認済みです。

## 次のステップ

- [基本構文](basic-syntax.md) - 構文の基礎を学ぶ
- [クラスとオブジェクト](classes-and-objects.md) - オブジェクト指向プログラミング
- [Javaとの相互運用](java-interop.md) - Javaライブラリの活用
