# Quick Start

This guide covers the essential features of Onion to get you productive quickly.

## Variables and Types

Onion is statically typed and requires explicit type annotations for `val` / `var` declarations:

```onion
// Type annotation
val name: String = "Alice"
val age: Int = 30
val price: Double = 19.99

// Type inference (coming in future versions)
val count = 42  // Inferred as Int
```

## Control Flow

### If-Else

```onion
val x: Int = 10

if x > 5 {
  IO::println("x is greater than 5")
} else {
  IO::println("x is 5 or less")
}
```

### While Loop

```onion
var i: Int = 0
while i < 5 {
  IO::println(i)
  i = i + 1
}
```

### For Loop

```onion
for var i: Int = 0; i < 10; i = i + 1 {
  IO::println("Count: " + i)
}
```

### Foreach Loop

```onion
val names: java.util.List = ["Alice", "Bob", "Charlie"]
foreach name: String in names {
  IO::println("Hello, " + name)
}
```

### Select Statement (Pattern Matching)

```onion
val score: Int = 85

select score {
  case 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100:
    IO::println("Grade: A")
  case 80, 81, 82, 83, 84, 85, 86, 87, 88, 89:
    IO::println("Grade: B")
  case 70, 71, 72, 73, 74, 75, 76, 77, 78, 79:
    IO::println("Grade: C")
  else:
    IO::println("Grade: F")
}
```

## Functions

### Basic Function

```onion
def greet(name: String): String = "Hello, " + name + "!"

val message: String = greet("World")
IO::println(message)
```

### Lambda Expressions

```onion
// Lambda syntax: (params) -> { body }
val add: (Int, Int) -> Int = (x: Int, y: Int) -> { return x + y; }

val result: Int = add.call(5, 3)  // 8
IO::println(result)
```

### Function with Multiple Parameters

```onion
def multiply(a: Int, b: Int): Int = a * b

IO::println(multiply(6, 7))  // 42
```

## Arrays and Collections

### Arrays

```onion
// Fixed-size array
val numbers: Int[] = new Int[5]
numbers[0] = 10
numbers[1] = 20

val colors: String[] = new String[3]
colors[0] = "red"
colors[1] = "green"
colors[2] = "blue"

// Iterate over array
foreach color :String in colors {
  IO::println(color)
}
```

### Lists (ArrayList)

```onion
import {
  java.util.ArrayList;
}

val list: ArrayList = new ArrayList
list << "First"   // << is the append operator
list << "Second"
list << "Third"

IO::println(list.size)  // 3
IO::println(list[0])     // "First"
```

## Classes and Objects

### Basic Class

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

val person: Person = new Person("Alice", 30)
IO::println(person.greet)
```

Key points:
- Fields are accessed via `this.field`
- Members are private by default
- Use `public:` to mark public members
- `def this` declares a constructor

### Inheritance

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
}
```

Syntax:
- `class Child : Parent` - extends a class
- `class Impl <: Interface` - implements an interface
- `class Multi : Parent <: Interface` - both

## Java Interoperability

Onion has seamless access to Java libraries:

```onion
import {
  java.io.File;
  javax.swing.JFrame;
  javax.swing.JButton;
}

val frame: JFrame = new JFrame("My Window")
frame.setSize(400, 300)
frame.setVisible(true)

val file: File = new File("data.txt")
if file.exists {
  IO::println("File exists!")
}
```

### Common Java Classes

```onion
// Math operations
val random: Double = Math::random()
val sqrt: Double = Math::sqrt(16.0)

// String operations
val upper: String = "hello".toUpperCase()
val length: Int = "hello".length

// Parsing
val num: Int = JInteger::parseInt("42")
```

## Exception Handling

```onion
try {
  val value: Int = JInteger::parseInt("not a number")
  IO::println(value)
} catch e :NumberFormatException {
  IO::println("Invalid number format: " + e.getMessage)
}
```

## Type Casting

Use the `$` operator for type casting:

```onion
val random: Double = Math::random()
val randomInt: Int = (random * 100)$Int

val obj: Object = "Hello"
val str: String = obj$String
```

## Module System

```onion
// Import Java classes
import {
  java.util.ArrayList;
  java.util.HashMap;
  java.io.File;
}

// Use fully qualified names without import
val list: java.util.ArrayList = new java.util.ArrayList
```

## Next Steps

- [Language Guide](../guide/overview.md) - Deep dive into language features
- [Examples](../examples/overview.md) - More complete program examples
- [Tools Reference](../tools/compiler.md) - Learn about compiler options
