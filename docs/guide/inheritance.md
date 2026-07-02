# Inheritance

Onion supports both class inheritance and interface implementation, allowing you to build hierarchical type systems.

## Extending Classes

### Basic Inheritance

Use `:` to extend a parent class:

```onion
class Animal {
protected:
  val name: String

  public:
    def this(n: String) {
      this.name = n
    }

    def speak: String = "Some sound"
}

class Dog : Animal {
  public:
    def this(n: String): (n) { }

    def speak: String = "Woof!"
}

val dog: Dog = new Dog("Buddy")
println(dog.speak())  // "Woof!"
```

### Calling Parent Constructor

Call the parent constructor via the super-initializer list on `def this`:

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

### Method Overriding

Child classes can override parent methods:

```onion
class Shape {
  public:
    def area: Double = 0.0

    def describe: String = "A shape"
}

class Circle : Shape {
  val radius: Double

  public:
    def this(r: Double) {
      this.radius = r
    }

    def area: Double = 3.14159 * this.radius * this.radius  // Override

    def describe: String = "A circle with radius " + this.radius  // Override
}
```

## Implementing Interfaces

### Single Interface

Use `<:` to implement an interface:

```onion
import { java.lang.Comparable; }

class Person <: Comparable[Object] {
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

### Default Methods

An interface method with a body compiles to a JVM default method:
implementing classes inherit it, may override it, and dispatch stays
virtual through the interface type. Expression bodies work too.

```onion
interface Greeter {
  def name(): String
  def greet(): String { return "Hello, " + this.name() }
  def shout(): String = "HEY " + this.name()
}

class K <: Greeter {
public:
  def this {}
  def name(): String { return "kota" }
}

println(new K().greet())   // Hello, kota — no override needed
```

### Multiple Interfaces

Implement multiple interfaces with a comma-separated list:

```onion
import {
  java.io.Serializable;
  java.lang.Comparable;
}

class Student <: Serializable, Comparable[Object] {
  val id: Int
  val name: String

  public:
    def this(i: Int, n: String) {
      this.id = i
      this.name = n
    }

    def compareTo(other: Object): Int {
      val otherStudent: Student = (other as Student)
      return this.id - otherStudent.id
    }
}
```

## Combining Inheritance and Interfaces

Use both `:` (extend class) and `<:` (implement interface):

```onion
import {
  javax.swing.JFrame;
  java.awt.event.ActionListener;
  java.awt.event.ActionEvent;
}

class Calculator : JFrame <: ActionListener {
  var result: Long

  public:
    def this {
      // JFrame constructor
      this.result = 0L
    }

    def actionPerformed(event :ActionEvent) {
      // Implement ActionListener interface
      println("Button clicked")
    }
}
```

Syntax:
```onion
class Child : ParentClass <: Interface1, Interface2 {
  // ...
}
```

## Delegation Pattern

### Forward Directive

Use `forward` to delegate interface methods to a member:

```onion
interface Logger {
  def log(message: String): void
  def count(): Int
}

class BasicLogger <: Logger {
  var n: Int

  public:
    def this { this.n = 0 }
    def log(message: String): void {
      this.n = this.n + 1
      println(message)
    }
    def count(): Int = n
}

class PrefixLogger <: Logger {
  forward val delegate: Logger

  public:
    def this(delegate: Logger) {
      this.delegate = delegate
    }

    // Custom method
    def logAll(items: String[]): void {
      foreach item: String in items {
        this.delegate.log("[app] " + item)
      }
    }
}

val logger: PrefixLogger = new PrefixLogger(new BasicLogger())
logger.logAll(new String[]{"First", "Second"})
println(logger.count())   // count() is forwarded to BasicLogger -> 2
```

The `forward` directive automatically implements the interface methods (`log`/`count`) by forwarding calls to the `delegate` member, so `PrefixLogger` only needs to add its own behaviour.

### Forwarding a Generic Interface

`forward` also works over a **parameterized generic interface**, not just non-generic ones. Delegate to a `List[String]` member and the class satisfies `List[String]` for free:

```onion
import {
  java.util.List;
  java.util.ArrayList;
}

class MyList <: List[String] {
  forward val backing: List[String]

  public:
    def this(xs: List[String]) { backing = xs }
}

def main(args: String[]): void {
  val base: List[String] = new ArrayList[String]()
  base.add("a")
  base.add("b")

  // MyList is usable anywhere a List[String] is expected
  val list: List[String] = new MyList(base)
  list.add("c")
  println("size=" + list.size())   // size=3
  foreach s: String in list {
    println(s)                      // a, b, c
  }
}
```

The same works for a **user-defined generic interface** — forward to a `Container[Int]` member and the class implements `Container[Int]`:

```onion
interface Container[T] {
  def first(): T
  def count(): Int
}

class IntBox <: Container[Int] {
  public:
    def this {}
    def first(): Int { return 42 }
    def count(): Int { return 3 }
}

class Wrapper <: Container[Int] {
  forward val inner: Container[Int]

  public:
    def this(c: Container[Int]) { inner = c }
}

def main(args: String[]): void {
  val w: Container[Int] = new Wrapper(new IntBox())
  println("count=" + w.count())   // count=3
  println("first=" + w.first())   // first=42
}
```

## Polymorphism

### Type Substitution

Child objects can be assigned to parent variables:

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

foreach animal :Animal in animals {
  println(animal.speak())
}
// Output:
// Woof!
// Meow!
// Generic sound
```

### Interface Polymorphism

```onion
import {
  java.util.List;
  java.util.ArrayList;
  java.util.LinkedList;
}

val list1: List[String] = new ArrayList[String]()  // ArrayList implements List
val list2: List[String] = new LinkedList[String]()  // LinkedList implements List

val lists: java.util.List[List[String]] = [list1, list2]
foreach list: List[String] in lists {
  list.add("Item")
  println("Size: " + list.size())
}
```

## Abstract Concepts

### Note on Abstract Methods

The compiler enforces abstract method implementation at compile time. Concrete subclasses must implement abstract members or be declared `abstract`:

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

## Inheritance Best Practices

### Favor Composition Over Inheritance

Use delegation when possible:

```onion
interface Logger {
  def log(message: String): void
  def count(): Int
}

// Instead of inheriting from a concrete BasicLogger...
// class PrefixLogger : BasicLogger { ... }

// ...prefer delegating to the Logger interface.
class PrefixLogger <: Logger {
  forward val delegate: Logger

  public:
    def this(delegate: Logger) {
      this.delegate = delegate
    }
}
```

### Keep Hierarchies Shallow

Avoid deep inheritance chains:

```onion
// Good: Shallow hierarchy
Animal
  ├─ Dog
  └─ Cat

// Bad: Deep hierarchy
Vehicle
  └─ MotorVehicle
      └─ Car
          └─ Sedan
              └─ LuxurySedan
```

### Override Consistently

When overriding methods, maintain the parent's contract:

```onion
class Parent {
  public:
    def process(value :Int) :Int {
      if value < 0 {
        return 0
      }
      return value * 2
    }
}

class Child : Parent {
  public:
    def process(value :Int) :Int {
      // Maintain parent's behavior
      if value < 0 {
        return 0
      }
      return value * 3  // Different implementation, same contract
    }
}
```

## Next Steps

- [Java Interoperability](java-interop.md) - Working with Java classes
- [Lambda Expressions](lambda-expressions.md) - Functional programming
- [Examples](../examples/oop.md) - Object-oriented examples
