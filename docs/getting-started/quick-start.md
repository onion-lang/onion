# Quick Start

This guide covers the essential features of Onion to get you productive quickly.

## Variables and Types

Onion is statically typed with type inference:

```onion
// Type annotation
def name :String = "Alice"
def age :Int = 30
def price :Double = 19.99

// Type inference (coming in future versions)
def count = 42  // Inferred as Int
```

## Control Flow

### If-Else

```onion
def x :Int = 10

if x > 5 {
  IO::println("x is greater than 5")
} else {
  IO::println("x is 5 or less")
}
```

### While Loop

```onion
def i :Int = 0
while i < 5 {
  IO::println(i)
  i = i + 1
}
```

### For Loop

```onion
for i = 0; i < 10; i = i + 1 {
  IO::println("Count: " + i)
}
```

### Foreach Loop

```onion
def names :String[] = ["Alice", "Bob", "Charlie"]
foreach name :String in names {
  IO::println("Hello, " + name)
}
```

### Select Statement (Pattern Matching)

```onion
def score :Int = 85

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
def greet(name :String) :String {
  "Hello, " + name + "!"
}

def message :String = greet("World")
IO::println(message)
```

### Lambda Expressions

```onion
// Lambda syntax: #(params) { body }
def add = #(x :Int, y :Int) { x + y }

def result :Int = add.call(5, 3)  // 8
IO::println(result)
```

### Function with Multiple Parameters

```onion
def multiply(a :Int, b :Int) :Int {
  a * b
}

IO::println(multiply(6, 7))  // 42
```

## Arrays and Collections

### Arrays

```onion
// Fixed-size array
def numbers :Int[] = new Int[5]
numbers[0] = 10
numbers[1] = 20

// Array literal
def colors :String[] = ["red", "green", "blue"]

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

def list :ArrayList = new ArrayList
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

def person :Person = new Person("Alice", 30)
IO::println(person.greet)
```

Key points:
- `@name` denotes instance variables (members)
- Members are private by default
- Use `public:` to mark public members
- `def new` is the constructor

### Inheritance

```onion
import {
  java.util.List;
  java.util.ArrayList;
}

class MyList <: List {
  @internal :ArrayList

  public:
    def new {
      @internal = new ArrayList
    }

    def add(item :Object) :Boolean {
      @internal.add(item)
    }

    def size :Int {
      @internal.size
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

def frame :JFrame = new JFrame("My Window")
frame.setSize(400, 300)
frame.setVisible(true)

def file :File = new File("data.txt")
if file.exists {
  IO::println("File exists!")
}
```

### Common Java Classes

```onion
// Math operations
def random :Double = Math::random()
def sqrt :Double = Math::sqrt(16.0)

// String operations
def upper :String = "hello".toUpperCase()
def length :Int = "hello".length

// Parsing
def num :Int = JInteger::parseInt("42")
```

## Exception Handling

```onion
try {
  def value :Int = JInteger::parseInt("not a number")
  IO::println(value)
} catch e :NumberFormatException {
  IO::println("Invalid number format: " + e.getMessage)
}
```

## Type Casting

Use the `$` operator for type casting:

```onion
def random :Double = Math::random()
def randomInt :Int = (random * 100)$Int

def obj :Object = "Hello"
def str :String = obj$String
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
def list :java.util.ArrayList = new java.util.ArrayList
```

## Next Steps

- [Language Guide](../guide/overview.md) - Deep dive into language features
- [Examples](../examples/overview.md) - More complete program examples
- [Tools Reference](../tools/compiler.md) - Learn about compiler options
