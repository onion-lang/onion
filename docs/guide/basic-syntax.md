# Basic Syntax

This guide covers the fundamental syntax elements of the Onion programming language.

## Comments

Currently, Onion supports C-style comments:

```onion
// Single-line comment

/*
 * Multi-line comment
 * Spans multiple lines
 */
```

## Variables

### Variable Declaration

Variables are declared with `val` (immutable) or `var` (mutable). Local declarations can omit the type when an initializer is present; otherwise provide an explicit type:

```onion
val name = "Alice"
var count = 0
val age: Int = 30
val price: Double = 19.99
val isActive: Boolean = true
```

Assignments update existing variables; they do not declare new ones.

### Naming Conventions

- Variable names use camelCase: `firstName`, `totalCount`
- Class names use PascalCase: `Person`, `Calculator`
- Fields are accessed via `this.field`

## Primitive Types

Onion supports the standard JVM primitive types:

| Type | Description | Example |
|------|-------------|---------|
| `Int` | 32-bit integer | `42` |
| `Long` | 64-bit integer | `42L` |
| `Double` | 64-bit floating point | `3.14` |
| `Float` | 32-bit floating point | `3.14f` |
| `Boolean` | true or false | `true`, `false` |
| `Char` | Single character | `'A'` |
| `Byte` | 8-bit integer | `127` |
| `Short` | 16-bit integer | `1000` |

## Literals

### String Literals

```onion
val greeting: String = "Hello, World!"
val multiline: String = "Line 1\nLine 2\nLine 3"
val withQuotes: String = "She said \"Hello\""
```

### Numeric Literals

```onion
val decimal: Int = 42
val hex: Int = 0xFF
val binary: Int = 0b1010
val octal: Int = 077
val longValue: Long = 100L
val byteValue: Byte = 5B
val shortValue: Short = 5S
val doubleValue: Double = 3.14
val floatValue: Float = 3.14f
val scientific: Double = 1.23e10
```

Underscores group digits in any numeric literal:

```onion
val million = 1_000_000
val mask = 0xFF_FF
val flags = 0b1010_1010
val precise = 1_234.5
```

### Character Literals

```onion
val letter: Char = 'A'
val newline: Char = '\n'
val tab: Char = '\t'
```

### Boolean Literals

```onion
val isTrue: Boolean = true
val isFalse: Boolean = false
```

### Null Literal

```onion
val nullable: String? = null   // null needs a nullable type (W0012 otherwise)
```

## Operators

### Arithmetic Operators

```onion
val a: Int = 10
val b: Int = 3

val sum: Int = a + b        // 13
val diff: Int = a - b       // 7
val product: Int = a * b    // 30
val quotient: Int = a / b   // 3
val remainder: Int = a % b  // 1
```

### Comparison Operators

```onion
val x: Int = 5
val y: Int = 10

x == y  // false (equal)
x != y  // true  (not equal)
x < y   // true  (less than)
x > y   // false (greater than)
x <= y  // true  (less than or equal)
x >= y  // false (greater than or equal)
```

### Logical Operators

```onion
val a: Boolean = true
val b: Boolean = false

a && b  // false (logical AND)
a || b  // true  (logical OR)
!a      // false (logical NOT)
```

### Assignment Operators

```onion
var x: Int = 10

x = 20       // Simple assignment
x = x + 5    // Add and assign (no += syntax yet)
x = x - 3    // Subtract and assign
x = x * 2    // Multiply and assign
```

### Increment/Decrement

```onion
var count: Int = 0
count = count + 1  // Increment
// Or use post-increment (in some contexts)
count++
count--
```

### Type Casting Operator

The `as` keyword performs type casting. When chaining with method calls, use parentheses:

```onion
val x: Double = 3.14
val y: Int = x as Int  // Cast to Int (3)

val random: Double = Math::random()
val randomInt: Int = (random * 100) as Int

// When chaining method calls, parentheses are required
val btn: Object = getButton()
val text: String = (btn as JButton).getText()
```

### List Append Operator

The `<<` operator appends to lists:

```onion
import { java.util.ArrayList; }

val list: ArrayList[String] = new ArrayList[String]()
list << "First"
list << "Second"
list << "Third"
```

## Lists and Maps

Write a list between square brackets. This is what you will use most of the time — the
standard library takes and returns lists, never arrays.

```onion
val colors = ["red", "green", "blue"]
val scores = [95.5, 87.3, 91.0]
val empty: List[String] = []          // annotate when there is nothing to infer from
```

Read and write elements with `[]`, and ask for the size with `.size`:

```onion
val first = colors[0]      // "red"
val n = colors.size        // 3
```

A map literal pairs keys with values:

```onion
val ages = ["alice": 30, "bob": 25]
val lookup = ages["alice"]            // 30
val nothing: Map[String, Int] = [:]   // the empty map
```

Lists come with pipelines built in, so you rarely need an index at all:

```onion
val odds = [1, 2, 3, 4, 5].filter { n => n % 2 == 1 }   // [1, 3, 5]
val tens = odds.map { n => n * 10 }                     // [10, 30, 50]
```

See [Collections](collections.md) for the full set.

### Arrays

Arrays exist, but you need them in one place: talking to Java. `main(args: String[])`,
`byte[]` for binary I/O, and varargs are the usual cases.

```onion
val buffer: String[] = new String[3]
buffer[0] = "a"
val length: Int = buffer.length   // .length, not .size — arrays are the Java type
```

`Colls::toList(args)` crosses over from an array into list-land when you want the
pipelines back.

## Expressions

### Arithmetic Expressions

```onion
val result: Int = (10 + 5) * 2 - 3  // 27
val average: Double = (10 + 20 + 30) / 3.0
```

### String Concatenation

```onion
val firstName: String = "Alice"
val lastName: String = "Smith"
val fullName: String = firstName + " " + lastName

val age: Int = 30
val message: String = "I am " + age + " years old"
```

### Method Calls

```onion
// Static method call
println("Hello")

// Instance method call
val text: String = "hello"
val upper: String = text.toUpperCase()
val len: Int = text.length
```

### Object Creation

```onion
import { java.util.ArrayList; }

val list: ArrayList[String] = new ArrayList[String]()
val array: String[] = new String[10]
```

## Block Elements

### Expressions In Blocks

Any expression can appear directly inside a block:

```onion
println("Hello")
var x: Int = 10
x = x + 1
```

### Block Expressions

Blocks are enclosed in curly braces:

```onion
{
  val temp: Int = 5
  println(temp)
  // temp is only visible in this block
}
```

## Scope and Visibility

### Local Variables

Variables declared in a method or block are local to that scope:

```onion
def method {
  val local: Int = 10
  // local is only visible here
}
```

### Fields

Fields are declared with `val` / `var` and accessed via `this.field`:

```onion
class Example {
  var count: Int
  val name: String = "default"

  public:
    def increment {
      this.count = this.count + 1
    }
}
```

### Access Modifiers

By default, members are private. Use `public:` to mark public members:

```onion
class Person {
  val ssn: String = "000-00-0000"  // Private by default

  public:
    val name: String = "Alice"  // Public

    def getName: String = this.name  // Public method
}
```

## Interfaces

An interface declares method signatures without bodies. A whole interface
can sit on a single line, and a no-body method may be followed by the
closing `}` on the same line:

```onion
interface Shape { def area(): Double }

class Circle extends Object conforms Shape {
  val r: Double
public:
  def this(r: Double) { this.r = r }
  override def area(): Double = 3.14 * this.r * this.r
}

val s: Shape = new Circle(2.0)
println(s.area())   // 12.56
```

## Module System

### Import Statements

Import Java classes for use in your Onion code:

```onion
import {
  java.util.ArrayList;
  java.util.HashMap;
  java.io.File;
}

val list: ArrayList[String] = new ArrayList[String]()
val map: HashMap[String, String] = new HashMap[String, String]()
val file: File = new File("data.txt")
```

### Fully Qualified Names

You can also use fully qualified names without importing:

```onion
val list: java.util.ArrayList[String] = new java.util.ArrayList[String]()
```

## Next Steps

- [Variables and Types](variables-and-types.md) - Deep dive into the type system
- [Control Flow](control-flow.md) - Conditionals and loops
- [Functions](functions.md) - Function definitions and lambdas
