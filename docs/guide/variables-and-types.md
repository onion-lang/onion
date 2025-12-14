# Variables and Types

Onion is a statically-typed language, meaning every variable has a type determined at compile time.

## Type Annotations

Variables require explicit type annotations:

```onion
def name :String = "Alice"
def age :Int = 30
def height :Double = 5.8
```

## Type System

### Primitive Types

Onion supports all JVM primitive types:

```onion
def byteVal :Byte = 127
def shortVal :Short = 32767
def intVal :Int = 2147483647
def longVal :Long = 9223372036854775807L
def floatVal :Float = 3.14f
def doubleVal :Double = 3.14159
def charVal :Char = 'A'
def boolVal :Boolean = true
```

### Reference Types

#### String Type

```onion
def message :String = "Hello, World!"
def empty :String = ""
def multiline :String = "Line 1\nLine 2"
```

#### Class Types

Any Java or Onion class can be used as a type:

```onion
import {
  java.util.ArrayList;
  java.io.File;
}

def list :ArrayList = new ArrayList
def file :File = new File("data.txt")
```

#### Interface Types

```onion
import {
  java.util.List;
  java.util.ArrayList;
}

def list :List = new ArrayList  // Interface type
```

### Array Types

Arrays are declared with `Type[]` syntax:

```onion
def integers :Int[] = new Int[10]
def strings :String[] = ["a", "b", "c"]
def objects :Object[] = new Object[5]
```

### Null Type

The `null` literal has a special null type:

```onion
def maybeString :String = null
def maybeObject :Object = null
```

## Type Casting

### Using the `$` Operator

Convert between types using the `$` casting operator:

```onion
// Numeric casting
def x :Double = 3.14
def y :Int = x$Int  // 3

// Object casting
def obj :Object = "Hello"
def str :String = obj$String

// Random number to Int
def random :Int = (Math::random() * 100)$Int
```

### Automatic Widening

Smaller numeric types automatically widen to larger ones:

```onion
def i :Int = 42
def l :Long = i  // Int → Long (automatic)
def d :Double = l  // Long → Double (automatic)
```

### Explicit Narrowing

Narrowing conversions require explicit casting:

```onion
def d :Double = 3.14
def i :Int = d$Int  // Must use $ operator
```

## Type Compatibility

### Assignment Compatibility

A value can be assigned to a variable if:

1. Types are exactly the same
2. Value type is a subtype of variable type
3. Automatic widening applies (for primitives)

```onion
// Same type
def s1 :String = "Hello"
def s2 :String = s1  // OK

// Subtype
import { java.util.ArrayList; java.util.List; }
def arrayList :ArrayList = new ArrayList
def list :List = arrayList  // OK (ArrayList implements List)

// Widening
def i :Int = 42
def l :Long = i  // OK (Int → Long)
```

## Variable Scope

### Local Variables

Variables declared in methods or blocks:

```onion
def method {
  def local :Int = 10
  if true {
    def nested :Int = 20
    IO::println(local)   // OK
    IO::println(nested)  // OK
  }
  // IO::println(nested)  // ERROR: nested not in scope
}
```

### Member Variables

Instance variables prefixed with `@`:

```onion
class Counter {
  @count :Int

  public:
    def new {
      @count = 0  // Initialize member
    }

    def increment {
      @count = @count + 1  // Access member
    }

    def getCount :Int {
      @count  // Return member value
    }
}
```

### Static Variables

Static members belong to the class, not instances:

```onion
class MathUtils {
  static @PI :Double = 3.14159

  public:
    static def square(x :Double) :Double {
      x * x
    }
}

// Access static members
def pi :Double = MathUtils::PI
def result :Double = MathUtils::square(5.0)
```

## Type Inference (Future Feature)

Currently, Onion requires explicit type annotations. Future versions may support type inference:

```onion
// Current (required)
def name :String = "Alice"

// Future (planned)
def name = "Alice"  // Inferred as String
```

## Generic Types (Java Generics)

When using Java generic types, specify type parameters:

```onion
import {
  java.util.ArrayList;
  java.util.HashMap;
}

// Note: Current Onion may not enforce type parameters
def list :ArrayList = new ArrayList  // Can hold any Object

// In Java: ArrayList<String> list = new ArrayList<>();
// Onion: def list :ArrayList = new ArrayList
```

## Function Types

Functions are represented by `Function0` through `Function10` interfaces:

```onion
def func :Function1 = (x :Int) -> { return x * 2; }
def result :Int = func.call(5)$Int  // 10
```

- `Function0` - No parameters
- `Function1` - One parameter
- `Function2` - Two parameters
- ... up to `Function10`

## Next Steps

- [Control Flow](control-flow.md) - if, while, for, select
- [Functions](functions.md) - Function definitions and lambdas
- [Classes and Objects](classes-and-objects.md) - Object-oriented programming
