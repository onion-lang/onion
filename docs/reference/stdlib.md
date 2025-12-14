# Standard Library

Onion's standard library consists of built-in modules and interfaces for common functionality.

## IO Module

Console input and output operations.

### IO::println

Print a line to standard output:

```onion
IO::println("Hello, World!")
IO::println("Value: " + value)
```

### IO::print

Print without newline:

```onion
IO::print("Enter name: ")
def name :String = IO::input("")
```

### IO::input

Read a line of input from the user:

```onion
def name :String = IO::input("What's your name? ")
IO::println("Hello, " + name)
```

## System Module

Access to system-level operations via Java's `System` class.

### System::out

Standard output stream:

```onion
System::out.println("Direct system output")
System::out.print("No newline")
```

### System::in

Standard input stream:

```onion
import {
  java.io.BufferedReader;
  java.io.InputStreamReader;
}

def reader :BufferedReader = new BufferedReader(
  new InputStreamReader(System::in)
)
```

### System::currentTimeMillis

Get current time in milliseconds:

```onion
def time :Long = System::currentTimeMillis()
IO::println("Current time: " + time)
```

### System::getProperty

Get system properties:

```onion
def os :String = System::getProperty("os.name")
def user :String = System::getProperty("user.name")
def home :String = System::getProperty("user.home")
```

### System::exit

Exit the program:

```onion
System::exit(0)  // Success
System::exit(1)  // Error
```

## Math Module

Mathematical operations via Java's `Math` class.

### Math::random

Generate random number between 0.0 and 1.0:

```onion
def r :Double = Math::random()
def randomInt :Int = (Math::random() * 100)$Int
```

### Math::sqrt

Square root:

```onion
def result :Double = Math::sqrt(16.0)  // 4.0
```

### Math::pow

Exponentiation:

```onion
def result :Double = Math::pow(2.0, 3.0)  // 8.0
```

### Math::abs

Absolute value:

```onion
def abs1 :Int = Math::abs(-10)     // 10
def abs2 :Double = Math::abs(-3.14)  // 3.14
```

### Math::max / Math::min

Maximum and minimum:

```onion
def max :Int = Math::max(10, 20)    // 20
def min :Int = Math::min(10, 20)    // 10
```

### Math::floor / Math::ceil / Math::round

Rounding functions:

```onion
def floor :Double = Math::floor(3.7)  // 3.0
def ceil :Double = Math::ceil(3.2)    // 4.0
def round :Long = Math::round(3.5)    // 4
```

### Math::sin / Math::cos / Math::tan

Trigonometric functions (radians):

```onion
def sine :Double = Math::sin(Math::PI / 2)    // 1.0
def cosine :Double = Math::cos(0.0)           // 1.0
def tangent :Double = Math::tan(Math::PI / 4) // 1.0
```

### Math Constants

```onion
def pi :Double = Math::PI       // 3.14159...
def e :Double = Math::E         // 2.71828...
```

## Function Interfaces

Built-in function types for lambdas and closures.

### Function0

Function with no parameters:

```onion
def func :Function0 = () -> { return 42; }
def result :Int = func.call()$Int
```

### Function1

Function with one parameter:

```onion
def double :Function1 = (x :Int) -> { return x * 2; }
def result :Int = double.call(5)$Int
```

### Function2

Function with two parameters:

```onion
def add :Function2 = (x :Int, y :Int) -> { return x + y; }
def result :Int = add.call(3, 7)$Int
```

### Function3 through Function10

Functions with 3 to 10 parameters follow the same pattern.

## Wrapper Classes

Java wrapper classes for primitives (accessed with `J` prefix in some contexts).

### JInteger

Integer operations:

```onion
def i :Int = JInteger::parseInt("42")
def s :String = JInteger::toString(42)
def max :Int = JInteger::MAX_VALUE
def min :Int = JInteger::MIN_VALUE
```

### JLong

Long operations:

```onion
def l :Long = JLong::parseLong("1234567890")
def s :String = JLong::toString(1234567890L)
```

### JDouble

Double operations:

```onion
def d :Double = JDouble::parseDouble("3.14")
def s :String = JDouble::toString(3.14)
```

### JBoolean

Boolean operations:

```onion
def b :Boolean = JBoolean::parseBoolean("true")
def s :String = JBoolean::toString(true)
```

## Common Java Classes

Frequently used Java standard library classes.

### String

String operations (automatically available):

```onion
def text :String = "Hello, World!"
def upper :String = text.toUpperCase()
def lower :String = text.toLowerCase()
def length :Int = text.length()
def sub :String = text.substring(0, 5)
def contains :Boolean = text.contains("World")
def starts :Boolean = text.startsWith("Hello")
def ends :Boolean = text.endsWith("!")
```

### StringBuilder

Efficient string building:

```onion
import { java.lang.StringBuilder; }

def builder :StringBuilder = new StringBuilder()
builder.append("Hello")
builder.append(" ")
builder.append("World")
def result :String = builder.toString()
```

### ArrayList

Dynamic arrays:

```onion
import { java.util.ArrayList; }

def list :ArrayList = new ArrayList
list.add("First")
list << "Second"  // Using << operator
def size :Int = list.size()
def item :Object = list.get(0)
list.remove(0)
def empty :Boolean = list.isEmpty()
```

### HashMap

Key-value maps:

```onion
import { java.util.HashMap; }

def map :HashMap = new HashMap
map.put("key1", "value1")
map.put("key2", "value2")
def value :Object = map.get("key1")
def has :Boolean = map.containsKey("key1")
def size :Int = map.size()
```

### File

File operations:

```onion
import { java.io.File; }

def file :File = new File("data.txt")
def exists :Boolean = file.exists()
def isFile :Boolean = file.isFile()
def isDir :Boolean = file.isDirectory()
def name :String = file.getName()
def path :String = file.getPath()
def length :Long = file.length()
```

### BufferedReader

Reading text:

```onion
import {
  java.io.BufferedReader;
  java.io.FileReader;
}

def reader :BufferedReader = new BufferedReader(
  new FileReader("file.txt")
)

def line :String = null
while (line = reader.readLine()) != null {
  IO::println(line)
}

reader.close()
```

### BufferedWriter

Writing text:

```onion
import {
  java.io.BufferedWriter;
  java.io.FileWriter;
}

def writer :BufferedWriter = new BufferedWriter(
  new FileWriter("output.txt")
)

writer.write("Hello, World!")
writer.newLine()
writer.close()
```

## Iterables Module

Provided via `onion.Iterables` (Java interface).

Access iteration utilities for collections and arrays.

## Next Steps

- [Language Specification](specification.md) - Formal language spec
- [Compiler Architecture](compiler-architecture.md) - Compiler internals
- [Java Interoperability](../guide/java-interop.md) - Using Java libraries
