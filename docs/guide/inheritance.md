# Inheritance

Onion supports both class inheritance and interface implementation, allowing you to build hierarchical type systems.

## Extending Classes

### Basic Inheritance

Use `:` to extend a parent class:

```onion
class Animal {
  @name :String

  public:
    def new(n :String) {
      @name = n
    }

    def speak :String {
      "Some sound"
    }
}

class Dog : Animal {
  public:
    def new(n :String) {
      super.new(n)
    }

    def speak :String {
      "Woof!"
    }
}

def dog :Dog = new Dog("Buddy")
IO::println(dog.speak())  // "Woof!"
```

### Calling Parent Constructor

Use `super.new()` to call the parent constructor:

```onion
class Vehicle {
  @brand :String

  public:
    def new(b :String) {
      @brand = b
    }
}

class Car : Vehicle {
  @model :String

  public:
    def new(b :String, m :String) {
      super.new(b)  // Call parent constructor
      @model = m
    }
}
```

### Method Overriding

Child classes can override parent methods:

```onion
class Shape {
  public:
    def area :Double {
      0.0
    }

    def describe :String {
      "A shape"
    }
}

class Circle : Shape {
  @radius :Double

  public:
    def new(r :Double) {
      @radius = r
    }

    def area :Double {  // Override
      3.14159 * @radius * @radius
    }

    def describe :String {  // Override
      "A circle with radius " + @radius
    }
}
```

## Implementing Interfaces

### Single Interface

Use `<:` to implement an interface:

```onion
import { java.lang.Comparable; }

class Person <: Comparable {
  @name :String
  @age :Int

  public:
    def new(n :String, a :Int) {
      @name = n
      @age = a
    }

    def compareTo(other :Object) :Int {
      def otherPerson :Person = other$Person
      @age - otherPerson.@age
    }
}
```

### Multiple Interfaces

Implement multiple interfaces with additional `<:` declarations:

```onion
import {
  java.io.Serializable;
  java.lang.Comparable;
}

class Student <: Serializable <: Comparable {
  @id :Int
  @name :String

  public:
    def new(i :Int, n :String) {
      @id = i
      @name = n
    }

    def compareTo(other :Object) :Int {
      def otherStudent :Student = other$Student
      @id - otherStudent.@id
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
  @result :Long

  public:
    def new {
      // JFrame constructor
    }

    def actionPerformed(event :ActionEvent) {
      // Implement ActionListener interface
      IO::println("Button clicked")
    }
}
```

Syntax:
```onion
class Child : ParentClass <: Interface1 <: Interface2 {
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
  forward @internal :List  // Delegate List methods to @internal

  @internal :ArrayList

  public:
    def new {
      @internal = new ArrayList
    }

    // Custom methods
    def addAll(items :String[]) {
      foreach item :String in items {
        @internal.add(item)
      }
    }
}

def list :MyList = new MyList
list << "First"   // Uses delegated add() method
list << "Second"
IO::println(list.size())  // Uses delegated size() method
```

The `forward` directive automatically implements interface methods by forwarding calls to the specified member.

## Polymorphism

### Type Substitution

Child objects can be assigned to parent variables:

```onion
class Animal {
  public:
    def speak :String {
      "Generic sound"
    }
}

class Dog : Animal {
  public:
    def speak :String {
      "Woof!"
    }
}

class Cat : Animal {
  public:
    def speak :String {
      "Meow!"
    }
}

def animals :Animal[] = new Animal[3]
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

def list1 :List = new ArrayList  // ArrayList implements List
def list2 :List = new LinkedList  // LinkedList implements List

def lists :List[] = [list1, list2]
foreach list :List in lists {
  list.add("Item")
  IO::println("Size: " + list.size())
}
```

## Abstract Concepts

### Note on Abstract Methods

Onion currently has limited support for abstract method validation. Abstract methods may not be enforced at compile time:

```onion
// Abstract pattern (not fully enforced)
class AbstractShape {
  public:
    def area :Double {
      // Should be overridden by subclasses
      0.0
    }
}

class Circle : AbstractShape {
  @radius :Double

  public:
    def area :Double {
      3.14159 * @radius * @radius
    }
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
  forward @internal :List
  @internal :ArrayList

  public:
    def new {
      @internal = new ArrayList
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
      value * 2
    }
}

class Child : Parent {
  public:
    def process(value :Int) :Int {
      // Maintain parent's behavior
      if value < 0 {
        return 0
      }
      value * 3  // Different implementation, same contract
    }
}
```

## Next Steps

- [Java Interoperability](java-interop.md) - Working with Java classes
- [Lambda Expressions](lambda-expressions.md) - Functional programming
- [Examples](../examples/oop.md) - Object-oriented examples
