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
IO::println(dog.speak())  // "Woof!"
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

class Person <: Comparable {
  val name: String
  val age: Int

  public:
    def this(n: String, a: Int) {
      this.name = n
      this.age = a
    }

    def compareTo(other: Object): Int {
      val otherPerson: Person = other$Person
      return this.age - otherPerson.age
    }
}
```

### Multiple Interfaces

Implement multiple interfaces with a comma-separated list:

```onion
import {
  java.io.Serializable;
  java.lang.Comparable;
}

class Student <: Serializable, Comparable {
  val id: Int
  val name: String

  public:
    def this(i: Int, n: String) {
      this.id = i
      this.name = n
    }

    def compareTo(other: Object): Int {
      val otherStudent: Student = other$Student
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
      IO::println("Button clicked")
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

    // Custom methods
    def addAll(items: String[]) {
      foreach item: String in items {
        this.internal << item;
      }
    }
}

val list: MyList = new MyList;
list << "First";
list << "Second";
IO::println(list.size);
```

The `forward` directive automatically implements interface methods by forwarding calls to the specified member.

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
  IO::println(animal.speak())
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

val list1: List = new ArrayList  // ArrayList implements List
val list2: List = new LinkedList  // LinkedList implements List

val lists: java.util.List = [list1, list2]
foreach list: List in lists {
  list.add("Item")
  IO::println("Size: " + list.size())
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
// Instead of inheriting from ArrayList
class MyList : ArrayList {
  // ...
}

// Consider delegation
class MyList <: List {
  forward val internal: List;

  public:
    def this {
      this.internal = new ArrayList;
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
