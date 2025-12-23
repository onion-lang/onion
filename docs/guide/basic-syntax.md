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
val octal: Int = 077
val longValue: Long = 100L
val doubleValue: Double = 3.14
val scientific: Double = 1.23e10
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
val nullable: String = null
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

The `$` operator performs type casting:

```onion
val x: Double = 3.14
val y: Int = x$Int  // Cast to Int (3)

val random: Double = Math::random()
val randomInt: Int = (random * 100)$Int
```

### List Append Operator

The `<<` operator appends to lists:

```onion
import { java.util.ArrayList; }

val list: ArrayList = new ArrayList
list << "First"
list << "Second"
list << "Third"
```

## Arrays

### Array Declaration

```onion
// Create array with size
val numbers: Int[] = new Int[10]

// Initialize elements
val colors: String[] = new String[3]
colors[0] = "red"
colors[1] = "green"
colors[2] = "blue"

val scores: Double[] = new Double[5]
scores[0] = 95.5
scores[1] = 87.3
```

### Array Access

```onion
val fruits: String[] = new String[3]
fruits[0] = "apple"
fruits[1] = "banana"
fruits[2] = "orange"

val first: String = fruits[0]     // "apple"
val second: String = fruits[1]    // "banana"

fruits[2] = "grape"  // Modify element

val length: Int = fruits.length  // Array length
```

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
IO::println("Hello")

// Instance method call
val text: String = "hello"
val upper: String = text.toUpperCase()
val len: Int = text.length
```

### Object Creation

```onion
import { java.util.ArrayList; }

val list: ArrayList = new ArrayList
val array: String[] = new String[10]
```

## Statements

### Expression Statements

Any expression can be a statement:

```onion
IO::println("Hello")
var x: Int = 10
x = x + 1
```

### Block Statements

Blocks are enclosed in curly braces:

```onion
{
  val temp: Int = 5
  IO::println(temp)
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

## Module System

### Import Statements

Import Java classes for use in your Onion code:

```onion
import {
  java.util.ArrayList;
  java.util.HashMap;
  java.io.File;
}

val list: ArrayList = new ArrayList
val map: HashMap = new HashMap
val file: File = new File("data.txt")
```

### Fully Qualified Names

You can also use fully qualified names without importing:

```onion
val list: java.util.ArrayList = new java.util.ArrayList
```

## Next Steps

- [Variables and Types](variables-and-types.md) - Deep dive into the type system
- [Control Flow](control-flow.md) - Conditionals and loops
- [Functions](functions.md) - Function definitions and lambdas
