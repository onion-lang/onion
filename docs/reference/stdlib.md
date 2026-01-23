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
val name: String = IO::readln()
```

### IO::readln

Read a line of input from the user:

```onion
val name: String = IO::readln("What's your name? ")
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

val reader: BufferedReader = new BufferedReader(
  new InputStreamReader(System::in)
)
```

### System::currentTimeMillis

Get current time in milliseconds:

```onion
val time: Long = System::currentTimeMillis()
IO::println("Current time: " + time)
```

### System::getProperty

Get system properties:

```onion
val os: String = System::getProperty("os.name")
val user: String = System::getProperty("user.name")
val home: String = System::getProperty("user.home")
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
val r: Double = Math::random()
val randomInt: Int = (Math::random() * 100)$Int
```

### Math::sqrt

Square root:

```onion
val result: Double = Math::sqrt(16.0)  // 4.0
```

### Math::pow

Exponentiation:

```onion
val result: Double = Math::pow(2.0, 3.0)  // 8.0
```

### Math::abs

Absolute value:

```onion
val abs1: Int = Math::abs(-10)     // 10
val abs2: Double = Math::abs(-3.14)  // 3.14
```

### Math::max / Math::min

Maximum and minimum:

```onion
val max: Int = Math::max(10, 20)    // 20
val min: Int = Math::min(10, 20)    // 10
```

### Math::floor / Math::ceil / Math::round

Rounding functions:

```onion
val floor: Double = Math::floor(3.7)  // 3.0
val ceil: Double = Math::ceil(3.2)    // 4.0
val round: Long = Math::round(3.5)    // 4
```

### Math::sin / Math::cos / Math::tan

Trigonometric functions (radians):

```onion
val sine: Double = Math::sin(Math::PI / 2)    // 1.0
val cosine: Double = Math::cos(0.0)           // 1.0
val tangent: Double = Math::tan(Math::PI / 4) // 1.0
```

### Math Constants

```onion
val pi: Double = Math::PI       // 3.14159...
val e: Double = Math::E         // 2.71828...
```

## Function Interfaces

Built-in function types for lambdas and closures. You can call them with `f(args)` as a shorthand for `f.call(args)`.

### Function0

Function with no parameters:

```onion
val func: Function0[Int] = () -> { return 42; }
val result: Int = func.call()
```

### Function1

Function with one parameter:

```onion
val double: Function1[Int, Int] = (x: Int) -> { return x * 2; }
val result: Int = double.call(5)
```

### Function2

Function with two parameters:

```onion
val add: Function2[Int, Int, Int] = (x: Int, y: Int) -> { return x + y; }
val result: Int = add.call(3, 7)
```

### Function3 through Function10

Functions with 3 to 10 parameters follow the same pattern.

## Wrapper Classes

Java wrapper classes for primitives (accessed with `J` prefix in some contexts).

### JInteger

Integer operations:

```onion
val i: Int = JInteger::parseInt("42")
val s: String = JInteger::toString(42)
val max: Int = JInteger::MAX_VALUE
val min: Int = JInteger::MIN_VALUE
```

### JLong

Long operations:

```onion
val l: Long = JLong::parseLong("1234567890")
val s: String = JLong::toString(1234567890L)
```

### JDouble

Double operations:

```onion
val d: Double = JDouble::parseDouble("3.14")
val s: String = JDouble::toString(3.14)
```

### JBoolean

Boolean operations:

```onion
val b: Boolean = JBoolean::parseBoolean("true")
val s: String = JBoolean::toString(true)
```

## Common Java Classes

Frequently used Java standard library classes.

### String

String operations (automatically available):

```onion
val text: String = "Hello, World!"
val upper: String = text.toUpperCase()
val lower: String = text.toLowerCase()
val length: Int = text.length()
val sub: String = text.substring(0, 5)
val contains: Boolean = text.contains("World")
val starts: Boolean = text.startsWith("Hello")
val ends: Boolean = text.endsWith("!")
```

### StringBuilder

Efficient string building:

```onion
import { java.lang.StringBuilder; }

val builder: StringBuilder = new StringBuilder()
builder.append("Hello")
builder.append(" ")
builder.append("World")
val result: String = builder.toString()
```

### ArrayList

Dynamic arrays:

```onion
import { java.util.ArrayList; }

val list: ArrayList = new ArrayList
list.add("First")
list << "Second"  // Using << operator
val size: Int = list.size()
val item: Object = list.get(0)
list.remove(0)
val empty: Boolean = list.isEmpty()
```

### HashMap

Key-value maps:

```onion
import { java.util.HashMap; }

val map: HashMap = new HashMap
map.put("key1", "value1")
map.put("key2", "value2")
val value: Object = map.get("key1")
val has: Boolean = map.containsKey("key1")
val size: Int = map.size()
```

### File

File operations:

```onion
import { java.io.File; }

val file: File = new File("data.txt")
val exists: Boolean = file.exists()
val isFile: Boolean = file.isFile()
val isDir: Boolean = file.isDirectory()
val name: String = file.getName()
val path: String = file.getPath()
val length: Long = file.length()
```

### BufferedReader

Reading text:

```onion
import {
  java.io.BufferedReader;
  java.io.FileReader;
}

val reader: BufferedReader = new BufferedReader(
  new FileReader("file.txt")
)

var line: String = null
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

val writer: BufferedWriter = new BufferedWriter(
  new FileWriter("output.txt")
)

writer.write("Hello, World!")
writer.newLine()
writer.close()
```

## Iterables Module

Provided via `onion.Iterables` (Java interface).

Access iteration utilities for collections and arrays:

- `Iterables::map(list|iterable, f)`
- `Iterables::filter(list|iterable, predicate)`
- `Iterables::foldl(iterable, init, f)`
- `Iterables::exists(iterable, predicate)`
- `Iterables::forAll(iterable, predicate)`
- `Iterables::listOf(elements...)`

## Option Module

Provided via `onion.Option`.

- `Option::some(value)` / `Option::none()` / `Option::of(value)`
- `opt.getOrElse(defaultValue)`
- `opt.map(f)` / `opt.flatMap(f)` / `opt.filter(predicate)`

## Result Module

Provided via `onion.Result`.

- `Result::ok(value)` / `Result::err(error)`
- `Result::ofNullable(value, errorIfNull)` / `Result::trying(operation)`
- `res.map(f)` / `res.mapError(f)` / `res.flatMap(f)` / `res.toOption()`

## Future Module

Provided via `onion.Future`. Represents asynchronous computations.

### Creating Futures

```onion
// Already completed with a value
val done: Future[Int] = Future::successful(42)

// Already failed
val fail: Future[Int] = Future::failed(new RuntimeException("error"))

// Run async on background thread
val async: Future[String] = Future::async(() -> { return compute(); })

// Async with exception handling
val safe: Future[Int] = Future::asyncThrowing(() -> {
  return riskyOperation();
})

// Delay
val delayed: Future[Void] = Future::delay(1000L)  // 1 second
```

### Transformation Methods

```onion
val f: Future[Int] = Future::successful(10)

// Transform the value
f.map((x: Int) -> { return x * 2; })  // Future[Int] = 20

// Chain async operations
f.flatMap((x: Int) -> { return Future::successful(x + 1); })

// Filter (fails if predicate false)
f.filter((x: Int) -> { return x > 0; })

// Alias for flatMap (used by do notation)
f.bind((x: Int) -> { return Future::successful(x); })
```

### Error Handling

```onion
val f: Future[Int] = Future::failed(new RuntimeException("oops"))

// Recover with value
f.recover((e: Throwable) -> { return 0; })

// Recover with another Future
f.recoverWith((e: Throwable) -> { return Future::successful(42); })

// Transform error
f.mapError((e: Throwable) -> { return new CustomException(e); })
```

### Callbacks

```onion
val f: Future[String] = Future::async(() -> { return "result"; })

f.onSuccess((value: String) -> { IO::println(value); })
f.onFailure((error: Throwable) -> { IO::println(error); })
f.onComplete(
  (value: String) -> { IO::println("ok: " + value); },
  (error: Throwable) -> { IO::println("err: " + error); }
)
```

### Blocking Operations

```onion
val f: Future[Int] = Future::successful(42)

f.await()              // Block and get result (throws on failure)
f.awaitTimeout(5000L)  // Block with timeout in ms
f.getOrElse(0)         // Get result or default on failure
```

### Status Queries

```onion
f.isCompleted()  // true if done (success or failure)
f.isSuccess()    // true if completed successfully
f.isFailure()    // true if completed with error
```

### Combining Futures

```onion
val f1: Future[Int] = Future::successful(1)
val f2: Future[Int] = Future::successful(2)

// Zip into tuple-like array
f1.zip(f2)  // Future[Object[]] = [1, 2]

// Race: first to complete wins
f1.race(f2)

// Wait for all
Future::all(f1, f2, f3)  // Future[Object[]] = [1, 2, 3]

// First to complete
Future::first(f1, f2, f3)
```

### Conversions

```onion
val f: Future[Int] = Future::successful(42)

f.toOption()  // Option[Int] - Some(42) or None (blocks)
f.toResult()  // Result[Int, Throwable] (blocks)
f.underlying() // Java CompletableFuture for interop
```

### Do Notation Support

Future works with do notation for sequential async composition:

```onion
val result: Future[Int] = do[Future] {
  x <- Future::async(() -> { return fetchA(); })
  y <- Future::async(() -> { return fetchB(x); })
  ret x + y
}
```

## Next Steps

- [Language Specification](specification.md) - Formal language spec
- [Compiler Architecture](compiler-architecture.md) - Compiler internals
- [Java Interoperability](../guide/java-interop.md) - Using Java libraries
