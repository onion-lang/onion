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
  shape doc = toml     // E0076: サポートしているのは json, yaml
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
| `E0005` | method applicable for ….…(…) is not found |
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
| `E0016` | inheritance relations which includes … have cyclicity |
| `E0018` | class … do inheritance illegally |
| `E0019` | method ….… cannot be called |
| `E0020` | this method cannot return value |
| `E0021` | constructor not found |
| `E0022` | ambiguous constructor |
| `E0023` | interface required, but type … is used |
| `E0025` | duplicated constructor definition …(…) |
| `E0026` | duplicated generated method ….…(…) |
| `E0027` | type … is not boxable type |
| `E0028` | lvalue is required |
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
| `E0050` | current instance is not available in static context |
| `E0051` | return type is required for method … |
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

## 関連項目

- [言語仕様](specification.md)
- [標準ライブラリ](stdlib.md)
