# 継承

Onionはクラスの継承とインターフェースの実装をサポートし、型の階層構造を構築できます。

## クラスの継承

`:` で親クラスを継承します：

```onion
class Animal {
  val name: String

  public:
    def this(n: String) {
      this.name = n
    }

    def speak: String = "Some sound"
}

class Dog : Animal {
  public:
    def this(n: String): (n) { }   // 親コンストラクタの呼び出し

    def speak: String = "Woof!"    // メソッドのオーバーライド
}

val dog: Dog = new Dog("Buddy")
IO::println(dog.speak())  // "Woof!"
```

親コンストラクタへの引数は `def this(args): (superArgs) { ... }` の形式で渡します：

```onion
class Vehicle {
  val brand: String

  public:
    def this(b: String) {
      this.brand = b
    }
}

class Car : Vehicle {
  val model: String

  public:
    def this(b: String, m: String): (b) {
      this.model = m
    }
}
```

## インターフェースの実装

`<:` でインターフェースを実装します。複数のインターフェースはカンマ区切りで列挙します：

```onion
import { java.lang.Comparable; }

class Person <: Comparable {
  val name: String
  val age: Int

  public:
    def this(n: String, a: Int) {
      this.name = n
      this.age = a
    }

    def compareTo(other: Object): Int {
      val otherPerson: Person = (other as Person)
      return this.age - otherPerson.age
    }
}
```

## デフォルトメソッド

インターフェースのメソッドにボディを書くとJVMのデフォルトメソッドになります：

```onion
interface Greeter {
  def name(): String
  def greet(): String { return "Hello, " + this.name() }
}

class K <: Greeter {
public:
  def this {}
  def name(): String { return "kota" }
}

IO::println(new K().greet())   // Hello, kota（overrideなしで使える）
```

## 継承とインターフェースの組み合わせ

`:` と `<:` を組み合わせて使えます：

```onion
// 構文
class Child : ParentClass <: Interface1, Interface2 {
  // ...
}
```

## Delegationパターン（`forward`）

`forward` でインターフェースのメソッド委譲を自動生成します：

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

    def addAll(items: String[]) {
      foreach item: String in items {
        this.internal << item;
      }
    }
}
```

## ポリモーフィズム

子クラスのオブジェクトを親型の変数に代入できます：

```onion
class Animal {
  public:
    def speak: String = "Generic sound"
}

class Dog : Animal {
  public:
    def speak: String = "Woof!"
}

class Cat : Animal {
  public:
    def speak: String = "Meow!"
}

val animals: Animal[] = new Animal[3]
animals[0] = new Dog
animals[1] = new Cat
animals[2] = new Animal

foreach animal: Animal in animals {
  IO::println(animal.speak())
}
// Woof!
// Meow!
// Generic sound
```

## 抽象クラス

`abstract` で抽象クラスとメソッドを宣言します：

```onion
abstract class Shape {
  public:
    abstract def area(): Double;
}

class Circle : Shape {
  val radius: Double

  public:
    def this(r: Double) {
      this.radius = r
    }

    def area(): Double = 3.14159 * this.radius * this.radius
}
```

## 次のステップ

- [Javaとの相互運用](java-interop.md) - Javaクラスの拡張
- [ラムダ式](lambda-expressions.md) - 関数型プログラミング
- [クラスとオブジェクト](classes-and-objects.md) - Records・Enums・演算子オーバーロード
