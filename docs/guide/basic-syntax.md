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

Variables are declared with the `def` keyword and type annotations:

```onion
def name :String = "Alice"
def age :Int = 30
def price :Double = 19.99
def isActive :Boolean = true
```

### Naming Conventions

- Variable names use camelCase: `firstName`, `totalCount`
- Class names use PascalCase: `Person`, `Calculator`
- Member variables are prefixed with `@`: `@name`, `@balance`

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
def greeting :String = "Hello, World!"
def multiline :String = "Line 1\nLine 2\nLine 3"
def withQuotes :String = "She said \"Hello\""
```

### Numeric Literals

```onion
def decimal :Int = 42
def hex :Int = 0xFF
def octal :Int = 077
def longValue :Long = 100L
def doubleValue :Double = 3.14
def scientific :Double = 1.23e10
```

### Character Literals

```onion
def letter :Char = 'A'
def newline :Char = '\n'
def tab :Char = '\t'
```

### Boolean Literals

```onion
def isTrue :Boolean = true
def isFalse :Boolean = false
```

### Null Literal

```onion
def nullable :String = null
```

## Operators

### Arithmetic Operators

```onion
def a :Int = 10
def b :Int = 3

def sum :Int = a + b        // 13
def diff :Int = a - b       // 7
def product :Int = a * b    // 30
def quotient :Int = a / b   // 3
def remainder :Int = a % b  // 1
```

### Comparison Operators

```onion
def x :Int = 5
def y :Int = 10

x == y  // false (equal)
x != y  // true  (not equal)
x < y   // true  (less than)
x > y   // false (greater than)
x <= y  // true  (less than or equal)
x >= y  // false (greater than or equal)
```

### Logical Operators

```onion
def a :Boolean = true
def b :Boolean = false

a && b  // false (logical AND)
a || b  // true  (logical OR)
!a      // false (logical NOT)
```

### Assignment Operators

```onion
def x :Int = 10

x = 20       // Simple assignment
x = x + 5    // Add and assign (no += syntax yet)
x = x - 3    // Subtract and assign
x = x * 2    // Multiply and assign
```

### Increment/Decrement

```onion
def count :Int = 0
count = count + 1  // Increment
// Or use post-increment (in some contexts)
count++
count--
```

### Type Casting Operator

The `$` operator performs type casting:

```onion
def x :Double = 3.14
def y :Int = x$Int  // Cast to Int (3)

def random :Double = Math::random()
def randomInt :Int = (random * 100)$Int
```

### List Append Operator

The `<<` operator appends to lists:

```onion
import { java.util.ArrayList; }

def list :ArrayList = new ArrayList
list << "First"
list << "Second"
list << "Third"
```

## Arrays

### Array Declaration

```onion
// Create array with size
def numbers :Int[] = new Int[10]

// Array literal
def colors :String[] = ["red", "green", "blue"]

// Mixed approach
def scores :Double[] = new Double[5]
scores[0] = 95.5
scores[1] = 87.3
```

### Array Access

```onion
def fruits :String[] = ["apple", "banana", "orange"]

def first :String = fruits[0]     // "apple"
def second :String = fruits[1]    // "banana"

fruits[2] = "grape"  // Modify element

def length :Int = fruits.length  // Array length
```

## Expressions

### Arithmetic Expressions

```onion
def result :Int = (10 + 5) * 2 - 3  // 27
def average :Double = (a + b + c) / 3.0
```

### String Concatenation

```onion
def firstName :String = "Alice"
def lastName :String = "Smith"
def fullName :String = firstName + " " + lastName

def age :Int = 30
def message :String = "I am " + age + " years old"
```

### Method Calls

```onion
// Static method call
IO::println("Hello")

// Instance method call
def text :String = "hello"
def upper :String = text.toUpperCase()
def len :Int = text.length
```

### Object Creation

```onion
import { java.util.ArrayList; }

def list :ArrayList = new ArrayList
def array :String[] = new String[10]
```

## Statements

### Expression Statements

Any expression can be a statement:

```onion
IO::println("Hello")
def x :Int = 10
x = x + 1
```

### Block Statements

Blocks are enclosed in curly braces:

```onion
{
  def temp :Int = 5
  IO::println(temp)
  // temp is only visible in this block
}
```

## Scope and Visibility

### Local Variables

Variables declared in a method or block are local to that scope:

```onion
def method {
  def local :Int = 10
  // local is only visible here
}
```

### Member Variables

Member variables are prefixed with `@` and belong to the class instance:

```onion
class Example {
  @count :Int
  @name :String

  public:
    def increment {
      @count = @count + 1
    }
}
```

### Access Modifiers

By default, members are private. Use `public:` to mark public members:

```onion
class Person {
  @ssn :String  // Private by default

  public:
    @name :String  // Public

    def getName :String {  // Public method
      @name
    }
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

def list :ArrayList = new ArrayList
def map :HashMap = new HashMap
def file :File = new File("data.txt")
```

### Fully Qualified Names

You can also use fully qualified names without importing:

```onion
def list :java.util.ArrayList = new java.util.ArrayList
```

## Next Steps

- [Variables and Types](variables-and-types.md) - Deep dive into the type system
- [Control Flow](control-flow.md) - Conditionals and loops
- [Functions](functions.md) - Function definitions and lambdas
