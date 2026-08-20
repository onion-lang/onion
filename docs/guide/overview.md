# Language Overview

Onion is a statically typed language for turning messy external data into checked,
reversible tools. It runs on the JVM and calls Java directly.

If you have written Java, Kotlin or Scala, most of this page will feel familiar — classes,
interfaces, generics, lambdas all work the way you expect. This page covers that ground
quickly so you can get to the part that is different.

## What Onion is for

Most languages hand you a `String` at the boundary — a log line, a JSON body, a
command-line argument — and leave the rest to you. Onion asks you to describe that
boundary once, and derives the reading, the writing, the failure reporting and the CLI
from that one description.

```onion
record Access(ip: String, method: String, path: String, status: Int)
  shape common = re"(\S+) (\w+) (\S+) (\d+)"

val each = file"access.log".eachLine(Access::common())
val rows = Outcome::values(each)      // the lines that read
val bad  = Outcome::defects(each)     // the ones that didn't, with line numbers
```

A thousand-line log with five corrupted lines gives you 995 rows **and** the five you
could not read. Most tools give you 995 rows and no sign the other five existed.

That idea is covered in [Shapes](shapes.md). Everything below is the ordinary language
those shapes are built out of.

## Design goals

1. **Honest boundaries** - a failure to read external data is a value that says where and why
2. **Static type safety** - catch errors at compile time while staying concise
3. **Java interoperability** - use any Java library without a wrapper
4. **Checked at build time** - `law` and `example` clauses run during compilation
5. **JVM performance** - a mature runtime, no separate VM to install

## Language Characteristics

### Statically Typed

Every variable and expression has a type known at compile time:

```onion
val name = "Alice"          // inferred as String
val age: Int = 30
val scores = [95, 87, 91]   // inferred as List[Int]
```

The type system includes:
- Primitive types: `Int`, `Long`, `Double`, `Float`, `Boolean`, `Byte`, `Short`, `Char`
- Reference types: Classes and interfaces
- Array types: `Type[]`
- Null type: Special handling for `null` values
- Bottom type: `Nothing` for non-returning expressions

### Object-Oriented

Onion fully supports object-oriented programming:

```onion
class Animal {
  val name: String

  public:
    def this(n: String) {
      this.name = n
    }

    def speak: String {
      return "Some sound"
    }
}

class Dog(n: String) extends Animal(n) {
  public:
    def speak: String {
      return "Woof!"
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
val filter: (Int) -> Boolean = (x: Int) -> { return x > 10; }

// Closures
def makeCounter(): () -> Int {
  var count: Int = 0
  return () -> {
    count = count + 1;
    return count;
  };
}

val counter: () -> Int = makeCounter()
println(counter())  // 1
println(counter())  // 2
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

val list: ArrayList[String] = new ArrayList[String]()
val map: HashMap[String, String] = new HashMap[String, String]()
val window: JFrame = new JFrame("Title")
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

### Fields with `val` / `var`

Declare fields with `val` (immutable) or `var` (mutable) and access them via `this.field`:

```onion
class Counter {
  var count: Int

  public:
    def increment {
      this.count = this.count + 1
    }
}
```

### Type Annotations with `:`

Types are specified after a colon. Local declarations can omit the type when an initializer is present:

```onion
val variable: Type = value
val inferred = value
def method(param: Type): ReturnType { }
```

### Static Access with `::`

Static methods and fields use `::`:

```onion
println("Hello")
Math::random()
System::out.println("Java style")
```

Default static imports make some class members available without `::` (for example, `println("Hello")` from `onion.IO`). The default set is deliberately narrow — pure helpers plus `onion.IO` as the one console exception — so an effectful line looks effectful: `Files::readText`, `Http::get`, `DateTime::now` and `System::exit` must be written qualified, or imported explicitly with `import { onion.Files::* }` (a whole class) or `import { java.lang.System::exit }` (one member). The list lives in `src/main/resources/onion/default-static-imports.txt`.

### Type Casting with `as`

Cast expressions use the `as` operator:

```onion
val x: Double = 3.14
val y: Int = (x as Int)  // Cast to Int

val obj: Object = "string"
val str: String = (obj as String)  // Cast to String
```

### Pattern Matching with `select`

Switch-style pattern matching:

```onion
select value {
  case 1, 2, 3:
    println("Small")
  case 4, 5, 6:
    println("Medium")
  else:
    println("Large")
}
```

## What's Different from Java?

| Feature | Java | Onion |
|---------|------|-------|
| Field declarations | `Type field` | `val/var field: Type` |
| Variable declarations | `Type variable` | `val/var variable[: Type] = value` |
| Static access | `Class.method()` | `Class::method()` |
| Type casting | `(Type) value` | `value as Type` |
| Lambda syntax | `(x) -> x + 1` | `(x :Int) -> { return x + 1; }` |
| Pattern matching | `switch` (Java 14+) | `select` |
| List append | `list.add(x)` | `list << x` |

## Current Limitations

As documented in the README:

1. **Robustness** - The compiler enforces a no-crash / no-miscompile bar via a mutation fuzzer, a crash-reproducer corpus, and codegen-correctness tests; if you do hit a crash or miscompilation, please file a minimal repro
2. **Erasure generics** - No reified type info; type arguments are invariant (no variance or wildcards)
3. **Tail-call optimization** - Covers direct and mutual self-recursion, not general continuation-passing style
4. **Diagnostics** - Some errors are reported later in the pipeline than ideal

The examples in the `run/` directory are verified to compile and execute correctly.

## Next Steps

- [Basic Syntax](basic-syntax.md) - Learn the fundamentals
- [Classes and Objects](classes-and-objects.md) - Object-oriented programming
- [Java Interoperability](java-interop.md) - Working with Java libraries
