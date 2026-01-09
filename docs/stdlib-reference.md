# Onion Standard Library Reference

This document describes the standard library classes available in Onion programs.

## Overview

| Class | Description |
|-------|-------------|
| `IO` | Console input/output |
| `Strings` | String manipulation |
| `Files` | File operations |
| `Json` | JSON parsing and serialization |
| `Http` | HTTP client |
| `DateTime` | Date and time utilities |
| `Regex` | Regular expressions |

---

## IO

Console input/output utilities.

### Output

```
IO::println(obj)        // Print with newline
IO::print(obj)          // Print without newline
IO::printf(format, args...)  // Formatted output
IO::newline()           // Print empty line
IO::clear()             // Clear screen (ANSI)
```

### Error Output

```
IO::eprintln(obj)       // Print to stderr with newline
IO::eprint(obj)         // Print to stderr
IO::eprintf(format, args...)  // Formatted stderr output
```

### Input

```
IO::readLine(): String       // Read line from stdin
IO::readln(): String         // Alias for readLine
IO::readln(prompt): String   // Read with prompt
IO::input(prompt): String    // Read with prompt
IO::readAll(): String        // Read entire stdin
```

### Type-Safe Input

```
IO::readInt(): Int           // Read integer
IO::readInt(prompt): Int     // Read integer with prompt
IO::readLong(): Long
IO::readDouble(): Double
IO::readBoolean(): Boolean   // Accepts: true/false, yes/no, 1/0
```

### Safe Input (returns null on error)

```
IO::tryReadInt(prompt): Integer
IO::tryReadDouble(prompt): Double
```

### Example

```
IO::println("Hello, World!");
val name: String = IO::readln("Enter name: ");
val age: Int = IO::readInt("Enter age: ");
IO::printf("Hello %s, you are %d years old%n", name, age);
```

---

## Strings

String manipulation utilities.

### Split and Join

```
Strings::split(str, delimiter): String[]
Strings::splitRegex(str, regex): String[]
Strings::join(parts, delimiter): String
```

### Transformation

```
Strings::trim(str): String
Strings::upper(str): String
Strings::lower(str): String
Strings::replace(str, target, replacement): String
Strings::replaceRegex(str, regex, replacement): String
Strings::reverse(str): String
```

### Inspection

```
Strings::startsWith(str, prefix): Boolean
Strings::endsWith(str, suffix): Boolean
Strings::contains(str, substring): Boolean
Strings::isEmpty(str): Boolean
Strings::isBlank(str): Boolean
```

### Extraction

```
Strings::substring(str, start): String
Strings::substring(str, start, end): String
Strings::indexOf(str, substring): Int
Strings::lastIndexOf(str, substring): Int
Strings::lines(str): String[]
```

### Padding and Formatting

```
Strings::padLeft(str, length, padChar): String
Strings::padRight(str, length, padChar): String
Strings::repeat(str, count): String
```

### Example

```
val words: String[] = Strings::split("a,b,c", ",");
val upper: String = Strings::upper("hello");  // "HELLO"
val padded: String = Strings::padLeft("42", 5, '0');  // "00042"
```

---

## Files

File I/O utilities.

### Reading

```
Files::readText(path): String
Files::readText(path, charset): String
Files::readLines(path): String[]
Files::readBytes(path): Byte[]
```

### Writing

```
Files::writeText(path, content): void
Files::writeText(path, content, charset): void
Files::writeLines(path, lines): void
Files::appendText(path, content): void
Files::writeBytes(path, data): void
```

### File Operations

```
Files::exists(path): Boolean
Files::isFile(path): Boolean
Files::isDirectory(path): Boolean
Files::delete(path): Boolean
Files::mkdirs(path): Boolean
Files::listFiles(path): File[]
Files::size(path): Long
```

### Path Operations

```
Files::joinPath(parts...): String
Files::getFileName(path): String
Files::getParent(path): String
Files::getAbsolutePath(path): String
```

### Example

```
val content: String = Files::readText("input.txt");
Files::writeText("output.txt", Strings::upper(content));

if (Files::exists("data.json")) {
  val lines: String[] = Files::readLines("data.json");
  IO::println("Lines: " + lines.length);
}
```

---

## Json

JSON parsing and serialization (no external dependencies).

### Parsing

```
Json::parse(jsonString): Object           // Throws JsonParseException
Json::parseOrNull(jsonString): Object     // Returns null on error
```

### Serialization

```
Json::stringify(obj): String              // Compact JSON
Json::stringifyPretty(obj): String        // Pretty-printed JSON
```

### Type-Safe Accessors

```
Json::asObject(value): Map                // Cast to object (or null)
Json::asArray(value): List                // Cast to array (or null)
Json::get(obj, key): Object               // Get value by key
Json::getInt(obj, key): Int
Json::getDouble(obj, key): Double
Json::getBoolean(obj, key): Boolean
Json::getString(obj, key): String
```

### Example

```
val json: String = "{\"name\": \"Alice\", \"age\": 30}";
val data: Object = Json::parse(json);
val name: String = Json::getString(data, "name");
val age: Int = Json::getInt(data, "age");

val output: String = Json::stringify(data);
```

---

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
DateTime::diff(time1, time2): Long       // Difference in milliseconds
DateTime::diffDays(time1, time2): Int
DateTime::isBefore(time1, time2): Boolean
DateTime::isAfter(time1, time2): Boolean
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

## Function Interfaces

Onion provides function interfaces for closures (Function0 through Function10).

```
interface Function0[R] { def apply(): R }
interface Function1[T, R] { def apply(arg: T): R }
interface Function2[T1, T2, R] { def apply(arg1: T1, arg2: T2): R }
// ... up to Function10
```

### Example

```
val add: Function2[Int, Int, Int] = (a: Int, b: Int) -> { return a + b; };
val result: Int = add.apply(3, 4);  // 7
```

---

## Notes

- All utility classes use static methods accessible via `ClassName::methodName`
- Methods handle null inputs gracefully (returning empty strings/arrays or false)
- File operations may throw `IOException`
- HTTP operations may throw `Exception`
- JSON parsing may throw `JsonParseException` (use `parseOrNull` for safe parsing)
