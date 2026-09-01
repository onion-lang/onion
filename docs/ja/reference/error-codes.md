# Onion コンパイラ エラーコード

Onion は、安定したコードを持つコンパイルエラーを報告します。このページでは最も一般的なコードを説明し、修正方法を提案します。

## 内部エラー

### `I0000` — 内部コンパイラエラー

コンパイラフェーズから予期しない例外が発生しました。これはコンパイラのバグであり、プログラムのバグではありません。ソースを最小限の再現例に減らして、イシューを報告してください。

## 型エラー

### `E0000` — 互換性のない型

ある型の値が、別の型が期待される場所で使われました。

```onion
val x: Int = "hello"   // E0000: Int が期待される場所で String
```

よくある修正方法:
- 期待される型を確認し、値を明示的に変換します（例: `Int::parseInt(s)`）。
- nullable から non-null への不一致の場合、コンパイラは `!!`、`?:`、または null チェックを提案するヒントを追加します。

```onion
val name: String? = lookup()
val sure: String = name     // E0000 + nullable ヒント
val fixed: String = name ?: ""
```

### `E0001` — 互換性のない被演算子の型

演算子がサポートしていない型に適用されました。

```onion
val b = true + 1   // E0001
```

### `E0066` — raw なジェネリック型は使用できない

ジェネリック型が型引数なしで使われました。Onion は raw なジェネリック型を禁止して
います。型引数を明示的に指定してください。

```onion
val xs: List = [1]        // E0066: raw な List
val ok: List[Int] = [1]   // 修正版
```

### `E0067` — return の欠落

値を返すメソッドのブロック本体が、値を返さずに末尾へ到達する可能性があります。
すべてのパスで値を返すか、式本体を使ってください。

```onion
class C {
public:
  static def f(): Int { 5 + 10 }   // E0067: 値が返されない
}
```

修正方法:
- 明示的な `return` を追加: `{ return 5 + 10 }`。
- 式本体を使う: `def f(): Int = 5 + 10`。

### `E0027` — boxing できない型

`void` 型の式（`void` メソッドの呼び出し結果）が、値が必要な場所（文字列連結の
被演算子など）で使われました。`void` には box 化した形が存在しないため、box する
対象がありません。

```onion
class Test {
public:
  static def main(args: String[]): Int {
    val s = "a" + IO::println("b")   // E0027: IO::println は void を返す
    return 0
  }
}
```

修正方法: `void` を返す呼び出しの結果を値として使わず、単独の文として呼び出して
ください。

### `E0028` — lvalue が必要

代入式の左辺が代入可能な場所（ローカル変数・フィールド・配列要素）ではありません
（メソッド呼び出しの結果やリテラルなど）。

```onion
class Test {
public:
  static def main(args: String[]): Int {
    "a".length() = 3   // E0028: 左辺が代入可能な場所ではない
    return 0
  }
}
```

修正方法: 変数・フィールド・配列要素に代入してください。

### `E0036` — val に代入できない

`val` で宣言された束縛 ― ローカル変数、またはコンストラクタの外から書き込まれた
インスタンスフィールド ― に、初回の代入のあとで再代入（あるいはインクリメント・
デクリメント）が行われました。

```onion
class Test {
public:
  static def main(args: String[]): Int {
    val x: Int = 1
    x = 2   // E0036: x は val であり var ではない
    return x
  }
}
```

修正方法: 値を変更する必要があるなら `var` として宣言してください。インスタンスの
`val` フィールドはコンストラクタ内で一度だけ代入するようにしてください。

### `E0030` — 型はジェネリックではない

型引数を取らない型に対して型引数が渡されました。

```onion
class Test {
public:
  static def main(args: String[]): Int {
    val x: String[Int] = null   // E0030: String は型引数を取らない
    return 0
  }
}
```

修正方法: 型引数を外してください（`String[Int]` ではなく `String`）。

### `E0031` — 型引数の個数が一致しない

ジェネリック型に渡された型引数の個数が誤っています。

```onion
class Box[T] { public: def this {} }
class Test {
public:
  static def main(args: String[]): Int {
    val b = new Box[String, String]()   // E0031: Box は1個であり2個ではない
    return 0
  }
}
```

このエラーは、`val b: Box[String, String]? = null` のように nullable な型注釈の
中に個数不一致が書かれた場合にも発生します。

### `E0032` — 型引数は参照型でなければならない

型パラメータに `void` を代入することを防ぐチェックです。型チェッカ内には実際に
報告箇所がありますが、現時点の Onion の正しい構文からは到達できません。文法上、
型引数や型エイリアスの代入先など `type()` が使われる位置は `void_type()` を経由
せず（メソッドの `return_type()` だけが経由します）、そのため `void`/`Unit` を型
引数として書くこと自体ができません。`new Box[void]()`・`Box[void]`・
`type T = void` はいずれも E0032 ではなく構文エラーになります。この状態をあえて
実例で無理に到達させず未到達のままにしている理由は `SemanticErrorCodeCoverageSpec`
を参照してください。

### `E0033` — メソッドはジェネリックではない

型引数を取らないメソッドの呼び出しに型引数が渡されました。

```onion
class Test {
public:
  static def main(args: String[]): Int { return "abc".length[String]() }   // E0033
}
```

修正方法: 呼び出しから型引数を外してください（`"abc".length()`）。

### `E0034` — メソッド型引数の個数が一致しない

ジェネリックメソッド呼び出しに渡された明示的な型引数の個数が誤っています。

```onion
class Test {
public:
  static def main(args: String[]): Int {
    val l = java.util.Collections::emptyList[String, String]()   // E0034: 1個であり2個ではない
    return 0
  }
}
```

### `E0035` — erasure 後の JVM シグネチャが衝突している

Onion の型システムでは異なる2つのオーバーロードが、同じ JVM メソッド記述子に
erasure されてしまう場合があります（ジェネリクスは erasure ベースであり、
`List[String]` と `List[Integer]` はバイトコードレベルでは区別できません）。
その結果、クラスファイルは両方を保持できません。

```onion
class C {
public:
  def this {}
  def f(x: java.util.List[String]): Int = 1
  def f(x: java.util.List[Integer]): Int = 2   // E0035: どちらも f(List)I に erasure される
}
```

修正方法: オーバーロードの erasure 後シグネチャを変える（引数の個数を変える、
同じクラスに erasure されない引数型にするなど）か、内部で分岐する1つの
メソッドに統合してください。

## 名前解決エラー

### `E0002` — 変数が見つからない

参照されたローカル変数がスコープ内に存在しません。可能な場合、コンパイラは類似した名前を提案します。

```onion
val userName = "ok"
println(usrName)   // E0002、userName を提案
```

### `E0003` — クラスが見つからない

型名を解決できません。スペルとインポートを確認してください。

```onion
val xs = new ArrayLst[String]()   // E0003、ArrayList を提案
```

### `E0004` — フィールドが見つからない

対象の型にその名前のフィールドが存在しません。スペルとフィールドの可視性を確認してください。

```onion
class Test {
public:
  static def main(args: String[]): Int {
    val s = "a".noSuchField   // E0004
    return 0
  }
}
```

### `E0005` — メソッドが見つからない

呼び出しに一致するメソッドがありません。同じ名前のメソッドが存在するが引数の型が異なる場合、コンパイラは利用可能なシグネチャを一覧表示します。

### `E0006` — メソッド呼び出しが曖昧

2つのオーバーロードが呼び出しに対して同等に適用可能で、どちらがより特定的かを
決められません。しばしば、引数の静的型（`null` や共通のスーパータイプなど）が
複数の無関係なパラメータ型に同時に一致することが原因です。

```onion
class A {}
class B {}
class Test {
public:
  static def foo(x: A): Int = 1
  static def foo(x: B): Int = 2
  static def main(args: String[]): Int {
    foo(null)   // E0006: foo(A) と foo(B) の両方に適用可能
    return 0
  }
}
```

対処: 引数をキャストしてオーバーロードを確定させるか（`foo(null as A)`）、
どちらか一方しか一致しないようにオーバーロードをリネーム・統合してください。

### `E0011` — トップレベルのグローバル変数定義の重複

同じ名前のトップレベル `var`/`val` が2回宣言されています。修飾子付き（`static var x = ...`
を2回）でも、修飾子なし（ベアなトップレベル `var`/`val` はスクリプトの合成クラスの
`public static` フィールドに昇格されるため、修飾子付きの形と同様にグローバルです）でも
該当します。

```onion
static var x: Int = 1
static var x: Int = 2   // E0011: x は既に定義されている
```

### `E0012` — トップレベル関数定義の重複

2つのトップレベル `def` 関数が同じ名前・同じ引数型を持っています。引数型が異なる
オーバーロードは引き続き許可されます。

```onion
def foo(x: Int): Int { return x }
def foo(x: Int): Int { return x + 1 }   // E0012: foo(Int) は既に定義されている

def bar(x: Int): Int { return x }
def bar(x: String): String { return x }   // OK: 引数型が異なる
```

### `E0013` — メソッドにアクセスできない

`private`（またはアクセスが不十分な）メソッド・コンストラクタ・静的メソッドが、
それを参照できないクラスの外部から呼び出されています。

```onion
class C {
private:
  static def s(): Int = 1
}
def main(args: String[]): void { IO::println(C::s()) }   // E0013
```

### `E0014` — フィールドにアクセスできない

非公開フィールドがその宣言クラスの外部から読み書きされています。

```onion
class Plain {
  var value: String
public:
  def this(v: String) { value = v }
}
val p = new Plain("orig")
p.value = "changed"   // E0014: value は public ではない
```

### `E0015` — クラスにアクセスできない

静的型が別モジュールで宣言された `internal` クラスである値に対して、メンバーが
選択されています。可視性はモジュール単位（クラス名の最後のドットより前の接頭辞）
で決まり、`internal` クラスはそれを宣言したモジュール内からのみアクセスできます。

```onion
module pkg.a
internal class Hidden {
public:
  var n: Int
  def this { n = 1 }
}
```

```onion
module pkg.b
import { pkg.a.Hidden }
class UseIt {
public:
  static def main(args: String[]): Int {
    val h: Hidden = null
    return h.n   // E0015: Hidden は pkg.a に internal
  }
}
```

### `E0016` — 継承関係が循環している

クラスまたはインタフェースのスーパータイプ連鎖（`extends`/`conforms`）が、直接
または中間の型を経由して自分自身に戻ってしまっています。

```onion
class A extends B {
}
class B extends A {   // E0016: A -> B -> A の循環
}
```

対処: 循環を断ち切ります。どちらか一方が他方を継承しないようにしてください。

### `E0018` — 不正な継承

クラスまたはインタフェースが、使用できないスーパータイプを `extends`/`conforms`
しています。スーパータイプが `final` であるか、インタフェースの位置にクラスが
（またはその逆が）指定されています。

```onion
class A extends java.lang.String { public: def this {} }   // E0018: String は final
```

対処: `final` でないクラスを継承するか、`conforms` はインタフェースにのみ、
`extends` はクラスにのみ使用してください。

### `E0019` — 不正なメソッド呼び出し

`static` メソッドが、クラス自体ではなくインスタンスレシーバー（`obj.m(...)` や
`obj?.m(...)`）経由で呼び出されました。

```onion
class A {
public:
  def this {}
  static def s(): Int = 1
}
class Test {
public:
  static def main(args: String[]): Int { return new A().s() }   // E0019
}
```

対処: クラス経由で呼び出してください（`A::s()`）。

### `E0071` — インスタンスに対する静的呼び出し

`s::m()` が使われましたが、`s` は型ではなくローカル変数に解決されました —
Java/Kotlin の癖でインスタンスに `::` を使ってしまうケースです。汎用的な
「型が見つからない」エラーではなく、修正方法を直接示します。

```onion
class Test {
public:
  static def main(args: String[]): void {
    val s: String = "hi"
    IO::println(s::length())   // E0071: s は変数であり、型ではない
  }
}
```

対処: インスタンスメソッドの呼び出しには `.` を使ってください（`s.length()`）。
`::` は型の静的メンバー専用です。ローカル変数と同名の実在する型に対しては
正しく解決されます — このエラーは `::` が変数に対して使われた場合にのみ発生します。

### `E0091` — 値として使われたクラス

`E0071` の鏡像です。ローカル変数でもフィールドでもない、実在するクラスに
解決される大文字始まりのベアな名前が、値が期待される位置（典型的には
`.member` の対象）で使われました。汎用的な「ローカル変数が見つからない」
エラーではなく、`::` を直接示します。

```onion
static def main(args: String[]): void {
  IO::println(System.currentTimeMillis())   // E0091: System は変数ではなくクラス
}
```

対処: 静的メンバーには `::` を使ってください（`System::currentTimeMillis()`）。
`.` はインスタンスのメンバー専用です。クラスと同名の実在するローカル変数に
対しては正しく解決されます — このエラーは名前が変数にもフィールドにも
解決されない場合にのみ発生します。

### `E0020` — 値を返すことができない

宣言された戻り値の型が `void` ではないメソッド内でベアな `return;` が使われて
いるか、値が必要な位置で `return` が `void` 型の式を返しています。

```onion
class Test {
public:
  static def f(): Int { return }   // E0020: f は Int を返す必要がある
  static def main(args: String[]): Int { return 0 }
}
```

対処: 宣言された型の値を返す（`return 0`）か、メソッドの戻り値の型を `void`
に変更してください。

### `E0021` — コンストラクタが見つからない

引数に一致するコンストラクタがありません。コンパイラは利用可能なコンストラクタを一覧表示します。

### `E0022` — コンストラクタ呼び出しが曖昧

`new` 呼び出しに対して2つのコンストラクタオーバーロードが同等に適合します。
多くの場合、引数の静的型（`null` など）が複数の無関係な仮引数型に同時に
適合することが原因です。

```onion
class C {
public:
  def this(a: String) {}
  def this(b: StringBuilder) {}
}
class Test {
public:
  static def main(args: String[]): Int { val c = new C(null); return 0 }   // E0022
}
```

対処: 引数をキャストしてオーバーロードを確定させる（`new C(null as String)`）か、
候補が1つだけになるようコンストラクタの一方を改名・統合してください。

### `E0023` — インタフェースが要求されている

インタフェース型を要求する位置（`forward` で委譲するフィールドの宣言型、または
`{ ... }` クロージャリテラルの対象型）に、インタフェースではない型（クラスなど）
が指定されました。

```onion
class A {
  forward val x: String   // E0023: String はクラスであり、インタフェースではない
public:
  def this { x = "a" }
}
```

対処: フィールド／対象をインタフェース型で宣言するか、委譲が本当に不要であれば
`forward` を取り除いてください。

### `E0037` — 抽象メソッドが実装されていない

クラスが `conforms` したインタフェース（または `extends` した抽象クラス）が宣言する
抽象メソッドの実装を提供しておらず、かつそのクラス自身も `abstract` として宣言され
ていません。

```onion
interface I { def m(): Int }
class A conforms I {   // E0037: A は m() を実装するか abstract 宣言が必要
public:
  def this {}
}
```

対処: 不足しているメソッドを実装するか、クラスを `abstract` として宣言してください。

### `E0038` — 抽象クラスをインスタンス化できない

`new` 式が `abstract` として宣言されたクラスを対象にしています。そのクラス自身が
抽象メソッドを宣言しているかどうかは問いません。

```onion
abstract class Shape {
public:
  abstract def area(): Int
}
class Test {
public:
  static def main(args: String[]): Int {
    val s: Shape = new Shape();   // E0038: Shape は abstract
    return 0
  }
}
```

対処: 代わりに具象サブクラスをインスタンス化してください。

### `E0039` — final メソッドをオーバーライドできない

`override` を付けたメソッドが、スーパークラスで `final` として宣言されたメソッドを
対象にしています。

```onion
class A {
public:
  def this {}
  final def m(): Int = 1
}
class B extends A {
public:
  def this {}
  override def m(): Int = 2   // E0039: A.m は final
}
```

対処: `override` を取り除くか、オーバーライドが本当に必要であればスーパークラス側の
`final` を外してください。

### `E0068` — オーバーライド対象が見つからない

`override` を付けたメソッドが、実際には何もオーバーライドしていません —
そのスーパータイプ連鎖（基底クラスまたはインタフェース）に同名（同シグネチャ）の
メソッドが存在しません。

```onion
class Base {
public:
  def helper(): Int { return 1; }
}
class Sub extends Base {
public:
  override def notInBase(): Int { return 2; }   // E0068: Base にそのようなメソッドはない
}
```

対処: オーバーライド対象のメソッド名・シグネチャを一致させるか、新規メソッドであれば
`override` を外してください。

### `E0072` — 本体を持つ abstract メソッド

`abstract` として明示的に宣言されたメソッドが本体を持っています。本体はコード生成時に
黙って破棄されてしまうため — `abstract` と本体は矛盾する組み合わせなので、何も言わずに
無視するメソッドとしてコンパイルするのではなく、エラーとして拒否します。

```onion
abstract class B {
public:
  abstract def foo(): Int { return 99 }   // E0072: abstract メソッドは本体を持てない
}
```

対処: 本体を残すなら `abstract` を外して具象メソッドにするか、abstract のままにするなら
本体を削除してください。インタフェースのデフォルトメソッド（`abstract` キーワードのない
本体）はこのエラーの対象外です。

### `E0040` — プリミティブ型に対するメソッド呼び出し

メソッド呼び出しの対象式が `void` 型を持っています。典型的には、何も返さない
呼び出しの結果に対して直接メソッドを連鎖させた場合に発生します。

```onion
class Test {
public:
  static def main(args: String[]): Int {
    IO::println("a").toString()   // E0040: println は void を返す
    return 0
  }
}
```

対処: 2つの呼び出しを別々の文に分けてください。

### `E0041` — 不正なメソッド呼び出しターゲット

メソッド呼び出し（または `[...]` によるインデックスアクセス）の対象式の型が、
呼び出し可能なレシーバーとして使えません。たとえば `null` 除去前の nullable な
クラス型の値（`T?`）や、具体的な上限を持たないワイルドカード型などです。

```onion
class Box {
public:
  def this {}
}
class Test {
public:
  static def main(args: String[]): Int {
    val b: Box? = null;
    val v = b[0];   // E0041: Box? はメソッド呼び出しのターゲットとして不正
    return 0
  }
}
```

対処: まず `null` を除外してください（`if b != null { ... }`、`b ?: default`、
`b?.method()` / `b?[...]` など）。レシーバーが確定した非 null 型を持つようにします。

### `E0061` — `from re"..."` がサポートしないレコード成分型

`record ... from re"..."` 句は、各正規表現キャプチャグループをレコード成分の型に
変換することで `parse`/`parseAll` を導出します。この方法で生成できるのは
`String`、`Int`、`Long`、`Double`、`Float`、`Boolean`、`Short`、`Byte` の成分だけです。

```onion
record Inner(x: Int)
record R(a: String, b: Inner) from re"(\S+) (\S+)"   // E0061: Inner はサポートされていない成分型
```

対処: すべての成分をサポートされているスカラー型にとどめるか、通常の `from re"..."`
マッチのあとで該当フィールドを手動でパースしてください。

### `E0062` — `derive!` がサポートしないレコード成分型

`derive!(Json)` と `derive!(Yaml)` はどちらも、各成分をスカラー値にマッピングする
ことで `fromX`/`toX` の対を生成します。両者は同じ `toMap`/`fromMap` コアを共有して
いるため、この制約はどちらでも同一です。サポートされる成分型は `from re"..."` と
同じ、`String`、`Int`、`Long`、`Double`、`Float`、`Boolean`、`Short`、`Byte` です。

```onion
record Inner(z: Int)
record Bad(a: String, b: Inner) derive!(Json)   // E0062: Inner はシリアライズできない
```

対処: すべての成分をサポートされているスカラー型にとどめてください。

### `E0063` — 未知の `derive!` マーカー

`derive!(...)` が、コンパイラが実装していないフォーマット名を指定しています。
サポートされているマーカーは `Json` と `Yaml` で、単独でも
`derive!(Json, Yaml)` のように併用してもかまいません。

```onion
record U(a: String) derive!(Bogus)   // E0063: 未知の derive! マーカー Bogus
```

対処: `derive!(Json)`、`derive!(Yaml)`、`derive!(Json, Yaml)` のいずれかを使うか、
この句を削除してください。

### `E0086` — レコード成分名の重複

同じレコード内の2つの成分が同じ名前を宣言しています。成分名はそれぞれ private
フィールドと public アクセサメソッドを1つずつ生成するため、名前が重複すると
それ自身と衝突します。以前はこれを検査しておらず、生成されたクラスが実際に
ロードされたときに初めて（`ClassFormatError` が内部エラー I0000 として現れる
形で）失敗していました。

```onion
record R(a: Int, a: Int)   // E0086: レコード R の成分 a が重複しています
```

対処: どちらかの成分名を変更してください。

### `E0064` — law の反証

`law name(p: T) { boolExpr }` 句は、ビルド時に `p` の生成されたサンプル値に対して
チェックされます。式を false にするサンプルは反例であり、再現できるように
（シードやサンプル数などの）生成設定とともに報告されます。

```onion
record Pt(x: Int, y: Int)
  law wrong(p: Pt) { p.x() == p.y() }   // E0064: 反証された（例: Pt(0, 1)）
```

対処: あらゆる生成サンプルに対して成り立つように law（またはチェック対象のコード）を
修正するか、一般には成り立たない law であれば削除してください。

### `E0065` — example の失敗

トップレベルまたはレコードに付随する `example { boolExpr }` 句が、ビルド時に
`false` と評価された（または例外をスローした）ケースです。`law` と異なり、
`example` は生成されたサンプル群ではなく、1つの固定ケースをチェックします。

```onion
record R(x: Int)
  example { new R(1).x() == 2 }   // E0065: false と評価された
```

対処: example が期待する値を修正するか、その example が検証しているコード側を
修正してください。

### `E0073` — Map は直接反復できない

`foreach` は配列か `iterator()` を持つ値を走査しますが、`Map` はそのどちらでもありません。

```onion
val m = ["a": 1]
foreach k: String in m { println(k) }   // E0073
```

エントリを回す分割代入形式を使うか、ビューを選んでください。

```onion
foreach (k, v) in m { println(k + "=" + v) }
foreach k: String in m.keySet() { println(k) }
```

### `E0074` — law の引数型からサンプルを生成できない

`law` はビルド時に生成したサンプル値で検査されるため、すべての引数型に生成器が必要です。
生成できる型は `String`, `Int`, `Long`, `Double`, `Float`, `Boolean`, `Short`, `Byte` と、
全成分が生成可能なレコードです。

```onion
record Dummy(v: Int)
  law overArray(xs: Int[]) { xs != null }   // E0074: Int[] の生成器がない
```

それ以外——配列、`Map`、enum、インターフェース、コンストラクタが複数あるクラス——には
生成器がありません。以前はこうした law が黙って読み飛ばされ、**成立した law と区別が
つきませんでした**。実行できない検査が「通った検査」に見えてはいけません。
引数の型を変えるか、law を削除してください。

```onion
record Pt(x: Int, y: Int)
  law reflexive(p: Pt) { p == p }          // OK: flat なレコードは生成可能
```

### `E0075` — law を持つクラスをロードできなかった

law はコンパイル済みクラスに対して実行されます。`law` / `example` 句を持つクラスを
ロードできなかった場合、その検査は1つも実行されていません。`E0074` と同じ理由で、
黙って見逃さずに報告します。

### `E0076` — 未知の shape フォーマット

`shape name = <format>` 句が、コンパイラの知らない文書フォーマットを指しています。
推測すると「何も読まない shape」が黙って出来上がるため、サポート対象を列挙した
エラーにしています。

```onion
record Pt(x: Int, y: Int)
  shape doc = toml     // E0076: サポートしているのは json, yaml, config
```

インラインのパターンを書く場合は `shape name = re"..."` としてください。

### `E0080` — 自作の `Shape` に law がない

`onion.Shape[T]` を（`shape`/`from re"..."` による導出ではなく）直接実装した具象クラスは、
そのファイル内で機械検査される `law` か `example` を最低1つ主張しなければなりません。
導出された shape は round-trip が構造的に保証されますが、手書きのものは**主張するだけ**
なので、その主張を検査する必要があります。

```onion
class FixedWidth conforms Shape[Person] {
public:
  def this {}
  def parse(text: String, origin: Origin): Outcome[Person] { ... }
  def canPrint(): Boolean = true
  def print(v: Person): String { ... }
  def describe(): String = "fixed-width Person"
}
// E0080: FixedWidth の law を主張している箇所がこのファイルにない
```

対処: トップレベルの `example`（またはクラス自身の `law`）を追加してください——代表的な
主張は round-trip `s.parse(s.print(v)).get() == v` です。

```onion
example fixedWidthL1 {
  val s = new FixedWidth()
  val v = new Person("KOTA", 42)
  s.parse(s.print(v)).get() == v
}
```

パース専用の shape（`canPrint(): false`）は、読み取り方向にある law を代わりに主張します。
主張が偽であれば、他の `example` と同様に `E0065` になります。

## Capability エラー

`tool` 宣言は `requires { ... }` 節で境界を引きます。コンパイラは本体全体の効果を
（通常の関数を通じても推移的に）推論し、宣言をそれと突き合わせて検査します。

### `E0077` — tool が未宣言の効果を実行している

本体が `requires` 節でカバーされていない効果を実行しています。診断は不足している
効果、呼び出し先、呼び出し箇所を示します。

```onion
tool sneaky(src: String, dst: String): void
  requires { read(src) }
{
  Files::writeText(dst, Files::readText(src))   // E0077: `write` を実行 (writeText)
}
```

修正方法: 不足している capability を追加する — `requires { read(src), write(dst) }`。

### `E0078` — 本体が実行できない capability が宣言されている

`requires` 内の capability が未使用です: 本体のどこもそれを実行していません。誠実な
境界は、実際に起きることだけを列挙します。

```onion
tool overclaim(src: String): String
  requires { read(src), net }   // E0078: `net` が未使用
{
  return Files::readText(src)
}
```

修正方法: 未使用の capability を削除する。

### `E0079` — 不正な capability エントリ

`requires` のエントリが有効な capability ではありません: 未知の効果名、常に ambient な
効果へのパラメータ引数、またはこの tool のパラメータ名を指していない引数です。

```onion
tool t(): Int requires { teleport } { return 0 }
  // E0079: `teleport` は効果ではない

tool t2(x: String): Int requires { console(x) } { IO::println(x); return 0 }
  // E0079: `console` はパラメータ引数を取らない

tool t3(src: String): String requires { read(nope) } { return Files::readText(src) }
  // E0079: `nope` はパラメータ名ではない
```

効果の語彙は `read write net exec env clock rand console unknown` で、パラメータ引数
を取るのは `read`, `write`, `net`, `exec` のみです。

## Null 安全エラー

### `E0057` — 型引数が null の可能性がある

裸の型引数 `[T]` は nullable な型で実体化される可能性があるため、null チェックまたは非 null 制限なしには直接デリファレンスできません。

```onion
class Box[T] {
public:
  def size(x: T): Int = x.toString().length()   // E0057
}
```

修正方法:
- `?.` / `?:` / `if x != null` を使う。
- 非 null 制限を宣言: `class Box[T extends Object]`。

### `E0070` — nullable なメンバへのアクセス

nullable な型（`T?`）の値に対して、フィールド（メソッドではない）が直接アクセス
されました。この時点で値は null である可能性があります。

```onion
class Test {
public:
  static def main(args: String[]): Int {
    val x: String? = "abc"
    return x.length   // E0070: x は null の可能性がある
  }
}
```

対処: 安全にアクセスするには `?.`、デフォルト値を与えるには `?:`、非 null を
断言するには `!!`、または先に null チェック（`if x != null { ... }`）を行って
ください。

### `E0081` — tool のパラメータがコマンドラインから読めない

CLI は `tool` のパラメータから導出されるので、パラメータはすべてコマンドライン引数から
読める型でなければなりません。

```onion
record P(x: Int)

tool takeRec(p: P): Int requires { console } { IO::println("" + p.x()); return 0 }
// E0081: パラメータ `p` の型 P は引数から読み取れません。
//        使える型: String, Int, Long, Double, Float, Boolean, Short, Byte。
```

以前はこれが無言でした。CLI 合成がそのスクリプトを黙って飛ばし、`main` のない何も実行
しないプログラムがコンパイルされていました（issue #424）。

### `E0082` — tool 名の重複

2つの `tool` が同じ名前を持っています。関数のオーバーロード自体は正当ですが、コマンド
ラインは**名前だけ**で tool を選ぶため、2つ目は決して呼び出せず、`--contract` にも同名
で2件並んでどちらも指定できません。

```onion
tool same(a: Int): Int requires { console } { IO::println("A"); return 0 }
tool same(b: String): Int requires { console } { IO::println("B"); return 0 }
// E0082: tool 名 `same` が重複しています。
```

## try/catch エラー

### `E0083` — 到達不能な catch 節

後ろにある `catch` 節の例外型が、手前の節の型のサブタイプ（または同じ型）である場合、
その節には決して到達できません。手前の節が先に処理してしまうためです。

```onion
try {
  risky()
} catch e: RuntimeException {
  handle(e)
} catch e: IllegalArgumentException {
  // E0083: IllegalArgumentException は RuntimeException のサブタイプで、
  //        すでに手前の節が処理してしまう
}
```

より具体的な型を先に書くよう並べ替えるか、到達不能な節を削除してください。1つの
multi-catch 節（`catch e: A | B`）の候補同士は互いにチェックされません。

## extension メソッドエラー

### `E0084` — extension メソッドの重複

同じ `extension` ブロック内に、同じ名前・同じパラメータ型を持つメソッドが2つ宣言され
ています。チェックせずに放置すると、生成されるコンテナクラスが同一の JVM シグネチャを
持つメソッドを2つ持つことになり、コンパイル時ではなくクラスをロードした瞬間に初めて
（`ClassFormatError` が内部エラー I0000 として現れる形で）失敗します。

```onion
extension Double {
  def pct(): String = "" + self
  def pct(): String = "" + self   // E0084: duplicated extension method pct() on Double
}
```

対処: 重複を削除するかリネームしてください。名前は同じでもパラメータ型が異なる
extension メソッド同士は影響を受けません — 通常のオーバーロードです。

## 宣言エラー

### `E0007` — ローカル変数の重複

同じスコープ内で、同じ名前のローカル変数が2回宣言されています。

```onion
class Test {
public:
  static def main(args: String[]): Int {
    val x = 1
    val x = 2   // E0007: x はこのスコープで既に宣言済み
    return 0
  }
}
```

対処: どちらか一方の変数をリネームするか、冗長な宣言を削除してください。

### `E0008` — クラスの重複

トップレベルのクラス（インターフェース・レコード・enum を含む）が同じ名前で
2つ宣言されています。

```onion
class A { public: def this {} }
class A { public: def this {} }   // E0008: A は既に宣言済み
```

対処: どちらか一方の宣言をリネームまたは削除してください。

### `E0009` — フィールドの重複

同じフィールド名がクラス上で2回宣言されています。

```onion
class A {
  var x: Int
  var x: Int   // E0009: x は A 上に既に宣言済み
public:
  def this {}
}
```

対処: 重複したフィールドを削除するか、別の名前を付けてください。

### `E0010` — メソッドの重複

同じクラス上で、同じ名前・同じパラメータリストを持つメソッドが2つ宣言されて
おり、どちらも他方の正当なオーバーロードになっていません。

```onion
class Test {
public:
  def m(): Int = 1
  def m(): Int = 2   // E0010: m() は Test 上に既に宣言済み
  static def main(args: String[]): Int { return 0 }
}
```

対処: どちらか一方のメソッドをリネームするか、パラメータ型を変えて正当な
オーバーロードにしてください。

### `E0025` — コンストラクタの重複

同じクラスの2つのコンストラクタがまったく同じパラメータ型を持っており、
どちらも他方の正当なオーバーロードになっていません。

```onion
class A {
public:
  def this(x: Int) {}
  def this(y: Int) {}   // E0025: this(Int) は A に既に定義されている
}
```

対処: 一方のコンストラクタのパラメータ型を変えるか、重複を削除してください。

### `E0026` — 生成されたメソッドの重複

record の `law`/`example` 節は、節ごとに検査用メソッドを1つ合成し、その節の名前を
付けます。同じ名前・同じパラメータ型を持つ2つの `law`（または2つの `example`）は
同じメソッドへとマングルされ、コード生成時に衝突します。このチェックがなければ、
この衝突は通常の診断ではなく、生の JVM `ClassFormatError` として後になって表面化
していました。

```onion
record Point(x: Int, y: Int) from re"(-?\d+),(-?\d+)"
  law roundtrip(p: Point) { Point::parse(Point::format(p)) == p }
  law roundtrip(p: Point) { Point::parse(Point::format(p)) == p }   // E0026
```

対処: 一方の節の名前を変更するか、重複を削除してください。

### `E0029` — 型パラメータの重複

同じ型パラメータ名が、1つのクラス・インタフェース・メソッドの型パラメータリスト内で
2回出現しています。

```onion
class Box[T, T] { public: def this {} }   // E0029: T は既に宣言されている
```

対処: 型パラメータに異なる名前を付けてください。

### `E0051` — 戻り値の型が必要

トップレベル関数または extension メソッドに戻り値の型が宣言されておらず、
コンパイラがそれを推論できません。典型的には、その関数が自己再帰的で、
型付け済みの本体から型を推論する手がかりがない場合に発生します。

```onion
def f(n: Int) = f(n)   // E0051: f には明示的な戻り値の型が必要
def main(): void { }
```

対処: 関数に明示的な戻り値の型を与えてください（例: `def f(n: Int): Int = f(n)`）。

### `E0052` — ラムダのパラメータに型指定が必要

ラムダのパラメータに型注釈がなく、そのラムダが現れている文脈からは
コンパイラがパラメータの型を推論するための関数型インタフェースが得られません。

```onion
val f = (x) -> x + 1   // E0052: x の型をここでは推論できない
def main(): void { }
```

対処: パラメータに明示的に型を注釈する（`(x: Int) -> x + 1`）か、目的の型が
わかる文脈でラムダを使ってください — 例えば、関数型のパラメータが宣言された
メソッドへ直接引数として渡す、など。

### `E0053` — 循環した型エイリアス

2つ以上の `type` エイリアスが互いを参照しており、どれを解決しようとしても
無限に再帰してしまいます。

```onion
type A = B
type B = A   // E0053: A -> B -> A
```

対処: 少なくとも一方のエイリアスを具体的な型に向けることで循環を断ち切って
ください。

### `E0054` — 型エイリアスの重複

同じ `type` エイリアス名が同じスコープで2回宣言されています。

```onion
type A = java.lang.String
type A = java.lang.Integer   // E0054: A は既に宣言されている
```

対処: 一方の宣言をリネームするか削除してください。

### `E0055` — 関数には本体が必要

トップレベル関数が本体なし（`{ ... }` も `= expr` もなし）で宣言されています。
クラスのメソッドと異なり、トップレベル関数は決して abstract にはなれないため、
常に実装を持つ必要があります。

```onion
def f(): Int;   // E0055: f には本体が必要
def main(): void { }
```

対処: 関数に本体を与えてください。

### `E0085` — 本体のない static メソッド

`static` と宣言されたメソッドに本体（`{ ... }` も `= expr` も）がありません。文法上
本体のないメソッドは abstract/interface 的な宣言として受理されますが、`static` と
abstract は矛盾します — static メソッドはオーバーライドできないため、abstract にも
なり得ません。もしクラスファイルを生成すれば `ACC_STATIC` と `ACC_ABSTRACT` の両方が
必要になり、これは JVM が拒否します。

```onion
class LineFilter {
public:
  static def main(args: String[]): void
  // E0085: static method main must have a body
}
```

対処: 本体を追加するか、`static` を外して通常の abstract インスタンスメソッドとして
宣言してください。

### `E0069` — ローカル val に初期化子が必要

ローカル `val` が初期化子（`= expr`）なしで宣言されました。`var` と異なり `val` は
後から代入できないため、初期化子のない `val` は JVM のゼロ値デフォルトしか読み取れず
— それはバグであって、値ではありません。

```onion
class Test {
public:
  static def main(args: String[]): Int {
    val x: String   // E0069: x は初期化されていない
    IO::println(x)
    return 0
  }
}
```

対処: 宣言時に初期化子を与える（`val x: String = ...`）か、後から代入する必要が
本当にあるなら `var` を使ってください。コンストラクタ内で初期化されるフィールドの
`val` はこのエラーの対象外です。

## コンストラクタエラー

### `E0087` — セカンダリコンストラクタはプライマリに委譲する必要がある

プライマリコンストラクタ（クラス名の後ろのパラメータリスト、または `extends` 節の引数、
またはその両方）を持つクラスで、`: this(...)` で委譲しない `def this` を宣言しています。
親クラスのコンストラクタを呼び、`val`/`var` パラメータをフィールドに格納するのは
プライマリコンストラクタだけです。それを経由しないコンストラクタは、クラスが書いていない
暗黙の `super()` で親に到達し、フィールドを既定値のまま残します。この検査ができる前は
`class P(val x: Int) { def this { } }` がコンパイルでき、`new P().x` は `0` でした。

```onion
class Point(val x: Int, val y: Int) {
public:
  def this(x: Int) { }   // E0087: 委譲が必要 — `: this(x, 0)` と書く
}
```

修正: `def this(x: Int) : this(x, 0) { }` のように委譲を追加してください。プライマリ
コンストラクタを持たないクラスは影響を受けません。その `def this` は今まで通り親クラスの
引数なしコンストラクタを呼びます。

### `E0088` — コンストラクタ委譲の循環

複数のコンストラクタが互いに委譲し合って循環しており、親クラスのコンストラクタを呼ぶ
コンストラクタにどの経路からも到達しません。`new` すると `StackOverflowError` になります。

```onion
class C {
public:
  def this(a: Int) : this("s") { }   // E0088
  def this(s: String) : this(1) { }
}
```

修正: 循環の中の 1 つを委譲しないコンストラクタにする（プライマリを持つクラスでは
プライマリに委譲する）。

### `E0089` — record / enum 本体のコンストラクタ

`record` または `enum` の本体に `def this` があります。どちらも成分リスト／パラメータ
リストから正準コンストラクタが生成されるので、2 つ目のコンストラクタに意味を与えられません。
以前はこれが診断ではなくコンパイラのクラッシュでした。本体は型付けされず、コード生成が
record の「パラメータをフィールドに格納する」処理を引数の数が違うコンストラクタに対して
行っていました。

```onion
record R(a: Int, b: Int) {
public:
  def this(a: Int) { }   // E0089
}
```

修正: static なファクトリメソッドを使ってください（`static def of(a: Int): R = new R(a, 0)`）。

### `E0090` — コンストラクタ委譲より前の `this`

`: this(...)` の委譲や `extends B(...)` の親呼び出しの引数の中で、`this` が（明示的に、
または裸のフィールド名を通して）使われています。この時点ではオブジェクトがまだ存在しません。
JVM の検証器は `uninitializedThis` に対するフィールド読み出しを拒否するため、この検査が
無いとプログラムはロードに失敗するクラスにコンパイルされ、内部エラーとして報告されて
いました。Java の "cannot reference `x` before supertype constructor has been called" と
同じ規則です。

```onion
class F(val x: Int) {
public:
  var seed: Int = 3
  def this : this(seed) { }   // E0090: `seed` はまだ構築されていないオブジェクトのフィールド
}
```

修正: パラメータか定数を渡してください。クロージャは `this` を捕捉して後で（オブジェクトが
存在してから）実行されるので、この位置に書いても問題ありません。

## 制御フローエラー

### `E0048` — ループの外での break

`break`（ラベル付き・なし問わず）が、囲むループのない場所に出現しています。

```onion
class Test {
public:
  static def main(args: String[]): Int {
    break   // E0048: 囲むループがない
    return 0
  }
}
```

対処: `break` は `while`・`for`・`foreach` ループの内側でのみ使用してください。

### `E0049` — ループの外での continue

`continue`（ラベル付き・なし問わず）が、囲むループのない場所に出現しています。

```onion
class Test {
public:
  static def main(args: String[]): Int {
    continue   // E0049: 囲むループがない
    return 0
  }
}
```

対処: `continue` は `while`・`for`・`foreach` ループの内側でのみ使用してください。

### `E0050` — static コンテキストで現在のインスタンスが利用できない

`static` メソッドの内部で `this`/`self` が使われています。static メソッドには
レシーバインスタンスが存在しません。

```onion
class Test {
public:
  static def s(): Int { return this.hashCode() }   // E0050: s は static
  static def main(args: String[]): Int { return 0 }
}
```

対処: `static` を外すか、インスタンスを明示的にパラメータとして渡してください。

### `E0058` — ラベルが見つからない

ラベル付きの `break`/`continue` が、囲んでいるどのループにも束縛されていない
ラベルを指定しています。

```onion
class Test {
public:
  static def main(args: String[]): void {
    foreach i: Int in 0..3 {
      break nosuch   // E0058: nosuch というラベルの付いた囲みループがない
    }
  }
}
```

対処: 対象のループにラベルを付ける（`outer: foreach ... { break outer }`）か、
ラベルの綴りを修正してください。

## パターンマッチングエラー

### `E0042` — 網羅性のないパターンマッチング

sealed 型に対する `select` がすべてのケースを網羅していません。

```onion
sealed interface Shape {}
record Circle(r: Int) conforms Shape
record Rect(w: Int, h: Int) conforms Shape

select shape {
  case Circle(r): println("circle")
  // Rect ケースが欠けている → E0042
}
```

### `E0043` — 未知の名前付き引数

呼び出しが `name = value` 構文を使ったが、その名前が解決されたメソッド・
コンストラクタ・関数のどのパラメータとも一致しません。

```onion
class T {
public:
  static def f(x: Int, y: Int): Int = x + y
  static def main(args: String[]): Int { return f(x = 1, nope = 2) }   // E0043: f に nope という名前のパラメータはない
}
```

対処: 引数名のスペルを対象の宣言と照らし合わせてください。

### `E0044` — 引数の重複

同じパラメータが1回の呼び出しの中で2回束縛されています — 例えば、同じ呼び出し内で
既に使われた名前を繰り返す名前付き引数などです。

```onion
class Test {
public:
  static def f(x: Int, y: Int): Int = x + y
  static def main(args: String[]): Int { return f(x = 1, x = 2) }   // E0044: x は既に束縛されている
}
```

対処: 重複した束縛を削除してください。

### `E0045` — 名前付き引数の後の位置引数

呼び出しで、名前付き引数の後に位置引数が置かれています。呼び出しが一度名前付き
引数を使い始めたら、それ以降の引数もすべて名前で指定する必要があります —
そうでなければ、位置引数がどのパラメータを埋めるのか曖昧になるためです。

```onion
class Test {
public:
  static def f(x: Int, y: Int): Int = x + y
  static def main(args: String[]): Int { return f(x = 1, 2) }   // E0045: 2 が名前付き引数の後に来ている
}
```

対処: 残りの引数もすべて名前付きにするか、位置引数を先頭にまとめてください。

### `E0046` — 分割宣言パターンの束縛数が一致しない

分割宣言の `val`/`var (a, b, ...)`（またはネストした分割パターン）で指定した
変数の数が、record のコンポーネント数と一致していません。

```onion
record Point(x: Int, y: Int)
class Test {
public:
  static def main(args: String[]): Int {
    val (a, b, c) = new Point(1, 2)   // E0046: Point は2フィールドだが3つの束縛が指定された
    return 0
  }
}
```

対処: 束縛の数を record のコンポーネント数に合わせてください。

### `E0047` — record 型ではない

分割宣言の `val`/`var (a, b, ...) = expr` が、record ではない型の値に対して
使われたため、位置で束縛できるコンポーネントがありません。

```onion
class Test {
public:
  static def main(args: String[]): Int {
    val (a, b) = "not a record"   // E0047: String は record 型ではない
    return 0
  }
}
```

対処: record の値を分割するか、通常の `val`/`var` で値全体を束縛してください。

### `E0059` — 不正な正規表現リテラル

`re"..."` リテラル（裸の式、`select case`、`from re"..."` 句のいずれでも）が
正しい正規表現になっていません。コンパイル時に検証されるため、不正なパターンは
実行時に `PatternSyntaxException` をスローする代わりにここで捕捉されます。

```onion
val p = re"(unclosed"   // E0059: 不正な正規表現リテラル
```

対処: パターンを修正してください。

### `E0060` — 正規表現のキャプチャグループ数とバインディング数の不一致

`case re"..." (b1, b2, ...)` パターン（または `record ... from re"..."` 句）が、
パターンのキャプチャグループ数とは異なる数のバインディング／成分を指定しています。

```onion
select "x" {
  case re"(\d+)-(\d+)" (a): a   // E0060: パターンは2グループだが、バインディングは1個
  else: "no"
}
```

対処: バインディング（またはレコード成分）の数をパターンのキャプチャグループ数に
合わせてください。

## パーサーエラー

パーサーエラーには `E` コードは付きません。出現したトークンと期待されるトークンが報告されます。

```
Test.on:2:10: Syntax error. Encountered "{", but expecting ";"
```

## 診断コード一覧

上の各節は遭遇しやすいコードを例つきで説明しています。この表は全コードの一覧なので、
ビルドログで見たコードは必ずここで引けます。

| コード | 意味 |
|--------|------|
| `I0000` | コンパイラ内部エラー — あなたのプログラムではなくコンパイラのバグです |
| `E0000` | type … is expected, but type … is used |
| `E0001` | operator … is not applicable for type … |
| `E0002` | variable not found |
| `E0003` | class not found |
| `E0004` | field ….… is not found |
| `E0005` | a method applicable for ….…(…) is not found |
| `E0006` | ambiguous method |
| `E0007` | duplicated local variable definition … |
| `E0008` | duplicated class definition … |
| `E0009` | duplicated field definition ….… |
| `E0010` | duplicated method definition ….…(…) |
| `E0011` | duplicated global variable definition … |
| `E0012` | duplicated function definition …(…) |
| `E0013` | method ….…(…) is not accessible from class … |
| `E0014` | field ….… is not accessible from class … |
| `E0015` | class … is not accessible from class … |
| `E0016` | inheritance relations which include … have cyclicity |
| `E0018` | class … has illegal inheritance: … |
| `E0019` | method ….… cannot be called |
| `E0020` | this method cannot return a value |
| `E0021` | a constructor applicable for …(…) is not found |
| `E0022` | ambiguous constructor |
| `E0023` | an interface is required, but type … is used |
| `E0025` | duplicated constructor definition …(…) |
| `E0026` | duplicated generated method ….…(…) |
| `E0027` | type … is not a boxable type |
| `E0028` | an lvalue is required |
| `E0029` | duplicated type parameter definition … |
| `E0030` | type … does not take type arguments |
| `E0031` | type … expects … type arguments, but … are supplied |
| `E0032` | type argument … must be a reference type |
| `E0033` | method ….… does not take type arguments |
| `E0034` | method ….… expects … type arguments, but … are supplied |
| `E0035` | Erased JVM signature collision: ….…… |
| `E0036` | cannot assign to val … |
| `E0037` | class … must implement abstract method …(…) or be declared abstract |
| `E0038` | cannot instantiate abstract class … |
| `E0039` | method …(…) cannot override final method in … |
| `E0040` | cannot call method … on primitive type … |
| `E0041` | type … is not a valid method call target |
| `E0042` | non exhaustive pattern match |
| `E0043` | unknown parameter name: … |
| `E0044` | duplicate argument: … |
| `E0045` | positional argument after named argument is not allowed |
| `E0046` | wrong number of bindings in destructuring pattern |
| `E0047` | … is not a record type or does not exist |
| `E0048` | break is only allowed inside a loop |
| `E0049` | continue is only allowed inside a loop |
| `E0050` | current instance is not available in a static context |
| `E0051` | a return type is required for method … |
| `E0052` | lambda parameter … must specify a type |
| `E0053` | cyclic type alias detected: … |
| `E0054` | duplicate type alias: … |
| `E0055` | function … requires a body |
| `E0057` | value of type parameter … may be null and cannot be dereferenced directly |
| `E0058` | label … is not defined on any enclosing loop |
| `E0059` | invalid regular expression literal: … |
| `E0060` | the regex pattern has … capture group(s) but … binding(s) were given |
| `E0061` | record component … has type …, which cannot be derived from a `from re"..."` clause |
| `E0062` | record component … has type …, which derive! cannot serialize |
| `E0063` | unknown derive! marker `…` |
| `E0064` | law violation |
| `E0065` | example failed |
| `E0066` | raw type … is not allowed; supply type arguments (e.g |
| `E0067` | method … may reach the end of its body without returning a … |
| `E0068` | method …(…) is marked override but does not override any method in a base class or interface of … |
| `E0069` | local val … must be initialized at its declaration |
| `E0070` | value of type … may be null and cannot be dereferenced directly |
| `E0071` | … is a variable, not a type |
| `E0072` | abstract method … cannot have a body (the body would be silently ignored) |
| `E0073` | a Map (…) cannot be iterated directly by foreach |
| `E0074` | law parameter not generatable |
| `E0075` | law class not loadable |
| `E0076` | unknown shape format `…` |
| `E0077` | tool `…` performs `…` here (calling …) but does not declare it |
| `E0078` | tool `…` declares capability `…` but nothing in its body can perform it |
| `E0079` | tool `…` has an invalid capability `…`: … |
| `E0080` | class `…` implements onion.Shape but nothing in its file asserts a law |
| `E0081` | tool parameter not cli convertible |
| `E0082` | duplicate tool name `…` |
| `E0083` | this catch clause for … can never be reached: an earlier catch clause for … already handles it |
| `E0084` | duplicated extension method …(…) on … |
| `E0085` | static method … must have a body |
| `E0086` | duplicated record component … in … |
| `E0087` | class … has a primary constructor, so every `def this` must delegate to it |
| `E0088` | constructor delegation in … never reaches a constructor that calls the superclass |
| `E0089` | a record / an enum cannot declare `def this`: … already has its canonical constructor |
| `E0090` | `this` is used in a constructor's delegation arguments before the object of … exists |
| `E0091` | … is a class, not a variable |

## 関連項目

- [言語仕様](specification.md)
- [標準ライブラリ](stdlib.md)
