# Standard Library

Onion's standard library consists of built-in modules and interfaces for common functionality.

## Modules at a glance

| Area | Modules |
|------|---------|
| **I/O & system** | `IO` (console), `Files` (files + paths), `System`, `Proc` (subprocesses), `Args` (CLI) |
| **Collections** | `Colls` (lists: map/filter/fold, chunked/windowed, sumBy/maxBy), `Iterables`, `Maps`, `Sets` |
| **Text** | `Strings` (case, split, pad, parse), `Text` (wrap/indent/table), `Regex` |
| **Numbers** | `Math`, `Stats` (sum/average/median/stddev), `Format` (grouping, bytes, durations) |
| **Data formats** | `Json`, `Yaml`, `Csv` |
| **Encoding** | `Codec` (base64/hex/url), `Hash` (md5/sha256/…) |
| **Functional** | `Option`, `Result`, `Future` |
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
```

### Rand::shuffle

Shuffle an array, returning a shuffled list:

```onion
val cards: String[] = new String[]{"A", "B", "C", "D"}
val shuffled: List[String] = Rand::shuffle(cards)
```

## Assert Module

Testing assertions via `onion.Assert`. Throws `AssertionError` on failure.

### Basic Assertions

```onion
Assert::assertTrue(x > 0)
Assert::assertFalse(list.isEmpty())
Assert::assertEquals(expected, actual)
Assert::assertNotEquals(a, b)
```

### Null Assertions

```onion
Assert::assertNotNull(result)
Assert::assertNull(errorMessage)
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
val elapsedNs: Long = Timing::elapsedNanos(start)    // Elapsed in nanoseconds
val elapsedMs: Double = Timing::elapsedMs(start)     // Elapsed in milliseconds
```

### Formatting Time

```onion
val nanos: Long = 1234567890L
val formatted: String = Timing::formatNanos(nanos)   // "1.23s"
// Output formats: "123ns", "45.67μs", "12.34ms", "1.23s"
```

### Sleep

```onion
Timing::sleep(1000L)  // Sleep for 1000 milliseconds
```

### Measuring Function Execution

```onion
// Measure and print execution time, return result
val result: Int = Timing::measure(() -> { return expensiveOperation(); })
// Prints: "Elapsed: 123.45ms"

// Get execution time in nanoseconds without printing
val timeNanos: Long = Timing::time(() -> { return expensiveOperation(); })
```

## Strings Module

String utilities (`onion.Strings`, auto-imported):

```onion
Strings::split("a,b,c", ",")          // String[] {"a","b","c"}
Strings::join(parts, "-")             // arrays or Lists
Strings::upper(s) / Strings::lower(s) / Strings::trim(s)
Strings::replace(s, "a", "b") / Strings::replaceRegex(s, "[0-9]+", "#")
Strings::startsWith(s, p) / Strings::endsWith(s, p) / Strings::contains(s, sub)
Strings::padLeft(s, 8, '0') / Strings::padRight(s, 8, ' ') / Strings::repeat(s, 3)
```

Case and inspection helpers:

```onion
Strings::capitalize("hello")             // "Hello"
Strings::capitalizeWords("a b c")        // "A B C"
Strings::equalsIgnoreCase(a, b) / Strings::containsIgnoreCase(s, sub)
Strings::count("banana", "a")            // 3
```

Shaping and decomposition:

```onion
Strings::removePrefix("unhappy", "un")   // "happy"
Strings::removeSuffix("running", "ing")  // "runn"
Strings::truncate("hello world", 8, "...")   // "hello..."
Strings::center("hi", 6, '*')            // "**hi**"
Strings::ifBlank("   ", "default")       // "default"
Strings::words("  a  b  c ")             // String[] {"a","b","c"}
Strings::chars("abc")                    // List ["a","b","c"]
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
Files::readLines("path.txt")           // String[]
Files::writeText("out.txt", content)
Files::readBytes(path) / Files::writeBytes(path, bytes)
Files::list("dir")                     // List of entry names
Files::glob("dir", "*.on")             // glob-matched names
Files::delete(path) / Files::exists(path)
```

Path helpers — file names, parents, joining, and extensions:

```onion
Files::getFileName("a/b/c.txt")        // "c.txt"
Files::getParent("a/b/c.txt")          // "a/b"
Files::joinPath("a/b", "c.txt")        // "a/b/c.txt"
Files::ext("report.txt")               // "txt"   (extension, keyword-safe name)
Files::stem("report.txt")              // "report"
Files::withExtension("report.txt", "md")   // "report.md"
```

## Json Module

JSON parsing and serialization (`onion.Json`):

```onion
val obj = Json::parse("{\"name\": \"kota\"}")
Json::getString(obj, "name")           // typed accessors: getInt/getDouble/getBoolean
Json::stringify(obj) / Json::stringifyPretty(obj)

// Navigable wrapper: index with [] and convert with as-methods
val v = Json::value(jsonText)
v["users"][0]["name"].asString()
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
double-quoted. Numbers and booleans are rendered verbatim.

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
record Config(host: String, port: Int, debug: Boolean) derive!(Yaml)

val cfg = new Config("localhost", 8080, false)
val yaml = Config::toYaml(cfg)
// "host: localhost\nport: 8080\ndebug: false\n"

val cfg2 = Config::fromYaml(yaml)   // Config? — null on parse/convert failure
```

`derive!(Json, Yaml)` is also valid; both formats share the internal
`toMap` / `fromMap` core, so there is no duplication:

```onion
record User(name: String, age: Int) derive!(Json, Yaml)

val u = new User("ko", 3)
val viaJson = User::fromJson(User::toJson(u))   // == u
val viaYaml = User::fromYaml(User::toYaml(u))  // == u
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
Http::get(url, headers): String    // headers: ["Key1", "Value1", ...]
```

### POST Requests

```
Http::post(url, body): String
Http::postJson(url, jsonBody): String    // Sets Content-Type: application/json
Http::post(url, body, headers): String
```

### Other Methods

```
Http::put(url, body): String
Http::delete(url): String
```

### URL Utilities

```
Http::urlEncode(str): String
Http::urlDecode(str): String
Http::buildQuery(params): String    // params: ["key1", "val1", ...]
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
Regex::findAll(input, pattern): String[]
Regex::findFirst(input, pattern): String
Regex::groups(input, pattern): String[]       // First match groups
Regex::groupsAll(input, pattern): String[][]  // All matches groups
```

### Replacement

```
Regex::replace(input, pattern, replacement): String
Regex::replaceFirst(input, pattern, replacement): String
```

### Splitting

```
Regex::split(input, pattern): String[]
Regex::split(input, pattern, limit): String[]
```

### Utility

```
Regex::quote(literal): String    // Escape special characters
Regex::isValid(pattern): Boolean
```

### Example

```
val text: String = "Email: alice@example.com, bob@test.org";
val emails: String[] = Regex::findAll(text, "[\\w.]+@[\\w.]+");
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
