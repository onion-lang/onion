# Variables and Types

Onion is a statically-typed language, meaning every variable has a type determined at compile time.

## Type Annotations

Local `val` / `var` declarations can omit the type when an initializer is present. Use explicit type annotations for fields, top-level declarations, or when no initializer is provided:

```onion
val name = "Alice"
var count = 0
val age: Int = 30
val height: Double = 5.8
```

## Type System

### Primitive Types

Onion supports all JVM primitive types:

```onion
val byteVal: Byte = 127
val shortVal: Short = 32767
val intVal: Int = 2147483647
val longVal: Long = 9223372036854775807L
val floatVal: Float = 3.14f
val doubleVal: Double = 3.14159
val charVal: Char = 'A'
val boolVal: Boolean = true
```

### Reference Types

#### String Type

```onion
val message: String = "Hello, World!"
val empty: String = ""
val multiline: String = "Line 1\nLine 2"
```

#### Class Types

Any Java or Onion class can be used as a type:

```onion
import {
  java.util.ArrayList;
  java.io.File;
}

val list: ArrayList = new ArrayList
val file: File = new File("data.txt")
```

#### Interface Types

```onion
import {
  java.util.List;
  java.util.ArrayList;
}

val list: List = new ArrayList  // Interface type
```

### Array Types

Arrays are declared with `Type[]` syntax:

```onion
val integers: Int[] = new Int[10]
val strings: String[] = new String[3]
strings[0] = "a"
strings[1] = "b"
strings[2] = "c"
val objects: Object[] = new Object[5]
```

### Null Type

The `null` literal has a special null type:

```onion
val maybeString: String = null
val maybeObject: Object = null
```

### Bottom Type (Nothing)

`Nothing` is the subtype of all types and is used for expressions that never return, such as `return`, `throw`, `break`, and `continue`.

## Type Casting

### Using the `$` Operator

Convert between types using the `$` casting operator:

```onion
// Numeric casting
val x: Double = 3.14
val y: Int = x$Int  // 3

// Object casting
val obj: Object = "Hello"
val str: String = obj$String

// Random number to Int
val random: Int = (Math::random() * 100)$Int
```

### Automatic Widening

Smaller numeric types automatically widen to larger ones:

```onion
val i: Int = 42
val l: Long = i  // Int → Long (automatic)
val d: Double = l  // Long → Double (automatic)
```

### Explicit Narrowing

Narrowing conversions require explicit casting:

```onion
val d: Double = 3.14
val i: Int = d$Int  // Must use $ operator
```

## Type Compatibility

### Assignment Compatibility

A value can be assigned to a variable if:

1. Types are exactly the same
2. Value type is a subtype of variable type
3. Automatic widening applies (for primitives)

```onion
// Same type
val s1: String = "Hello"
val s2: String = s1  // OK

// Subtype
import { java.util.ArrayList; java.util.List; }
val arrayList: ArrayList = new ArrayList
val list: List = arrayList  // OK (ArrayList implements List)

// Widening
val i: Int = 42
val l: Long = i  // OK (Int → Long)
```

## Variable Scope

### Local Variables

Variables declared in methods or blocks:

```onion
def method {
  val local: Int = 10
  if true {
    val nested: Int = 20
    IO::println(local)   // OK
    IO::println(nested)  // OK
  }
  // IO::println(nested)  // ERROR: nested not in scope
}
```

### Fields

Declare fields with `val` / `var` and access them via `this.field`:

```onion
class Counter {
  var count: Int

  public:
    def this {
      this.count = 0  // Initialize member
    }

    def increment {
      this.count = this.count + 1  // Access member
    }

    def getCount: Int = this.count  // Return member value
}
```

### Static Variables

Static members belong to the class, not instances:

```onion
class MathUtils {
  static val PI: Double = 3.14159

  public:
    static def square(x: Double): Double = x * x
}

// Access static members
val pi: Double = MathUtils::PI
val result: Double = MathUtils::square(5.0)
```

## Type Inference

Onion can infer the type of **local** `val` / `var` declarations when an initializer is present (fields and top-level declarations still require explicit types):

```onion
// With explicit type
val name: String = "Alice"

// Inferred from the initializer
val age = 30         // Int
var greeting = "Hi"  // String
```

## Generic Types (Java Generics)

When using Java generic types, specify type parameters with `[]`:

```onion
import {
  java.util.ArrayList;
  java.util.HashMap;
}

val list: ArrayList[String] = new ArrayList[String]
val map: HashMap[String, Int] = new HashMap[String, Int]
```

### Primitive Type Arguments

Type arguments may be primitive types (e.g., `Int`). Onion uses JVM erasure, so primitive type arguments are boxed/unboxed at call boundaries:

```onion
val list: ArrayList[Int] = new ArrayList[Int]
list.add(1)
val x: Int = list.get(0)
```

## Function Types

Functions are represented by `Function0` through `Function10` interfaces:

```onion
val func: (Int) -> Int = (x: Int) -> { return x * 2; }
val result: Int = func.call(5)  // 10
```

- `Function0` - No parameters
- `Function1` - One parameter
- `Function2` - Two parameters
- ... up to `Function10`

## Next Steps

- [Control Flow](control-flow.md) - if, while, for, select
- [Functions](functions.md) - Function definitions and lambdas
- [Classes and Objects](classes-and-objects.md) - Object-oriented programming
