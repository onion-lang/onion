# クラスとオブジェクト

Onionはクラス、継承、インターフェースを完全にサポートするオブジェクト指向言語です。

## クラス定義

`class` キーワードでクラスを定義します：

```onion
class Person {
  val name: String
  var age: Int

  public:
    def this(n: String, a: Int) {
      this.name = n
      this.age = a
    }

    def greet: String = "Hello, I'm " + this.name
}
```

`new` でオブジェクトを生成します：

```onion
val person: Person = new Person("Alice", 30)
IO::println(person.greet())  // "Hello, I'm Alice"
```

## プライマリコンストラクタ

クラス名の後ろにパラメータを書くと、プライマリコンストラクタになります。`val`/`var` パラメータはそのままパブリックフィールドになります：

```onion
class Point(val x: Int, val y: Int) {
public:
  def dist(): Int { return this.x * this.x + this.y * this.y }
}

class Conf(val host: String = "localhost", var port: Int = 8080)

class Animal(val name: String)
class Dog(name: String, val breed: String) : Animal(name)

val p = new Point(3, 4)        // p.x、p.y が使える
val c = new Conf(port = 9090)  // host はデフォルト値 "localhost"
```

## フィールドとアクセス修飾子

フィールドは**デフォルトでprivate**です。`public:` セクションで公開します：

```onion
class BankAccount {
  var balance: Double    // private
  val accountNumber: String  // private

  public:
    val owner: String  // public

    def this(owner: String, initial: Double) {
      this.owner = owner
      this.balance = initial
      this.accountNumber = "UNKNOWN"
    }

    def deposit(amount: Double) {
      this.balance = this.balance + amount
    }

    def getBalance: Double = this.balance
}
```

## staticメンバー

`static` キーワードでクラスレベルのメンバーを宣言します。アクセスには `::` を使います：

```onion
class MathUtils {
  static val PI: Double = 3.14159

  public:
    static def square(x: Double): Double = x * x
}

val area: Double = MathUtils::PI * MathUtils::square(5.0)
```

## Records

Recordsは`equals`、`hashCode`、`toString`、`copy`が自動生成される不変データクラスです：

```onion
record Point(x: Int, y: Int)

val p = new Point(1, 2)
p.x()            // コンポーネントアクセス（メソッド呼び出し）
p.copy(y = 9)    // 名前付き引数で部分コピー
p.copy()         // フルクローン
```

型パラメータも使えます：

```onion
record Pair[A, B](first: A, second: B)

val p = new Pair[String, Integer]("gen", 9)
val (s, n) = p              // 分割代入
p.copy(second = 42)         // 名前付き引数でコピー
```

## 演算子オーバーロード

二項演算子は左オペランドのメソッドにディスパッチされます（Kotlinスタイル）：`a + b` は `a.plus(b)` を呼び出します：

```onion
class Vec {
  val x: Int
  val y: Int
public:
  def this(x: Int, y: Int) { this.x = x; this.y = y }
  def plus(o: Vec): Vec { return new Vec(this.x + o.x, this.y + o.y) }
  def times(k: Int): Vec { return new Vec(this.x * k, this.y * k) }
}

val v = new Vec(1, 2) + new Vec(3, 4)   // Vec(4, 6)
val w = new Vec(1, 2) * 3               // Vec(3, 6)
```

## Enums

Enumsは標準JVM enumにコンパイルされます。データを持つenumも定義できます：

```onion
enum Color { RED, GREEN, BLUE }

enum Planet(mass: Double) {
  MERCURY(3.3e23),
  EARTH(5.97e24)
}

IO::println("" + Planet::EARTH.mass())
foreach p: Planet in Planet::values() {
  IO::println(p.name() + " = " + p.mass())
}
```

## 次のステップ

- [継承](inheritance.md) - クラスの拡張とインターフェースの実装
- [Javaとの相互運用](java-interop.md) - Javaクラスの活用
- [関数](functions.md) - メソッドと関数の詳細
