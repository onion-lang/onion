# Classes and Objects

Onion is an object-oriented language with full support for classes, inheritance, and interfaces.

## Class Definition

### Basic Class

Define a class with the `class` keyword:

```onion
class Person {
  @name :String
  @age :Int

  public:
    def new(n :String, a :Int) {
      @name = n
      @age = a
    }

    def greet :String {
      "Hello, I'm " + @name
    }
}
```

### Creating Objects

Instantiate objects with the `new` keyword:

```onion
def person :Person = new Person("Alice", 30)
IO::println(person.greet())  // "Hello, I'm Alice"
```

## Member Variables

### Instance Variables

Instance variables are prefixed with `@`:

```onion
class Counter {
  @count :Int

  public:
    def new {
      @count = 0
    }

    def increment {
      @count = @count + 1
    }

    def getCount :Int {
      @count
    }
}
```

### Access Modifiers

Members are **private by default**. Use `public:` to mark public members:

```onion
class BankAccount {
  @balance :Double  // Private (default)
  @accountNumber :String  // Private

  public:
    @owner :String  // Public

    def new(owner :String, initial :Double) {
      @owner = owner
      @balance = initial
    }

    def deposit(amount :Double) {  // Public method
      @balance = @balance + amount
    }

    def getBalance :Double {  // Public method
      @balance
    }
}
```

### Static Members

Static members belong to the class, not instances:

```onion
class MathUtils {
  static @PI :Double = 3.14159

  public:
    static def square(x :Double) :Double {
      x * x
    }

    static def circleArea(radius :Double) :Double {
      MathUtils::PI * radius * radius
    }
}

// Access static members with ::
def pi :Double = MathUtils::PI
def area :Double = MathUtils::circleArea(5.0)
```

## Constructors

### Default Constructor

Define constructors with `def new`:

```onion
class Point {
  @x :Int
  @y :Int

  public:
    def new(x :Int, y :Int) {
      @x = x
      @y = y
    }
}

def point :Point = new Point(10, 20)
```

### Multiple Constructors

Overload constructors for different initialization patterns:

```onion
class Rectangle {
  @width :Int
  @height :Int

  public:
    def new {
      @width = 0
      @height = 0
    }

    def new(size :Int) {
      @width = size
      @height = size
    }

    def new(w :Int, h :Int) {
      @width = w
      @height = h
    }
}

def rect1 :Rectangle = new Rectangle()
def rect2 :Rectangle = new Rectangle(10)
def rect3 :Rectangle = new Rectangle(10, 20)
```

### Named Constructors

Use special syntax for named constructors:

```onion
class Window {
  @title :String

  public:
    def this:(title :String) {
      @title = title
    }
}

def window :Window = new Window("My Window")
```

## Methods

### Instance Methods

Methods that operate on instance data:

```onion
class Circle {
  @radius :Double

  public:
    def new(r :Double) {
      @radius = r
    }

    def area :Double {
      3.14159 * @radius * @radius
    }

    def circumference :Double {
      2.0 * 3.14159 * @radius
    }
}

def circle :Circle = new Circle(5.0)
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

def printer :Printer = new Printer
printer.print(42)
printer.print("Hello")
printer.print(3.14)
```

### Getter and Setter Methods

```onion
class Person {
  @name :String
  @age :Int

  public:
    def getName :String {
      @name
    }

    def setName(name :String) {
      @name = name
    }

    def getAge :Int {
      @age
    }

    def setAge(age :Int) {
      if age >= 0 {
        @age = age
      }
    }
}
```

## The `self` Reference

Access the current instance with `self`:

```onion
import {
  javax.swing.JButton;
  java.awt.event.ActionListener;
}

class ButtonHandler <: ActionListener {
  public:
    def actionPerformed(event :ActionEvent) {
      def button :JButton = event.getSource()$JButton
      button.addActionListener(self)  // Reference to this instance
    }
}
```

## Next Steps

- [Inheritance](inheritance.md) - Extending classes and implementing interfaces
- [Java Interoperability](java-interop.md) - Working with Java classes
- [Examples](../examples/oop.md) - Object-oriented examples
