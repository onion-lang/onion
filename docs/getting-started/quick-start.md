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

val result: Int = add(5, 3)  // 8
println(result)
```

### Function with Multiple Parameters

```onion
def multiply(a: Int, b: Int): Int = a * b

println(multiply(6, 7))  // 42
```

## Lists and Maps

```onion
val colors = ["red", "green", "blue"]
val ages = ["alice": 30, "bob": 25]

println(colors[0])        // "red"
println(colors.size)      // 3
println(ages["alice"])    // 30

foreach color: String in colors {
  println(color)
}
```

Pipelines are built in, so you rarely need an index:

```onion
val odds = [1, 2, 3, 4, 5].filter { n -> n % 2 == 1 }   // [1, 3, 5]
val tens = odds.map { n -> n * 10 }                     // [10, 30, 50]
```

The standard library takes and returns lists. Arrays exist for talking to Java —
`main(args: String[])`, `byte[]` — and use `.length` where a list uses `.size`.

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

class BasicLogger conforms Logger {
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
class PrefixLogger conforms Logger {
  forward val delegate: Logger

  public:
    def this(delegate: Logger) {
      this.delegate = delegate
    }
}
```

Syntax:
- `class Child extends Parent` - extends a class
- `class Impl conforms Interface` - implements an interface
- `class Multi extends Parent conforms Interface` - both
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

## Shapes — reading messy data

This is the part of Onion that is not like the languages above. A `shape` clause
describes how text and a record correspond, and both directions come from that one
description:

```onion
record Pt(x: Int, y: Int)
  shape text = re"(-?\d+),(-?\d+)"
  shape doc  = json
  law roundtrip(p: Pt) { Pt::text().parse(Pt::text().print(p)).get() == p }
```

Reading returns an `Outcome` — a value, or **every** reason there is not one:

```onion
val o = Pt::text().parse("3,x")
if o.isOk() { println(o.get().x()) }
else        { println(o.describe()) }
// <input>:1: : expected match of /(-?\d+),(-?\d+)/, found 3,x
```

A failure knows where it came from and what it wanted. `null` cannot tell you either.

And reading many lines keeps the ones that failed instead of dropping them:

```onion
val each = file"points.txt".eachLine(Pt::text())
println(Outcome::values(each).size + " read, " + Outcome::defects(each).size + " not")
```

That `law` is not a comment — the compiler runs it during the build. See the
[Shapes guide](../guide/shapes.md).

## Next Steps

- [Shapes](../guide/shapes.md) - The part that is different: boundaries, `Outcome`, reversibility
- [Language Guide](../guide/overview.md) - Deep dive into language features
- [Examples](../examples/overview.md) - More complete program examples
- [Tools Reference](../tools/compiler.md) - Learn about compiler options
