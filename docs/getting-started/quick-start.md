# Quick Start

This guide covers the essential features of Onion to get you productive quickly.

## Variables and Types

Onion is statically typed. Local `val` / `var` declarations can include a type annotation, or omit it when an initializer is present:

```onion
// Type annotation
val name: String = "Alice"
val age: Int = 30
val price: Double = 19.99

// Type inference (local only)
val count = 42         // Inferred as Int
var greeting = "Hello" // Inferred as String
```

## Control Flow

### If-Else

```onion
val x: Int = 10

if x > 5 {
  println("x is greater than 5")
} else {
  println("x is 5 or less")
}
```

### While Loop

```onion
var i: Int = 0
while i < 5 {
  println(i)
  i = i + 1
}
```

### For Loop

```onion
for var i: Int = 0; i < 10; i = i + 1 {
  println("Count: " + i)
}
```

### Foreach Loop

```onion
val names: java.util.List[String] = ["Alice", "Bob", "Charlie"]
foreach name: String in names {
  println("Hello, " + name)
}
```

### Select Statement (Pattern Matching)

```onion
val score: Int = 85

select score {
  case 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100:
    println("Grade: A")
  case 80, 81, 82, 83, 84, 85, 86, 87, 88, 89:
    println("Grade: B")
  case 70, 71, 72, 73, 74, 75, 76, 77, 78, 79:
    println("Grade: C")
  else:
    println("Grade: F")
}
```

## Functions

### Basic Function

```onion
def greet(name: String): String = "Hello, " + name + "!"

val message: String = greet("World")
println(message)
```

### Lambda Expressions

```onion
// Lambda syntax: (params) -> { body }
val add: (Int, Int) -> Int = (x: Int, y: Int) -> { return x + y; }

val result: Int = add.call(5, 3)  // 8
println(result)
```

### Function with Multiple Parameters

```onion
def multiply(a: Int, b: Int): Int = a * b

println(multiply(6, 7))  // 42
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
  println(color)
}
```

### Lists (ArrayList)

```onion
import {
  java.util.ArrayList;
}

val list: ArrayList[String] = new ArrayList[String]()
list << "First"   // << is the append operator
list << "Second"
list << "Third"

println(list.size)  // 3
println(list[0])     // "First"
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
println(person.greet)
```

Key points:
- Fields are accessed via `this.field`
- Members are private by default
- Use `public:` to mark public members
- `def this` declares a constructor

### Inheritance

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

// `forward` auto-implements the Logger interface by delegating to `delegate`.
class PrefixLogger <: Logger {
  forward val delegate: Logger

  public:
    def this(delegate: Logger) {
      this.delegate = delegate
    }
}
```

Syntax:
- `class Child : Parent` - extends a class
- `class Impl <: Interface` - implements an interface
- `class Multi : Parent <: Interface` - both
- `forward val m: Interface` - auto-implement `Interface` by delegating to `m`

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
  println("File exists!")
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
  println(value)
} catch e :NumberFormatException {
  println("Invalid number format: " + e.getMessage)
}
```

## Type Casting

Use the `as` keyword for type casting. When chaining with method calls, use parentheses:

```onion
val random: Double = Math::random()
val randomInt: Int = (random * 100) as Int

val obj: Object = "Hello"
val str: String = obj as String

// When chaining method calls, parentheses are required
val btn: Object = getButton()
val text: String = (btn as JButton).getText()
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
val list: java.util.ArrayList[String] = new java.util.ArrayList[String]()
```

## Bidirectional Records

A record can derive both directions of a boundary from a single declaration, and
the compiler can check invariants at build time:

```onion
record Pt(x: Int, y: Int) from re"(-?\d+),(-?\d+)" derive!(Json)
  law roundtrip(p: Pt) { Pt::parse(Pt::format(p)) == p }
  example { Pt::parse("3,4") == new Pt(3, 4) }
```

`from re"..."` derives `parse` / `format`, `derive!(Json, Yaml)` derives
`fromJson` / `toJson` / `fromYaml` / `toYaml`, and the `law` / `example` clauses
are executed by the compiler so a broken round-trip fails the build. See the
[Scripting guide](../guide/scripting.md).

## Next Steps

- [Language Guide](../guide/overview.md) - Deep dive into language features
- [Examples](../examples/overview.md) - More complete program examples
- [Tools Reference](../tools/compiler.md) - Learn about compiler options
