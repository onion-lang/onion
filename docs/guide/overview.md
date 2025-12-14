# Language Overview

Onion is a statically-typed, object-oriented programming language designed for the Java Virtual Machine (JVM). This page provides an overview of the language's philosophy, design goals, and key characteristics.

## Design Philosophy

Onion was created with several goals in mind:

1. **Static Type Safety** - Catch errors at compile time while maintaining expressiveness
2. **Java Interoperability** - Seamless integration with existing Java libraries and frameworks
3. **Concise Syntax** - Reduce boilerplate while keeping code readable
4. **Familiar Concepts** - Build on established OOP and functional programming patterns
5. **JVM Performance** - Leverage the mature JVM ecosystem and runtime optimizations

## Language Characteristics

### Statically Typed

Every variable and expression has a type known at compile time:

```onion
def name :String = "Alice"  // Type annotation
def age :Int = 30
def scores :Int[] = new Int[10]
```

The type system includes:
- Primitive types: `Int`, `Long`, `Double`, `Float`, `Boolean`, `Byte`, `Short`, `Char`
- Reference types: Classes and interfaces
- Array types: `Type[]`
- Null type: Special handling for `null` values

### Object-Oriented

Onion fully supports object-oriented programming:

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
      // Call parent constructor
      super.new(n)
    }

    def speak :String {
      "Woof!"
    }
}
```

Features:
- **Classes** - Encapsulation of data and behavior
- **Inheritance** - Single class inheritance, multiple interface implementation
- **Polymorphism** - Method overriding and overloading
- **Access Control** - Public/private visibility
- **Interfaces** - Abstract contracts

### Functional Elements

While primarily object-oriented, Onion includes functional programming features:

```onion
// Lambda expressions
def filter = (x :Int) -> { return x > 10; }

// Closures
def makeCounter {
  def count :Int = 0
  () -> {
    count = count + 1;
    return count;
  }
}

def counter = makeCounter()
IO::println(counter.call())  // 1
IO::println(counter.call())  // 2
```

Features:
- **Lambda expressions** - Anonymous functions with `(params) -> { body }` syntax
- **Closures** - Functions that capture variables from their enclosing scope
- **First-class functions** - Functions as values via `Function0` through `Function10` interfaces

### JVM Target

Onion compiles directly to JVM bytecode:

- Compiled `.class` files are standard JVM classes
- Can be packaged in JARs alongside Java classes
- Inherits JVM's performance characteristics
- Access to the entire Java ecosystem

### Java Interoperability

Direct, seamless access to Java:

```onion
import {
  java.util.ArrayList;
  java.util.HashMap;
  javax.swing.JFrame;
}

def list :ArrayList = new ArrayList
def map :HashMap = new HashMap
def window :JFrame = new JFrame("Title")
```

Key points:
- Import Java classes with `import { }`
- Instantiate Java objects with `new`
- Call Java methods normally
- Implement Java interfaces
- Extend Java classes
- Use `::` for static method access

## Compilation Model

The Onion compiler follows a multi-phase architecture:

```
Source Code (.on)
    ↓
[Parsing] - JavaCC grammar → Untyped AST
    ↓
[Rewriting] - Normalization → Transformed AST
    ↓
[Type Checking] - Type inference & validation → Typed AST
    ↓
[Code Generation] - ASM bytecode generation → .class files
```

### Compilation Modes

1. **File Compilation** (`onionc`) - Produces `.class` files
2. **Script Execution** (`onion`) - Compiles to memory and runs immediately
3. **Interactive REPL** (`Shell`) - Evaluate expressions interactively

## Syntax Highlights

### Member Variables with `@`

Instance variables are prefixed with `@`:

```onion
class Counter {
  @count :Int

  public:
    def increment {
      @count = @count + 1
    }
}
```

### Type Annotations with `:`

Types are specified after a colon:

```onion
def variable :Type = value
def method(param :Type) :ReturnType { }
```

### Static Access with `::`

Static methods and fields use `::`:

```onion
IO::println("Hello")
Math::random()
System::out.println("Java style")
```

### Type Casting with `$`

Cast expressions use the `$` operator:

```onion
def x :Double = 3.14
def y :Int = x$Int  // Cast to Int

def obj :Object = "string"
def str :String = obj$String  // Cast to String
```

### Pattern Matching with `select`

Switch-style pattern matching:

```onion
select value {
  case 1, 2, 3:
    IO::println("Small")
  case 4, 5, 6:
    IO::println("Medium")
  else:
    IO::println("Large")
}
```

## What's Different from Java?

| Feature | Java | Onion |
|---------|------|-------|
| Instance variables | `this.field` | `@field` |
| Type annotations | `Type variable` | `variable :Type` |
| Static access | `Class.method()` | `Class::method()` |
| Type casting | `(Type) value` | `value$Type` |
| Lambda syntax | `(x) -> x + 1` | `(x :Int) -> { return x + 1; }` |
| Pattern matching | `switch` (Java 14+) | `select` |
| List append | `list.add(x)` | `list << x` |

## Current Limitations

As documented in the README:

1. **Incomplete validation** - Some compile-time checks (abstract method implementation, final method overriding) are not fully implemented
2. **Edge cases** - The compiler may crash on certain code patterns
3. **Partial features** - Some features like `finally` clauses are not yet supported

The examples in the `run/` directory are verified to compile and execute correctly.

## Next Steps

- [Basic Syntax](basic-syntax.md) - Learn the fundamentals
- [Classes and Objects](classes-and-objects.md) - Object-oriented programming
- [Java Interoperability](java-interop.md) - Working with Java libraries
