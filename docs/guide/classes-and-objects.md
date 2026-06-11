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
IO::println(person.greet())  // "Hello, I'm Alice"
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
class Dog(name: String, val breed: String) : Animal(name)   // body-less is fine

val p = new Point(3, 4)        // p.x, p.y readable; p.x = 9 is an error (val)
val c = new Conf(port = 9090)  // host defaults to "localhost"
```

`def this(...)` constructors remain available for classes that need
explicit bodies or multiple overloads.

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

### Default Constructor

Define constructors with `def this`:

```onion
class Point {
  val x: Int
  val y: Int

  public:
    def this(x: Int, y: Int) {
      this.x = x
      this.y = y
    }
}

val point: Point = new Point(10, 20)
```

### Multiple Constructors

Overload constructors for different initialization patterns:

```onion
class Rectangle {
  val width: Int
  val height: Int

  public:
    def this {
      this.width = 0
      this.height = 0
    }

    def this(size: Int) {
      this.width = size
      this.height = size
    }

    def this(w: Int, h: Int) {
      this.width = w
      this.height = h
    }
}

val rect1: Rectangle = new Rectangle()
val rect2: Rectangle = new Rectangle(10)
val rect3: Rectangle = new Rectangle(10, 20)
```

### Calling Super Constructors

To call a superclass constructor, add a super-initializer list: `def this(args): (superArgs) { ... }`.

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
IO::println("Area: " + circle.area())
```

### Method Overloading

Multiple methods with the same name but different parameters:

```onion
class Printer {
  public:
    def print(value :Int) {
      IO::println("Int: " + value)
    }

    def print(value :String) {
      IO::println("String: " + value)
    }

    def print(value :Double) {
      IO::println("Double: " + value)
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

class ButtonHandler <: ActionListener {
  public:
    def actionPerformed(event :ActionEvent) {
      val button: JButton = event.getSource()$JButton
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

IO::println("" + Planet::EARTH.mass())
foreach p: Planet in Planet::values() {
  IO::println(p.name() + " = " + p.mass())
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
