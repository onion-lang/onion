# Control Flow

Onion provides several control flow constructs for conditionals, loops, and pattern matching.

## Conditional Statements

### Control Flow as Expressions

Control-flow forms are expressions. Blocks evaluate to the last expression, and `return`/`throw`/`break`/`continue` are typed as bottom (they never produce a value). Loops evaluate to `void`.

```onion
val label: String = if age >= 18 { "Adult" } else { "Minor" }

val word: String = {
  val base = "a"
  base + "!"
}
```

### If-Else

Basic conditional execution:

```onion
val age: Int = 18

if age >= 18 {
  println("Adult")
} else {
  println("Minor")
}
```

### If-Else If-Else

Multiple conditions:

```onion
val score: Int = 85

if score >= 90 {
  println("Grade: A")
} else if score >= 80 {
  println("Grade: B")
} else if score >= 70 {
  println("Grade: C")
} else if score >= 60 {
  println("Grade: D")
} else {
  println("Grade: F")
}
```

### Assignment in Conditions

You can assign and test in one expression:

```onion
import {
  java.io.BufferedReader;
  java.io.InputStreamReader;
}

val reader: BufferedReader = new BufferedReader(
  new InputStreamReader(System::in)
)

var line: String = null
while (line = reader.readLine()) != null {
  println("Read: " + line)
}
```

## Loops

### While Loop

Execute while a condition is true:

```onion
var count: Int = 0
while count < 5 {
  println("Count: " + count)
  count = count + 1
}
```

Output:
```
Count: 0
Count: 1
Count: 2
Count: 3
Count: 4
```

### For Loop

Traditional C-style for loop:

```onion
for var i: Int = 0; i < 10; i = i + 1 {
  println(i)
}
```

Syntax: `for init; condition; update { body }`

### Do-While Loop

Executes the body at least once, checking the condition afterwards.
`continue` jumps to the check, `break` exits:

```onion
var i = 0
do {
  i = i + 1
} while i < 3
```

### Foreach Loop

Iterate over arrays and collections:

```onion
val fruits: String[] = new String[3]
fruits[0] = "apple"
fruits[1] = "banana"
fruits[2] = "cherry"

foreach fruit :String in fruits {
  println("Fruit: " + fruit)
}
```

With collections:

```onion
import { java.util.ArrayList; }

val list: ArrayList[String] = new ArrayList[String]()
list << "First"
list << "Second"
list << "Third"

foreach item :Object in list {
  println((item as String))
}
```

Ranges iterate without building an array — `a..b` is inclusive,
`a..<b` exclusive — and maps destructure into key/value pairs:

```onion
foreach i: Int in 0..<5 { println("" + i) }     // 0 1 2 3 4

val ages = ["alice": 30, "bob": 25]
foreach (name, age) in ages {
  println(name + " is " + age)
}
```

## Pattern Matching

### Select Statement

Switch-style pattern matching with multiple values per case:

```onion
val day: Int = 3

select day {
  case 1:
    println("Monday")
  case 2:
    println("Tuesday")
  case 3:
    println("Wednesday")
  case 4:
    println("Thursday")
  case 5:
    println("Friday")
  case 6, 7:
    println("Weekend")
  else:
    println("Invalid day")
}
```

Select can also produce a value:

```onion
val grade: String = select score {
  case 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100:
    "A"
  case 80, 81, 82, 83, 84, 85, 86, 87, 88, 89:
    "B"
  else:
    "F"
}
```

### Multiple Values Per Case

Group multiple values in a single case:

```onion
val num: Int = (Math::random() * 10) as Int

select num {
  case 0, 1, 2, 3:
    println("Low")
  case 4, 5, 6:
    println("Medium")
  case 7, 8, 9:
    println("High")
  else:
    println("Other")
}
```

### Select with Expressions

```onion
val score: Int = 85
var grade: String = null

select score {
  case 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100:
    grade = "A"
  case 80, 81, 82, 83, 84, 85, 86, 87, 88, 89:
    grade = "B"
  case 70, 71, 72, 73, 74, 75, 76, 77, 78, 79:
    grade = "C"
  else:
    grade = "F"
}

println("Grade: " + grade)
```

### Type Patterns, Destructuring and Guards

`select` also matches on types and record structure. A type pattern
binds a new name with the narrowed type in its branch:

```onion
select o {
  case s is String:  println("string of length " + s.length())
  case i is Integer: println("int " + i)
  else:              println("something else")
}
```

Records destructure positionally (`_` ignores a component), and `when`
adds a guard to any pattern:

```onion
record Circle(r: Int) <: Shape
record Rect(w: Int, h: Int) <: Shape

select shape {
  case Circle(r) when r > 10: println("big circle")
  case Circle(r):             println("circle " + r)
  case Rect(w, _):            println("rect of width " + w)
}
```

When the matched value's type is a `sealed` interface, the compiler
checks exhaustiveness (E0042) and lists any uncovered subtypes — an
`else` branch is unnecessary once every case is handled.

### Regex Patterns

A regex literal is a first-class pattern: `case re"..." (g1, g2)` matches a
`String` subject against an **anchored** regex (the whole string must match)
and binds its capture groups as `String` locals. Guards can use the bound
groups:

```onion
select request {
  case re"GET (\S+) HTTP/(\S+)" (path, ver): handleGet(path, ver)
  case re"POST (\S+)" (path):                handlePost(path)
  case re"(\d+)-(\d+)" (lo, hi) when Integer::parseInt(lo) < 20: low(lo, hi)
  case re"PING":                             pong()   // exact-match test
  else:                                      bad()
}
```

Because the pattern is a literal, it is validated at compile time: a
malformed regex is E0059 and a capture-group / binding count mismatch is
E0060. The body is raw — `\d` needs no double escaping.

## Do Notation (Monadic Composition)

Do notation provides a clean syntax for chaining operations on monadic types like `Option`, `Result`, and `Future`. It desugars to `flatMap`/`map` calls.

### Basic Syntax

```onion
do[MonadType] {
  x <- expr1   // Bind: extract value from monad
  y <- expr2   // Chain multiple bindings
  ret result   // Return: wrap result in monad
}
```

### With Option

Chain operations that might fail:

```onion
def findUser(id: Int): Option[User] { /* ... */ }
def getEmail(user: User): Option[String] { /* ... */ }

val email: Option[String] = do[Option] {
  user <- findUser(42)
  email <- getEmail(user)
  ret email
}

// Equivalent to:
// findUser(42).flatMap(user => getEmail(user).map(email => email))
```

### With Result

Handle errors elegantly:

```onion
def parseNumber(s: String): Result[Int, String] { /* ... */ }
def divide(a: Int, b: Int): Result[Int, String] { /* ... */ }

val result: Result[Int, String] = do[Result] {
  x <- parseNumber("10")
  y <- parseNumber("2")
  z <- divide(x, y)
  ret z
}
```

### With Future

Compose async operations:

```onion
def fetchUser(id: Int): Future[User] { /* ... */ }
def fetchPosts(user: User): Future[List[String]] { /* ... */ }

val posts: Future[List[String]] = do[Future] {
  user <- fetchUser(42)
  posts <- fetchPosts(user)
  ret posts
}
```

### With List (Comprehensions)

`do[List]` iterates every combination of its bindings — a list
comprehension:

```onion
val pairs = do[List] {
  x <- [1, 2]
  y <- ["a", "b"]
  ret x + y
}
// [1a, 1b, 2a, 2b]
```

### Mixing with Regular Expressions

You can mix bindings with regular expressions and declarations:

```onion
val result: Option[Int] = do[Option] {
  x <- parseNumber(input)
  val doubled = x * 2        // Local declaration
  y <- validateRange(doubled)
  println("Valid: " + y) // Side effect
  ret y
}
```

### Error Short-Circuiting

If any step fails, the entire do block short-circuits:

```onion
val result: Option[Int] = do[Option] {
  x <- Option::some(10)    // Success
  y <- Option::none()      // Fails here - short circuits
  z <- Option::some(30)    // Never executed
  ret x + y + z
}
// result is Option::none()
```

## Break and Continue

### Break Statement

Exit a loop early:

```onion
var i: Int = 0
while true {
  if i >= 5 {
    break
  }
  println(i)
  i = i + 1
}
```

### Continue Statement

Skip to the next iteration:

```onion
for var i: Int = 0; i < 10; i = i + 1 {
  if i % 2 == 0 {
    continue  // Skip even numbers
  }
  println(i)  // Print odd numbers only
}
```

Output: `1 3 5 7 9`

## Exception Handling

### Try-Catch

Handle exceptions with try-catch blocks:

```onion
import { java.lang.NumberFormatException; }

val input: String = "not a number"

try {
  val number: Int = JInteger::parseInt(input)
  println("Parsed: " + number)
} catch e :NumberFormatException {
  println("Invalid number: " + e.getMessage())
}
```

### Multiple Catch Blocks

```onion
import {
  java.io.FileReader;
  java.io.FileNotFoundException;
  java.io.IOException;
}

try {
  val reader: FileReader = new FileReader("file.txt")
  // Use reader...
} catch e :FileNotFoundException {
  println("File not found: " + e.getMessage())
} catch e :IOException {
  println("IO error: " + e.getMessage())
}
```

### Multi-Catch

One handler for several exception types:

```onion
try {
  risky()
} catch e: IllegalArgumentException | IllegalStateException {
  println("bad state or argument: " + e.getMessage())
} catch e: Exception {
  println("anything else")
}
```

### Try-With-Resources

Resources declared in parentheses after `try` are closed automatically
when the block exits — normally or by exception. Multiple resources
close in reverse declaration order; `catch`/`finally` combine as usual:

```onion
import { java.io.* }

try (val reader = new BufferedReader(new FileReader("file.txt"))) {
  println(reader.readLine())
} catch e: IOException {
  println("IO error: " + e.getMessage())
}

try (val a = openA(); val b = openB()) {
  // b closes first, then a
}
```

The resource type needs a `close(): void` method (`AutoCloseable` /
`Closeable` from Java interop work directly).

### Accessing Exception Information

```onion
try {
  val result: Int = 10 / 0
} catch e :ArithmeticException {
  println("Error: " + e.getMessage())
  println("Type: " + e.getClass().getName())
}
```

## Control Flow Best Practices

### Early Return

Use early returns to reduce nesting:

```onion
def processData(data :String) :Int {
  if data == null {
    return 0
  }

  if data.length() == 0 {
    return 0
  }

  // Process data...
  return data.length()
}
```

### Guard Clauses

Validate preconditions early:

```onion
def divide(a :Int, b :Int) :Int {
  if b == 0 {
    println("Error: Division by zero")
    return 0
  }

  return a / b
}
```

### Avoid Deep Nesting

Prefer flat code over deeply nested conditions:

```onion
// Bad: Deep nesting
if condition1 {
  if condition2 {
    if condition3 {
      // Code here
    }
  }
}

// Good: Early returns or guard clauses
if !condition1 { return }
if !condition2 { return }
if !condition3 { return }
// Code here
```

## Next Steps

- [Functions](functions.md) - Function definitions and lambdas
- [Classes and Objects](classes-and-objects.md) - OOP in Onion
- [Examples](../examples/basic.md) - See control flow in action
