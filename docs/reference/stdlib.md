# Standard Library

Onion's standard library consists of built-in modules and interfaces for common functionality.

## Modules at a glance

| Area | Modules |
|------|---------|
| **I/O & system** | `IO` (console), `Files` (files + paths), `System`, `Proc` (subprocesses), `Args` (CLI), `Http` (HTTP client) |
| **Collections** | `Colls` (lists: map/filter/fold, chunked/windowed, sumBy/maxBy), `Iterables`, `Maps`, `Sets` |
| **Text** | `Strings` (case, split, pad, parse), `Text` (wrap/indent/table), `Regex` |
| **Numbers** | `Math`, `OnionMath` (hyperbolic trig, `clamp`, `hypot`, bounded `randomInt`), `Stats` (sum/average/median/stddev), `Format` (grouping, bytes, durations) |
| **Data formats** | `Json`, `Yaml`, `Csv`, `Config` (dot-notation config access) |
| **Encoding** | `Codec` (base64/hex/url), `Hash` (md5/sha256/…) |
| **Functional** | `Option`, `Result`, `Future`, `Outcome` + `Defect` (reading external data) |
| **Positions** | `Origin` (where a value came from, in the text it was read out of) |
| **Boundaries** | `Shape` + `Shapes` (text <-> typed value), `Scalars` |
| **Date & random** | `DateTime`, `Rand` (choice/shuffle/sample/uuid) |
| **Testing & timing** | `Assert`, `Timing` |

Most helpers are also usable as method chains, not only as static `Module::` calls —
collections (`list.filter { ... }.map { ... }`, `m.mapValues { ... }`), strings
(`"s".capitalize()`), hashing/encoding (`"pw".sha256()`, `"x".base64Encode()`), text
layout (`text.wrap(40)`), numeric aggregation (`nums.sum()`, `nums.average()`), and number
formatting (`(1536L).bytes()`, `(21L).ordinal()`).

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

### IO::readLine

Read a line from standard input, or `null` at end of input. `IO::readln()` (no
prompt) is an alias for this:

```onion
val line: String? = IO::readLine()
```

### IO::readAll

Read all remaining standard input as a single string:

```onion
val everything: String = IO::readAll()
```

### Formatted Output

```onion
IO::printf("%s is %d\n", "age", 30)
val s: String = IO::format("%.2f", 3.14159)
```

### Error Output (stderr)

```onion
IO::eprint("warning: ")
IO::eprintln("disk almost full")
IO::eprintf("failed after %d retries\n", 3)
```

### Type-Safe Input

Read and parse a line as a specific type, throwing on invalid input; each has
an overload that prints a prompt first:

```onion
val age: Int = IO::readInt("Age: ")
val price: Long = IO::readLong("Price: ")
val ratio: Double = IO::readDouble("Ratio: ")
val ok: Boolean = IO::readBoolean("Continue? ")  // accepts true/yes/1, false/no/0
```

### Safe Input

Like the type-safe readers above, but return `null` instead of throwing on
invalid input or end of stream:

```onion
val n: Int? = IO::tryReadInt("N: ")
val d: Double? = IO::tryReadDouble("D: ")
val l: Long? = IO::tryReadLong("L: ")
```

### Line-Oriented I/O

```onion
val lines: List = IO::readLines()          // reads until end of input
IO::eachLine { line => IO::println(line) } // applies a callback to each remaining line
IO::printLines(["a", "b", "c"])            // one item per line
IO::printAll("a", "b", "c")                // varargs form of printLines
```

### Utility

```onion
IO::flush()    // flushes standard output
IO::newline()  // prints a blank line
IO::clear()    // clears the terminal screen (ANSI escape codes)
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
val randomInt: Int = (Math::random() * 100) as Int
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

## OnionMath Module

An `onion.*` numeric module, distinct from the JDK's `Math`, covering hyperbolic
trig, safe rounding/clamping, and a bounded random integer. It is default-imported
like the rest of the standard library, so no explicit import is needed.

### OnionMath::sin / OnionMath::cos / OnionMath::tan / OnionMath::asin / OnionMath::acos / OnionMath::atan / OnionMath::atan2

Trigonometric and inverse trigonometric functions (radians):

```onion
val sine: Double = OnionMath::sin(OnionMath::PI / 2)     // 1.0
val angle: Double = OnionMath::atan2(1.0, 1.0)           // pi/4
```

### OnionMath::sinh / OnionMath::cosh / OnionMath::tanh

Hyperbolic trigonometric functions:

```onion
val h: Double = OnionMath::sinh(1.0)
```

### OnionMath::exp / OnionMath::log / OnionMath::log10

Exponential and logarithms:

```onion
val e2: Double = OnionMath::exp(1.0)     // e
val l: Double = OnionMath::log(OnionMath::E)   // 1.0
val l10: Double = OnionMath::log10(100.0)      // 2.0
```

### OnionMath::pow / OnionMath::sqrt / OnionMath::cbrt

Powers and roots:

```onion
val cube: Double = OnionMath::pow(2.0, 3.0)  // 8.0
val root: Double = OnionMath::sqrt(16.0)     // 4.0
val croot: Double = OnionMath::cbrt(27.0)    // 3.0
```

### OnionMath::abs / OnionMath::absFloat / OnionMath::absInt / OnionMath::absLong

Absolute value, by primitive type:

```onion
val a1: Double = OnionMath::abs(-3.14)
val a2: Int = OnionMath::absInt(-10)      // 10
val a3: Long = OnionMath::absLong(-10L)   // 10
```

### OnionMath::min / OnionMath::minInt / OnionMath::minLong / OnionMath::max / OnionMath::maxInt / OnionMath::maxLong

Minimum and maximum, by primitive type:

```onion
val lo: Int = OnionMath::minInt(10, 20)   // 10
val hi: Int = OnionMath::maxInt(10, 20)   // 20
```

### OnionMath::floor / OnionMath::ceil / OnionMath::round / OnionMath::roundFloat

Rounding functions:

```onion
val f: Double = OnionMath::floor(3.7)     // 3.0
val c: Double = OnionMath::ceil(3.2)      // 4.0
val r: Long = OnionMath::round(3.5)       // 4
val rf: Int = OnionMath::roundFloat(3.5f) // 4
```

### OnionMath::random / OnionMath::randomInt

Random number generation. Unlike `Math::random`, `randomInt` takes bounds directly
and is tracked by the effect checker as a `Rand` effect:

```onion
val r: Double = OnionMath::random()          // [0.0, 1.0)
val n: Int = OnionMath::randomInt(1, 10)     // [1, 10], inclusive
```

### OnionMath::signum / OnionMath::signumFloat

Sign of a number (`-1.0`, `0.0`, or `1.0`):

```onion
val s: Double = OnionMath::signum(-5.0)   // -1.0
```

### OnionMath::toRadians / OnionMath::toDegrees

Angle unit conversion:

```onion
val rad: Double = OnionMath::toRadians(180.0)  // pi
val deg: Double = OnionMath::toDegrees(OnionMath::PI)  // 180.0
```

### OnionMath::clamp / OnionMath::clampInt

Constrain a value to a range:

```onion
val c1: Double = OnionMath::clamp(15.0, 0.0, 10.0)  // 10.0
val c2: Int = OnionMath::clampInt(-5, 0, 10)        // 0
```

### OnionMath::hypot

Hypotenuse without intermediate overflow/underflow:

```onion
val h: Double = OnionMath::hypot(3.0, 4.0)  // 5.0
```

### OnionMath Constants

```onion
val pi: Double = OnionMath::PI  // 3.14159...
val e: Double = OnionMath::E    // 2.71828...
```

## Origin

Where a value came from, in the text it was read out of — the runtime counterpart to the
compiler's own source locations. A parser that knows it failed on line 12 can say so,
instead of returning a bare `null`.

`source` is free-form: a file path, a URL, `"<stdin>"`, `"<literal>"`. Line and column are
1-based. A column of `0` means the position is known only to the line, which is what a
line-oriented parser can honestly report.

```onion
import { onion.Origin; }

val o = Origin::at("access.log", 12, 5)
println(o.describe())          // access.log:12:5

val lineOnly = Origin::atLine("data.json", 4)
println(lineOnly.describe())   // data.json:4
println(lineOnly.hasColumn())  // false
```

### Origin::at / Origin::atLine / Origin::spanning

`at(source, line, column)` spans a single character; `atLine(source, line)` records a line
with no column; `spanning(source, line, column, span)` covers `span` characters.

### origin.onLine / origin.inSource

Parsing a document line by line means each sub-parse reports positions relative to its own
line. `onLine` lifts one back into the whole document; `inSource` retargets it.

```onion
Origin::at("log.txt", 1, 3).onLine(40).describe()   // log.txt:40:3
```

### origin.describe

`file:line:column`, or `file:line` when only the line is known — the form every compiler
and editor already knows how to parse. `toString` returns the same.

## Outcome and Defect

The result of reading external data: either a value, or **every** reason it could not be
read. `Defect` is one thing that was wrong; `Outcome[T]` is a value or a list of them.

A `Defect` answers three questions a caller actually has — where in the text (`origin`,
which may be absent), where in the value (`path`), and what was expected against what was
found.

```onion
import { onion.Outcome; onion.Defect; onion.Origin; }

val d = Defect::at(Origin::atLine("config.json", 4), "port", "Int", "\"http\"")
println(d.describe())     // config.json:4: port: expected Int, found "http"

val missing = Defect::of("name", "String", "absent")
println(missing.describe())   // name: expected String, found absent
```

### Why not Result?

Because of `zip`. `Result` is monadic: `bind` short-circuits, so the first bad field hides
the rest. A record with three malformed fields should report three defects in one pass.

```
Ok(f)   zip Ok(x)   = Ok(f(x))
Bad(d1) zip Ok(_)   = Bad(d1)
Ok(_)   zip Bad(d2) = Bad(d2)
Bad(d1) zip Bad(d2) = Bad(d1 ++ d2)     <- the reason this type exists
```

```onion
val a: Outcome[JInteger] = Outcome::bad(Defect::of("x", "Int", "p"))
val b: Outcome[JInteger] = Outcome::bad(Defect::of("y", "Int", "q"))
println(a.zip(b) { p, q => p + q }.defects().size)   // 2, not 1
```

`bind` still short-circuits, because it must — the second computation may depend on the
first's value. Both are available, and `do[Outcome]` uses `bind`.

### Reading many values

`all` is all-or-nothing and accumulates every defect. When a partial result is still worth
having — a log file where the good lines matter — `values` and `defects` keep both.

```onion
val os: List[Outcome[JInteger]] =
  [Outcome::ok(1), Outcome::bad(Defect::of("a", "Int", "x")), Outcome::ok(3)]

println(Outcome::values(os).size)    // 2
println(Outcome::defects(os).size)   // 1
println(Outcome::all(os).isOk())     // false
```

### Positioning a nested or per-line read

`under` prefixes every defect's path; `onLine` lifts positions reported relative to one
line back into the whole document.

```onion
o.under("address")     // "city" becomes "address.city"
o.onLine(40)           // a defect at line 1 of a fragment becomes line 40 of the file
```

## Shape

A partial, potentially bidirectional correspondence between external text and a typed
value. `Shape[T]` reads text into a `T` and — when the correspondence is invertible —
renders one back.

```onion
import { onion.Shape; onion.Shapes; onion.Outcome; }

val r = pointShape.parse("3,4")
if r.isOk() { println(r.get()) }
println(pointShape.print(pt))
```

### Two laws, deliberately kept apart

```
L1  round-trip      parse(print(v)) == Ok(v)     guaranteed wherever print exists
L2  normalization   print(parse(t)) == t         false in general
```

L2 fails for ordinary reasons — `"007"` is a perfectly good `Int` that prints back as
`"7"`. A shape satisfying L2 as well is *lossless*, which is rare and is what a lens
needs. Most shapes are L1-only, and saying which is the difference between a reversible
language and one that claims to be.

### canPrint

Not every shape can render. A regex with a `\s+` separator has no unique rendering, so
the shape is read-only and `canPrint()` says so before `print` is called — rather than
the method silently not existing.

### Component failures accumulate

Reading two `Int` components out of `"abc,def"` reports **two** defects, not the first
one. That is what `Outcome`'s accumulating `zip` is for.

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

val list: ArrayList[String] = new ArrayList[String]
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

val map: HashMap[String, String] = new HashMap[String, String]
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
- `Iterables::sort(list, comparator)`
- `Iterables::listOf(elements...)`

## Option Module

Provided via `onion.Option`.

- `Option::some(value)` / `Option::none()` / `Option::of(value)`
- `opt.getOrElse(defaultValue)` / `opt.orElseGet(() -> default)` / `opt.orNull()`
- `opt.orElse(otherOption)`
- `opt.map(f)` / `opt.flatMap(f)` / `opt.filter(predicate)`
- `opt.contains(value)` / `opt.exists(predicate)`
- `opt.fold(() -> ifEmpty, v -> ifPresent)` — collapse to a single value
- `opt.toList()` — zero- or one-element list

## Result Module

Provided via `onion.Result`.

- `Result::ok(value)` / `Result::err(error)`
- `Result::ofNullable(value, errorIfNull)` / `Result::trying(operation)`
- `res.map(f)` / `res.mapError(f)` / `res.flatMap(f)` / `res.toOption()`
- `res.getOrElse(default)` / `res.orElseGet(() -> default)` / `res.orNull()`
- `res.fold(e -> ifErr, v -> ifOk)` — collapse to a single value
- `res.recover(e -> value)` / `res.recoverWith(e -> otherResult)` — rescue an `Err`
- `res.exists(predicate)` / `res.toList()`

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

These are **non-blocking** — they report the future's *current* state, so a future
that is still running reports both `isSuccess()` and `isFailure()` as `false`. To wait
for the outcome, use `await()`/`getOrElse()` (or `onSuccess`/`onFailure`/`recover`)
rather than polling `isFailure()`.

### Combining Futures

```onion
val f1: Future[Int] = Future::successful(1)
val f2: Future[Int] = Future::successful(2)

// Zip into tuple-like array
f1.zip(f2)  // Future[List[Object]] = [1, 2]

// Race: first to complete wins
f1.race(f2)

// Wait for all
Future::all(f1, f2, f3)  // Future[List[Object]] = [1, 2, 3]

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

## Rand Module

Random number generation utilities via `onion.Rand`.

### Rand::nextInt / nextLong / nextDouble / nextBoolean

Generate random numbers:

```onion
val randomInt: Int = Rand::nextInt()            // Random Int
val randomLong: Long = Rand::nextLong()         // Random Long
val randomDouble: Double = Rand::nextDouble()   // 0.0 to 1.0
val randomBool: Boolean = Rand::nextBoolean()   // Random Boolean
```

### Rand::nextInt (bounded)

Generate a random integer in a range:

```onion
val dice: Int = Rand::nextInt(6) + 1      // 1 to 6
val percent: Int = Rand::nextInt(100)     // 0 to 99
val d20: Int = Rand::nextInt(1, 21)       // 1 to 20 (min, exclusive max)
```

### Rand::nextDouble (bounded)

```onion
val small: Double = Rand::nextDouble(10.0)         // 0.0 to 10.0
val ranged: Double = Rand::nextDouble(1.0, 2.0)    // 1.0 to 2.0
```

### Rand::choice

Pick one random element from a list:

```onion
val colors: List[String] = ["red", "green", "blue"]
val picked: String = Rand::choice(colors)
```

### Rand::shuffle

Shuffle an array, returning a shuffled list:

```onion
val cards: List[String] = ["A", "B", "C", "D"]
val shuffled: List[String] = Rand::shuffle(cards)
```

### Rand::sample

Pick `n` distinct random elements from a list, without replacement:

```onion
val deck: List[String] = ["A", "B", "C", "D", "E"]
val hand: List[String] = Rand::sample(deck, 3)   // 3 distinct cards
```

### Rand::uuid

Generate a random UUID string:

```onion
val id: String = Rand::uuid()   // e.g. "3fa85f64-5717-4562-b3fc-2c963f66afa6"
```

## Assert Module

Testing assertions via `onion.Assert`. Throws `AssertionError` on failure.

### Basic Assertions

```onion
Assert::isTrue(x > 0)
Assert::isFalse(list.isEmpty())
Assert::equals(expected, actual)
Assert::notEquals(a, b)
```

### Null Assertions

```onion
Assert::notNull(result)
Assert::isNull(errorMessage)
```

### Explicit Failure

```onion
if invalidState {
  Assert::fail("Should not reach here")
}
```

## Timing Module

Time measurement utilities via `onion.Timing`.

### Getting Current Time

```onion
val startNanos: Long = Timing::nanos()     // High-precision (System.nanoTime)
val startMillis: Long = Timing::millis()   // Wall clock (System.currentTimeMillis)
```

### Measuring Elapsed Time

```onion
val start: Long = Timing::nanos()
// ... some operation ...
val elapsedNs: Long = Timing::elapsedNanos(start)      // Elapsed in nanoseconds
val elapsedMs: Double = Timing::elapsedMs(start)       // Elapsed in milliseconds (double, sub-ms precision)
val elapsedMillis: Long = Timing::elapsedMillis(start) // Elapsed in milliseconds since a Timing::millis() start
```

### Formatting Time

```onion
val nanos: Long = 1234567890L
val formatted: String = Timing::formatNanos(nanos)   // "1.23s"
// Output formats: "123ns", "45.67μs", "12.34ms", "1.23s"

val millis: Long = 125000L
val formattedMs: String = Timing::formatMillis(millis)  // "2m5s"
// Output formats: "500ms", "1.23s", "2m30s"
```

### Sleep

```onion
Timing::sleep(1000L)        // Sleep for 1000 milliseconds
Timing::sleepNanos(500000L) // Sleep for 500,000 nanoseconds
```

### Measuring Function Execution

```onion
// Measure and print execution time, return result
val result: Int = Timing::measure(() -> { return expensiveOperation(); })
// Prints: "Elapsed: 123.45ms"

// Same, but for a function that returns nothing
Timing::measureVoid(() -> { expensiveOperation(); })
// Prints: "Elapsed: 123.45ms"
Timing::measureVoid("task", () -> { expensiveOperation(); })
// Prints: "task: 123.45ms"

// Get execution time in nanoseconds without printing
val timeNanos: Long = Timing::time(() -> { return expensiveOperation(); })
```

## Strings Module

String utilities (`onion.Strings`, auto-imported):

```onion
Strings::split("a,b,c", ",")          // List[String] ["a","b","c"]
Strings::splitRegex("a1b2c", "[0-9]") // List[String] ["a","b","c"]
Strings::join(parts, "-")             // arrays or Lists
Strings::upper(s) / Strings::lower(s) / Strings::trim(s)
Strings::replace(s, "a", "b") / Strings::replaceRegex(s, "[0-9]+", "#")
Strings::startsWith(s, p) / Strings::endsWith(s, p) / Strings::contains(s, sub)
Strings::padLeft(s, 8, '0') / Strings::padRight(s, 8, ' ') / Strings::repeat(s, 3)
```

Case and inspection helpers:

```onion
Strings::capitalize("hello")             // "Hello"
Strings::decapitalize("Hello")           // "hello"
Strings::capitalizeWords("a b c")        // "A B C"
Strings::equalsIgnoreCase(a, b) / Strings::containsIgnoreCase(s, sub)
Strings::count("banana", "a")            // 3
Strings::isEmpty("") / Strings::isBlank("   ")   // true / true
Strings::reverse("abc")                  // "cba"
Strings::lines("a\nb\r\nc")              // List[String] ["a","b","c"]
```

Shaping and decomposition:

```onion
Strings::removePrefix("unhappy", "un")   // "happy"
Strings::removeSuffix("running", "ing")  // "runn"
Strings::truncate("hello world", 8, "...")   // "hello..."
Strings::center("hi", 6, '*')            // "**hi**"
Strings::ifBlank("   ", "default")       // "default"
Strings::words("  a  b  c ")             // List[String] ["a","b","c"]
Strings::chars("abc")                    // List ["a","b","c"]
Strings::substring("hello", 1) / Strings::substring("hello", 1, 3)  // "ello" / "el"
Strings::indexOf("hello", "l") / Strings::lastIndexOf("hello", "l")   // 2 / 3
```

Null-safe parsing (return `null`/fallback instead of throwing):

```onion
Strings::toIntOrNull("42")               // 42, or null if not an int
Strings::toLongOrNull("100") / Strings::toDoubleOrNull("3.14")
Strings::toIntOr("nope", 0)              // 0
```

## Files Module

File I/O (`onion.Files`):

```onion
Files::readText("path.txt")            // whole file as String
Files::readLines("path.txt")           // List[String]
Files::writeText("out.txt", content)
Files::writeLines("out.txt", lines)    // List[String] -> one line per entry
Files::appendText("out.txt", content)  // appends, creating the file if needed
Files::readBytes(path) / Files::writeBytes(path, bytes)
Files::list("dir")                     // List of entry names
Files::listFiles("dir")                // List of java.io.File entries
Files::glob("dir", "*.on")             // glob-matched names
Files::delete(path) / Files::exists(path)
Files::isFile(path) / Files::isDirectory(path)
Files::mkdirs(path)                    // creates dir + missing parents
Files::size(path)                      // Long, size in bytes (0 if missing)
Files::copy(src, dst)                  // replaces dst if it exists
Files::move(src, dst)                  // rename; replaces dst if it exists
Files::copyDir(src, dst)               // recursive directory copy
```

Path helpers — file names, parents, joining, and extensions:

```onion
Files::getFileName("a/b/c.txt")        // "c.txt"
Files::getParent("a/b/c.txt")          // "a/b"
Files::getAbsolutePath("a/b/c.txt")    // absolute path resolved against the cwd
Files::joinPath("a/b", "c.txt")        // "a/b/c.txt"
Files::ext("report.txt")               // "txt"   (extension, keyword-safe name)
Files::stem("report.txt")              // "report"
Files::withExtension("report.txt", "md")   // "report.md"
```

## Json Module

JSON parsing and serialization (`onion.Json`). The intermediate representation is
plain Java `Map`/`List`/scalars (`String`/`Long`/`Double`/`Boolean`/`null`):

```onion
val obj = Json::parse("{\"name\": \"kota\"}")
Json::getString(obj, "name")           // typed accessors: getInt/getDouble/getBoolean
Json::stringify(obj) / Json::stringifyPretty(obj)

// Building a value to stringify
val m = Json::object()                 // empty Map
m.put("x", 1)
Json::stringify(m)                     // {"x":1}
val a = Json::array()                  // empty List, for JSON array values

// Navigable wrapper: index with [] and convert with as-methods
val v = Json::value(jsonText)
v["users"][0]["name"].asString()
```

The plain `getString`/`getInt`/`getLong`/`getDouble`/`getFloat`/`getBoolean`/`getShort`/`getByte`
return a boxed value that is `null` when the key is missing or has the wrong type — assigning
that straight into a non-null primitive throws `NullPointerException`. `getStringOr`/`getIntOr`/
`getLongOr`/`getDoubleOr`/`getFloatOr`/`getBooleanOr(obj, key, default)` return a primitive with
an explicit fallback instead:

```onion
val obj = Json::parse("{}")
Json::getIntOr(obj, "missing", 42)     // 42, no NPE
Json::getStringOr(obj, "name", "anon") // "anon"
```

A missing key or out-of-range index on the `Json::value` wrapper yields a null-holding
`Value` instead of throwing, so a chain like `v["users"][99]["name"]` stays safe until you
convert it — `asString()`/`asInt()`/etc. return `null`/`0`/`false` at the end of the chain.
`Value` also has `isNull()` (was the underlying value `null`?), `size()` (element count for
an array/object Value, `0` otherwise), and `raw()` (the underlying `Map`/`List`/scalar/`null`).

`Json::parseOrNull(json)` behaves like `Json::parse(json)` but returns `null` on malformed
input instead of throwing `Json.JsonParseException` — useful when a parse failure is just
another "absent" case rather than an error to handle separately:

```onion
val obj = Json::parseOrNull("not json")   // null, no exception
```

`Json::asObject(obj)` and `Json::asArray(obj)` are type-safe casts on the plain
`Map`/`List` representation: each returns its argument cast to `Map`/`List` when the
runtime type matches, or `null` otherwise. They're handy after `Json::get`, `Json::parse`,
or `Json::parseOrNull` return `Object` and you need the Map/List view back to iterate:

```onion
val obj = Json::parse("{\"tags\": [\"a\", \"b\"]}")
val tags = Json::asArray(Json::get(obj, "tags"))   // List, or null if "tags" wasn't an array
```

## Yaml Module

YAML serialization and parsing for flat block-mapping documents
(`onion.Yaml`). Shares the same intermediate representation as `Json` —
scalars map to the same Java types — so `derive!(Yaml)` builds on exactly
the same `toMap` / `fromMap` core as `derive!(Json)`.

Scope: flat block mapping only (no nested maps, no sequences, no anchors).

### Yaml::parse

Parse a YAML flat block-mapping string into a `LinkedHashMap`:

```onion
val data = Yaml::parse("name: Alice\nage: 30\n")
// data is a LinkedHashMap; scalars follow the same type inference as Json::parse
```

Scalar type inference rules (identical to `Json`):
- `""` or `null` → `null`
- `true` / `false` → `Boolean`
- Bare integer (matches `-?\d+`) → `Long`
- Floating-point pattern or number containing `.`/`e`/`E` → `Double`
- Quoted `"..."` → `String` (unescaped, no further coercion)
- Anything else → `String`

Throws `Yaml.YamlParseException` on malformed input; `derive!(Yaml)`'s
`fromYaml` catches this and returns `null` instead.

### Yaml::stringify

Serialize a `Map` (or scalar) to a YAML flat block-mapping string:

```onion
val m = ["name": "Alice", "age": 30L]
val yaml = Yaml::stringify(m)
// "name: Alice\nage: 30\n"
```

String values that would be misread on parse-back (those containing `:`,
`#`, newlines, or that look like numbers or booleans) are automatically
double-quoted. Numbers and booleans are rendered verbatim. Map **keys** are
quoted under the same rule — a key containing `:` or leading/trailing
whitespace is double-quoted so it doesn't collide with the `key: value`
separator on parse-back.

### Round-trip guarantee

For any `Map` produced by `Yaml::parse`, `Yaml::parse(Yaml::stringify(m))`
returns an equal map. Equivalently, for any record annotated with
`derive!(Yaml)`, `fromYaml(toYaml(v)) == v` holds for all scalar-component
values.

### Usage with `derive!(Yaml)`

`derive!(Yaml)` synthesizes `fromYaml` and `toYaml` on any scalar-component
record; see [Records — derive!](specification.md#derive-record-serde-derivation)
for the full contract.

```onion
record ServerConfig(host: String, port: Int, debug: Boolean) derive!(Yaml)

val cfg = new ServerConfig("localhost", 8080, false)
val yaml = ServerConfig::toYaml(cfg)
// "host: localhost\nport: 8080\ndebug: false\n"

val cfg2 = ServerConfig::fromYaml(yaml)   // ServerConfig? — null on parse/convert failure
```

`derive!(Json, Yaml)` is also valid; both formats share the internal
`toMap` / `fromMap` core, so there is no duplication:

```onion
record User(name: String, age: Int) derive!(Json, Yaml)

val u = new User("ko", 3)
val viaJson = User::fromJson(User::toJson(u))   // == u
val viaYaml = User::fromYaml(User::toYaml(u))  // == u
```

## Config Module

Configuration loading and dot-notation access over parsed JSON (`onion.Config`). Builds on
`Json::parse`, so the same object/array/scalar shape applies; nothing here is YAML- or
`.env`-aware — it's JSON plus dotted-path lookups and environment-variable overrides.

```onion
val config = Config::loadJson("config.json")          // reads + parses a JSON file
val config2 = Config::parseJson("{\"port\": 8080}")   // parses a JSON string directly

Config::get(config, "database.host")                   // raw value, or null if not found
Config::getString(config, "database.host", "localhost")
Config::getInt(config, "database.port", 5432)
Config::getLong(config, "database.maxConnections", 10L)
Config::getDouble(config, "database.timeout", 30.0)
Config::getBoolean(config, "database.ssl", false)
```

Paths are dot-separated and walk both objects and arrays — a numeric segment indexes into
an array:

```onion
val config = Config::parseJson("{\"users\": [{\"name\": \"Alice\"}, {\"name\": \"Bob\"}]}")
Config::getString(config, "users.0.name", "unknown")   // "Alice"
```

A missing key, an out-of-range array index, or a value that can't convert to the requested
type all fall back to the supplied default instead of throwing; the numeric getters accept
the stored value as either a JSON number or a numeric string. `hasPath` checks presence
without needing a default:

```onion
Config::hasPath(config, "database.host")   // true / false
```

Environment variables round out configuration — `getEnv` reads one directly, and
`getWithEnvOverride` reads a config path but lets an environment variable take precedence
when set, which is useful for overriding a checked-in config value at deploy time:

```onion
Config::getEnv("PORT", "3000")
Config::getWithEnvOverride(config, "database.host", "DB_HOST", "localhost")
```

## Csv Module

Self-contained RFC 4180 CSV parsing and serialization (`onion.Csv`,
auto-imported) — quoted fields, embedded commas/newlines, and doubled quotes
are handled.

```onion
val rows = Csv::parse(text)                  // List of List of String
val recs = Csv::parseWithHeader(text)        // List of Map (header -> value)

Csv::column(rows, 0)                          // one positional column
Csv::columnByName(recs, "age")                // one header-named column

val out  = Csv::stringify(rows)               // rows -> CSV text
val out2 = Csv::stringifyWithHeader(recs)     // records -> CSV (inverse of parseWithHeader)
```

## Hash Module

Cryptographic and checksum digests (`onion.Hash`). Each hashes a string's UTF-8
bytes and returns a lowercase hex digest.

```onion
Hash::sha256("password")   // 64-char hex
Hash::sha512(text)         // 128-char hex
Hash::md5(text) / Hash::sha1(text)   // checksums / interop (not collision-safe)
```

## Codec Module

Text encoding and decoding (`onion.Codec`): Base64, hex, and URL/percent form.

```onion
val enc = Codec::base64Encode("Hello")    // "SGVsbG8="
Codec::base64Decode(enc)                  // "Hello"
Codec::hexEncode("Hi") / Codec::hexDecode("4869")
Codec::urlEncode("a b&c") / Codec::urlDecode(s)
```

## Stats Module

Numeric aggregation over a list of numbers (`onion.Stats`). The generic
aggregates accept `List[Int]`, `List[Long]` or `List[Double]` and work in double
precision; `sumInt` / `sumLong` keep integer precision.

```onion
val xs: List[Int] = [10, 20, 30, 40]
Stats::sum(xs)       // 100.0      Stats::sumInt(xs)   // 100
Stats::average(xs)   // 25.0       Stats::median(xs)   // 25.0
Stats::min(xs) / Stats::max(xs)    // 10.0 / 40.0
Stats::variance(xs) / Stats::stddev(xs)
```

These are also reachable as method calls, which is the form most code reaches
for. **The method form has the same double precision**, so a list of `Int`
sums to a `Double` — use `Stats::sumInt` when you want an `Int` back:

```onion
val xs: List[Int] = [10, 20, 30, 40]
xs.sum()             // 100.0  (Double — the generic aggregate)
Stats::sumInt(xs)    // 100    (Int)
```

Type erasure is the reason there is no `Int`-returning `sum()` overload: the
element type is gone at runtime, so `sum(List[Int])` and `sum(List[Double])`
would be the same JVM signature.

## Format Module

Locale-independent human-readable formatting (`onion.Format`) — commas, decimals,
sizes and durations.

```onion
Format::integer(1234567)          // "1,234,567"
Format::number(1234.5678, 2)      // "1,234.57"
Format::fixed(3.14159, 2)         // "3.14"
Format::percent(0.756, 1)         // "75.6%"
Format::bytes(1536)               // "1.5 KB"  (1024-based)
Format::duration(3661)            // "1h 1m 1s"
Format::ordinal(21)               // "21st"
```

## Text Module

Console text layout (`onion.Text`): word wrapping, indenting, and aligned tables.

```onion
Text::wrap("a long sentence ...", 40)   // List of wrapped lines
Text::indent("a\nb", "> ")              // "> a\n> b"
Text::dedent("    a\n    b")            // "a\nb"

Text::table([["Name", "Dept"], ["Alice", "Eng"], ["Bob", "Sales"]])
// Name   Dept
// Alice  Eng
// Bob    Sales
```

## Proc Module

Process execution for scripting (`onion.Proc`):

```onion
val r = Proc::capture("git", "status")  // r.status() / r.stdout() / r.stderr() / r.succeeded()
Proc::run("ls", "-la")                  // stdout as String (throws on failure)
Proc::exec("make", "build")             // exit code, output passes through
Proc::captureIn("/tmp", "ls")           // ...In variants set the working directory
```

## Args Module

Command-line argument parsing (`onion.Args`):

```onion
val parsed = Args::parse(args)
parsed.flag("verbose")                  // --verbose
parsed.option("out", "a.out")           // --out path (with default)
parsed.intOption("level", 3)
parsed.positional()                     // List of non-option arguments
```

## Colls Module

Collection factories and pipelines (`onion.Colls`):

```onion
Colls::listOf("a", "b", "c")            // immutable List
Colls::mutableListOf(1, 2, 3)           // ArrayList
Colls::range(0, 5)                      // List [0,1,2,3,4]
Colls::sortedBy(people) { p => p.age() }
// map/filter/reduce/fold pipelines are extension methods on
// List/Iterable/arrays: xs.map { x => x * 2 }.filter { x => x > 0 }
```

## Http

HTTP client utilities (uses Java 11+ HttpClient).

### GET Requests

```
Http::get(url): String
Http::get(url, headers): String    // headers: ["Name1", "Value1", ...]
```

### POST Requests

```
Http::post(url, body): String
Http::postJson(url, jsonBody): String    // Sets Content-Type: application/json
Http::post(url, body, headers): String   // headers: as for get
```

### Other Methods

```
Http::put(url, body): String
Http::delete(url): String
```

### URL Utilities

```
Http::encodeUrl(str): String
Http::decodeUrl(str): String
Http::buildQuery(params): String        // params: alternating keys and values
Http::buildUrl(baseUrl, params): String // appends "?"/"&" + buildQuery(params)
```

### Example

```
val response: String = Http::get("https://api.example.com/data");
val data: Object = Json::parse(response);

val postResponse: String = Http::postJson(
  "https://api.example.com/users",
  "{\"name\": \"Bob\"}"
);
```

---

## DateTime

Date and time utilities using epoch milliseconds.

### Current Time

```
DateTime::now(): Long              // Current epoch milliseconds
DateTime::nowString(): String      // ISO format (local timezone)
DateTime::nowString(pattern): String
```

### Parsing

```
DateTime::parse(isoString): Long
DateTime::parse(dateTime, pattern): Long
```

### Formatting

```
DateTime::format(epochMillis): String
DateTime::format(epochMillis, pattern): String
```

### Components

```
DateTime::year(epochMillis): Int
DateTime::month(epochMillis): Int       // 1-12
DateTime::day(epochMillis): Int         // 1-31
DateTime::hour(epochMillis): Int        // 0-23
DateTime::minute(epochMillis): Int      // 0-59
DateTime::second(epochMillis): Int      // 0-59
DateTime::dayOfWeek(epochMillis): Int   // 1=Monday, 7=Sunday
DateTime::dayOfYear(epochMillis): Int   // 1-366
```

### Arithmetic

```
DateTime::addDays(epochMillis, days): Long
DateTime::addHours(epochMillis, hours): Long
DateTime::addMinutes(epochMillis, minutes): Long
DateTime::addSeconds(epochMillis, seconds): Long
DateTime::addMonths(epochMillis, months): Long
DateTime::addYears(epochMillis, years): Long
```

### Comparison

```
DateTime::diff(time1, time2): Long        // Difference in milliseconds
DateTime::diffDays(time1, time2): Int
DateTime::diffHours(time1, time2): Long   // whole hours / minutes / seconds
DateTime::diffMinutes(time1, time2): Long
DateTime::diffSeconds(time1, time2): Long
DateTime::isBefore(time1, time2): Boolean
DateTime::isAfter(time1, time2): Boolean
DateTime::dayName(epochMillis): String    // "Friday"  (English, locale-independent)
DateTime::monthName(epochMillis): String  // "March"
```

### Factory

```
DateTime::of(year, month, day): Long
DateTime::of(year, month, day, hour, minute, second): Long
DateTime::startOfDay(epochMillis): Long
DateTime::endOfDay(epochMillis): Long
```

### Example

```
val now: Long = DateTime::now();
IO::println("Today: " + DateTime::format(now, "yyyy-MM-dd"));

val tomorrow: Long = DateTime::addDays(now, 1);
IO::println("Tomorrow: " + DateTime::format(tomorrow));

val birthday: Long = DateTime::of(1990, 5, 15);
val age: Int = DateTime::diffDays(now, birthday) / 365;
```

---

## Regex

Regular expression utilities.

### Matching

```
Regex::matches(input, pattern): Boolean   // Entire string matches
Regex::find(input, pattern): Boolean      // Pattern found anywhere
```

### Extraction

```
Regex::findAll(input, pattern): List[String]
Regex::findFirst(input, pattern): String
Regex::groups(input, pattern): List[String]   // First match groups
Regex::groupsAll(input, pattern): List[List[String]]  // All matches groups
```

### Replacement

```
Regex::replace(input, pattern, replacement): String
Regex::replaceFirst(input, pattern, replacement): String
```

### Splitting

```
Regex::split(input, pattern): List[String]
Regex::split(input, pattern, limit): List[String]
```

### Utility

```
Regex::quote(literal): String    // Escape special characters
Regex::isValid(pattern): Boolean
```

### Pattern literal overloads

A `re"..."` literal compiles to a `java.util.regex.Pattern`, not a `String`.
Every matching/extraction/replacement/splitting method above also has an
overload that takes a compiled `Pattern` directly, so a `re"..."` literal can
be passed straight in without going through a `String` pattern:

```
Regex::matches(input, pattern: Pattern): Boolean
Regex::find(input, pattern: Pattern): Boolean
Regex::findAll(input, pattern: Pattern): List[String]
Regex::findFirst(input, pattern: Pattern): String
Regex::groups(input, pattern: Pattern): List[String]
Regex::groupsAll(input, pattern: Pattern): List[List[String]]
Regex::replace(input, pattern: Pattern, replacement): String
Regex::replaceFirst(input, pattern: Pattern, replacement): String
Regex::split(input, pattern: Pattern): List[String]
Regex::split(input, pattern: Pattern, limit): List[String]
```

```
val p = re"[\w.]+@[\w.]+";
val emails: List[String] = Regex::findAll("alice@example.com", p);
```

### Example

```
val text: String = "Email: alice@example.com, bob@test.org";
val emails: List[String] = Regex::findAll(text, "[\\w.]+@[\\w.]+");
// ["alice@example.com", "bob@test.org"]

val masked: String = Regex::replace(text, "@[\\w.]+", "@***");
// "Email: alice@***, bob@***"

if (Regex::matches("hello123", "[a-z]+\\d+")) {
  IO::println("Pattern matched!");
}
```

---

## Maps Module

Map utility functions.

### Construction

```onion
val m: Map[String, Int] = Maps::newMap()
m.put("a", 1)
```

### Access

```onion
Maps::getOrDefault(m, "a", 0)     // 1
Maps::getOrDefault(m, "x", 0)     // 0
```

Result maps preserve insertion order (`LinkedHashMap`).

### Access

```onion
Maps::getOrElse(m, "x", () -> compute())   // lazy default when absent
Maps::keys(m)                              // List of keys, in order
Maps::values(m)                            // List of values, in order
```

### Transformation

```onion
Maps::mapValues(m, (v: Int) -> v * 2)
Maps::mapKeys(m, (k: String) -> k.toUpperCase())
Maps::filterValues(m, (v: Int) -> v > 0)
Maps::filterKeys(m, (k: String) -> k.startsWith("a"))
Maps::filter(m, (k: String, v: Int) -> v > 0)        // key+value predicate
Maps::invert(m)                                       // swap keys and values
Maps::toList(m, (k: String, v: Int) -> k + "=" + v)   // entries -> List
Maps::forEach(m, (k: String, v: Int) -> println(k))
```

### Querying

```onion
Maps::count(m, (k: String, v: Int) -> v > 0)
Maps::anyEntry(m, (k: String, v: Int) -> v < 0)
Maps::allEntries(m, (k: String, v: Int) -> v >= 0)
```

### Building from lists

```onion
Maps::groupBy(items, (x: Item) -> x.category())   // Map[K, List[Item]]
Maps::countBy(items, (x: Item) -> x.category())   // Map[K, Integer] frequency
```

### Combination

```onion
val merged = Maps::merge(a, b)                          // b wins on collisions
Maps::mergeWith(a, b, (x: Int, y: Int) -> x + y)        // combine on collision
Maps::update(m, "a", (v: Int) -> v + 1)                 // functional update
```

---

## Sets Module

Set utility functions. Result sets preserve insertion order (`LinkedHashSet`),
and the set-algebra operations are null-safe.

### Construction

```onion
val a = Sets::of(1, 2, 3)
val b = Sets::newSet[Int]()
val c = Sets::fromList([1, 1, 2, 3])   // distinct, first-seen order
Sets::toList(a)                        // back to a List
```

### Set algebra

```onion
Sets::union(a, b)
Sets::intersection(a, b)
Sets::difference(a, b)
Sets::symmetricDifference(a, b)        // in exactly one of the two
Sets::containsAll(a, b)
Sets::isSubsetOf(a, b)                 // every element of a is in b
Sets::isSupersetOf(a, b)
Sets::isDisjoint(a, b)                 // share no elements
```

### Functional operations

```onion
Sets::map(a, (x: Int) -> x * 2)
Sets::filter(a, (x: Int) -> x > 1)
Sets::forEach(a, (x: Int) -> println(x))
Sets::count(a, (x: Int) -> x > 1)
Sets::any(a, (x: Int) -> x > 2)
Sets::all(a, (x: Int) -> x > 0)
Sets::find(a, (x: Int) -> x > 2)       // matching element or null
```

---

## Next Steps

- [Language Specification](specification.md) - Formal language spec
- [Compiler Architecture](compiler-architecture.md) - Compiler internals
- [Java Interoperability](../guide/java-interop.md) - Using Java libraries
