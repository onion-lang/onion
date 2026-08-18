# Classes and Objects

Onion is an object-oriented language with full support for classes, inheritance, and interfaces.

## Class Definition

### Basic Class

Define a class with the `class` keyword:

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

### Creating Objects

Instantiate objects with the `new` keyword:

```onion
val person: Person = new Person("Alice", 30)
println(person.greet())  // "Hello, I'm Alice"
```

## Primary Constructors

The concise way to declare a class: parameters after the class name form
the primary constructor. `val`/`var` parameters become public fields
(final/mutable) assigned automatically; plain parameters exist only in
the constructor (useful for superclass arguments). Defaults and named
arguments work like everywhere else.

```onion
class Point(val x: Int, val y: Int) {
public:
  def dist(): Int { return this.x * this.x + this.y * this.y }
}

class Conf(val host: String = "localhost", var port: Int = 8080)

class Animal(val name: String)
class Dog(name: String, val breed: String) extends Animal(name)   // body-less is fine

val p = new Point(3, 4)        // p.x, p.y readable; p.x = 9 is an error (val)
val c = new Conf(port = 9090)  // host defaults to "localhost"
```

A class with a primary constructor can still declare more constructors with
`def this`, and each of them **delegates to the primary** with `: this(...)`.
See [Constructors](#constructors) below.

## Fields

### Instance Fields

Declare instance fields with `val` (immutable) or `var` (mutable), and access them via `this.field`:

```onion
class Counter {
  var count: Int

  public:
    def this {
      this.count = 0
    }

    def increment {
      this.count = this.count + 1
    }

    def getCount: Int = this.count
}
```

### Access Modifiers

Members are **private by default**. Use `public:` to mark public members:

```onion
class BankAccount {
  var balance: Double  // Private (default)
  val accountNumber: String  // Private

  public:
    val owner: String  // Public

    def this(owner: String, initial: Double) {
      this.owner = owner
      this.balance = initial
      this.accountNumber = "UNKNOWN"
    }

    def deposit(amount :Double) {  // Public method
      this.balance = this.balance + amount
    }

    def getBalance: Double = this.balance  // Public method
}
```

### Static Members

Static members belong to the class, not instances:

```onion
class MathUtils {
  static val PI: Double = 3.14159

  public:
    static def square(x: Double): Double = x * x

    static def circleArea(radius: Double): Double = MathUtils::PI * radius * radius
}

// Access static members with ::
val pi: Double = MathUtils::PI
val area: Double = MathUtils::circleArea(5.0)
```

## Constructors

Onion has the primary/secondary constructor model of Scala and Kotlin.

- The **primary constructor** is the parameter list after the class name, together
  with the arguments on the `extends` clause. It is where the superclass constructor
  is called, and it stores every `val`/`var` parameter into its field.
- A **secondary constructor** is a `def this(...)` in the class body. In a class that
  has a primary constructor, every secondary delegates to it with `: this(...)`,
  directly or through another secondary. That rule is what guarantees a `val` field
  from the primary can never be observed uninitialized, and that the superclass is
  always constructed the way the `extends` clause says.

```onion
class Point(val x: Int, val y: Int) {
public:
  def this(x: Int) : this(x, 0) { }      // secondary: delegates to the primary
  def this : this(0) { }                 // secondaries may chain
  def dist(): Int { return x * x + y * y }
}

val a = new Point(3, 4)
val b = new Point(3)                     // y == 0
val c = new Point()                      // x == 0, y == 0
```

A `def this` in a class with a primary constructor that does not delegate is an
error (`E0087`). Before this rule existed, `class P(val x: Int) { def this { } }`
compiled and `new P().x` was `0`.

### Superclass Arguments

Arguments for the superclass constructor are written on the `extends` clause, and
only there. A secondary constructor reaches them by delegating to the primary:

```onion
class Animal(val name: String)

class Dog(name: String, val breed: String) extends Animal(name) {
public:
  def this(name: String) : this(name, "mixed") { }
}
```

A class with no parameter list but arguments on `extends` has a no-arg primary
constructor that passes them along -- the same reading Kotlin and Scala give it:

```onion
class Rex extends Animal("Rex") { }      // new Rex().name == "Rex"
```

### Classes Without a Primary Constructor

A class that declares neither a parameter list nor `extends` arguments has no
primary constructor. Its `def this` constructors call the superclass no-arg
constructor implicitly, and may delegate to one another:

```onion
class Rectangle {
  val width: Int
  val height: Int
public:
  def this(w: Int, h: Int) {
    this.width = w
    this.height = h
  }
  def this(size: Int) : this(size, size) { }
  def this : this(0) { }
}
```

Such a class cannot pass arguments to its superclass; write a primary constructor
when it needs to. (An older form, `def this(x) : (x) { }`, used to do that and no
longer exists -- the compiler points at the `extends` clause if it meets it.)

### Field Initializers

Field initializers run after the primary constructor has stored its parameters, so
they can read the `val`/`var` parameter fields:

```onion
class Account(val opening: Int) {
public:
  var balance: Int = opening        // reads the field the primary just stored
  var history: List[Int] = [opening]
}
```

They run once, in the primary, regardless of which constructor `new` went through.
A plain (non-`val`/`var`) primary parameter is not a field and is not visible to an
initializer.

### What a Constructor's Arguments May Not Do

The object does not exist yet while its delegation arguments are being evaluated,
so neither `this` nor a field can be read inside `: this(...)` or `extends B(...)`
(`E0090`); pass a parameter or a constant instead. A closure in that position is
fine -- it captures `this` and runs later. Two constructors delegating to each other
in a cycle are rejected (`E0088`) rather than left to overflow the stack at `new`.
Records and enums have their canonical constructor and cannot declare `def this`
(`E0089`).

The primary constructor is always public. A class that wants a private constructor
-- the factory-method pattern -- declares no primary and uses `def this` in the
default (private) section.

## Methods

### Instance Methods

Methods that operate on instance data:

```onion
class Circle {
  val radius: Double

  public:
    def this(r: Double) {
      this.radius = r
    }

    def area: Double = 3.14159 * this.radius * this.radius

    def circumference: Double = 2.0 * 3.14159 * this.radius
}

val circle: Circle = new Circle(5.0)
println("Area: " + circle.area())
```

### Method Overloading

Multiple methods with the same name but different parameters:

```onion
class Printer {
  public:
    def print(value :Int) {
      println("Int: " + value)
    }

    def print(value :String) {
      println("String: " + value)
    }

    def print(value :Double) {
      println("Double: " + value)
    }
}

val printer: Printer = new Printer
printer.print(42)
printer.print("Hello")
printer.print(3.14)
```

### Getter and Setter Methods

```onion
class Person {
  var name: String
  var age: Int

  public:
    def getName: String = this.name

    def setName(name :String) {
      this.name = name
    }

    def getAge: Int = this.age

    def setAge(age :Int) {
      if age >= 0 {
        this.age = age
      }
    }
}
```

## The `self` Reference

Access the current instance with `self`:

```onion
import {
  javax.swing.JButton;
  java.awt.event.ActionEvent;
  java.awt.event.ActionListener;
}

class ButtonHandler conforms ActionListener {
  public:
    def actionPerformed(event :ActionEvent) {
      val button: JButton = event.getSource() as JButton
      button.addActionListener(self)  // Reference to this instance
    }
}
```

`this` and `self` are only available in instance contexts; static methods and static fields cannot reference them.

## Next Steps

- [Inheritance](inheritance.md) - Extending classes and implementing interfaces
- [Java Interoperability](java-interop.md) - Working with Java classes
- [Examples](../examples/oop.md) - Object-oriented examples

## Records

Records are concise immutable data classes with generated `equals`,
`hashCode`, `toString` and `copy`:

```onion
record Point(x: Int, y: Int)

val p = new Point(1, 2)
p.x()                       // component access (methods)
p.copy(y = 9)               // partial copy with named arguments
p.copy()                    // full clone
p.copy(5, 6)                // positional copy
```

Records work with `select` pattern matching when combined with sealed
interfaces, and take type parameters:

```onion
record Pair[A, B](first: A, second: B)

val p = new Pair[String, Integer]("gen", 9)
val (s, n) = p                 // destructuring declaration
p.copy(second = 42)            // named-argument copy
```

A record can also carry a `{ ... }` body of methods — instance methods, static
factories, private helpers, and operator methods — just like a class or enum.
The methods see the generated component accessors:

```onion
record Fraction(num: Int, den: Int) {
public:
  static def of(n: Int, d: Int): Fraction {
    val g = gcd(Math::abs(n), d)
    return new Fraction(n / g, d / g)
  }
  def plus(o: Fraction): Fraction =         // backs the `+` operator
    Fraction::of(num() * o.den() + o.num() * den(), den() * o.den())
  def toDouble(): Double = (num() as Double) / (den() as Double)
private:
  static def gcd(a: Int, b: Int): Int { ... }
}

val third = Fraction::of(1, 3)
val one = third + third + third            // exactly 1/1
```

## Generic Classes

Classes can take type parameters in `[]`. A parameter is available
throughout the body as an ordinary type:

```onion
class Box[T] {
  val v: T
public:
  def this(x: T) { v = x }
  def get(): T = v
}
```

A constructor infers its type arguments, so `new Box(...)` needs no `[T]` (the
"diamond"). They are inferred from the expected type when there is one, and
otherwise from the constructor arguments — the same way a generic method call
infers from its arguments. Explicit type arguments still work, and all forms
are equivalent:

```onion
val b: Box[String] = new Box("x")           // T inferred from the expected type
val n: Box[Integer] = new Box(9)            // T inferred as Integer
val b2: Box[String] = new Box[String]("y")  // explicit — same result
val b3 = new Box("z")                       // T inferred from the argument
```

Argument inference requires every type parameter to be pinned by an argument.
When nothing determines them, the bare generic is still rejected — supply an
expected type or explicit type arguments:

```onion
class Empty[T] { public: def this {} }

// val bad = new Empty()    // ERROR E0066: raw generic type Empty — nothing to
                            // infer T from; write `new Empty[String]()` or
                            // annotate the target
```

Type arguments are invariant (`Box[Dog]` is not a `Box[Animal]`); see
[Variables and Types](variables-and-types.md#generic-types-java-generics).

## Operator Overloading

Binary operators dispatch to convention methods on the left operand
(Kotlin-style): `a + b` calls `a.plus(b)`, and likewise `-` → `minus`,
`*` → `times`, `/` → `div`, `%` → `rem`. Compound assignment (`a += b`)
goes through the same method. `+` keeps string concatenation whenever a
`String` is involved, and numeric operands keep primitive arithmetic.

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

Enums compile to standard JVM enums. Constants get `name()` / `ordinal()`;
`values()` and `valueOf(String)` work as in Java. Record-style parameters
make data-carrying enums: each parameter becomes a final field with an
accessor, and constants pass constructor arguments.

```onion
enum Color { RED, GREEN, BLUE }

enum Planet(mass: Double) {
  MERCURY(3.3e23),
  EARTH(5.97e24)
}

println("" + Planet::EARTH.mass())
foreach p: Planet in Planet::values() {
  println(p.name() + " = " + p.mass())
}
Planet::valueOf("EARTH")     // works with java.lang.Enum.valueOf
```

Enums can declare methods in access sections after the constant list —
instance methods see the constant's data, static methods see `values()`:

```onion
enum Planet(mass: Double) {
  MERCURY(3.3e23),
  EARTH(5.97e24)
public:
  def heavierThan(other: Planet): Boolean {
    return this.mass() > other.mass()
  }
}
```

### Algebraic data types (`case` cases)

When the cases use the `case` keyword, each case can carry its **own** fields —
the enum becomes a full sum-of-products, so an algebraic data type no longer
needs a hand-written `sealed interface` plus `record`s:

```onion
enum Shape {
  case Circle(radius: Double)
  case Square(side: Double)
  case Origin
public:
  def area(): Double = select this {
    case c is Circle: c.radius() * c.radius() * 3.14
    case s is Square: s.side() * s.side()
    case o is Origin: 0.0
  }
}

val c: Shape = new Circle(2.0)
c.area()                      // 12.56
```

Each product case (`case Circle(radius: Double)`) has typed fields with
accessors; a singleton case (`case Origin`) is a zero-field case used as
`new Origin()`. The enum desugars to a `sealed interface` with one `record` per
case, so exhaustiveness (`E0042`) and `select` pattern matching come for free.

A `case`-style enum is a sealed hierarchy rather than a `java.lang.Enum`, so it
does not get `values()`/`valueOf()`/`ordinal()` — use the plain constant form
above when you want those.

Because it is a sealed hierarchy, a `case`-style enum can take type parameters,
which is what makes `Option`-shaped types expressible:

```onion
enum Opt[T] {
  case Some(value: T)
  case Nothing
}

def describe(o: Opt[String]): String = select o {
  case s is Some:    "some: " + s.value()   // s is a Some[String] here
  case n is Nothing: "none"
}
```

The parameters flow onto the generated interface and every case record, and a
type pattern recovers the scrutinee's type argument — matching `Some` out of an
`Opt[String]` binds `Some[String]`, so `s.value()` is a `String` rather than the
bare `T`. The plain constant form cannot take type parameters: it compiles to a
`java.lang.Enum`, which the JVM does not allow to be generic.
