# Functional Programming Examples

Examples demonstrating functional programming features in Onion.

## Lambda Expressions

Basic lambda syntax:

```onion
// Simple lambda
val double: (Int) -> Int = (x: Int) -> { return x * 2; }
println(double.call(5))  // 10

// Lambda with multiple parameters
val add: (Int, Int) -> Int = (x: Int, y: Int) -> { return x + y; }
println(add.call(3, 7))  // 10

// Lambda with no parameters
val greet: () -> String = () -> { println("Hello!"); return "done"; }
greet.call()
```

## Closures

Lambdas that capture variables:

**File: `LineFilter.on`**
```onion
import {
  java.io.BufferedReader;
  java.io.FileReader;
  java.io.StringReader;
}

var i: Int = 0
val filter: (String) -> String = (line: String) -> {
  i = i + 1
  return line + " (" + i + ")";
}

val reader: BufferedReader = new BufferedReader(
  new StringReader("First\nSecond\nThird")
)

var line: String = null
while (line = reader.readLine()) != null {
  println(filter.call(line))
}
```

**Output:**
```
First (1)
Second (2)
Third (3)
```

**Topics:**
- Closure capturing variable `i`
- Lambda returning modified string
- Post-increment in closure

## Counter Factory

Creating multiple independent counters:

```onion
def makeCounter(): () -> Int {
  var count: Int = 0
  return () -> {
    count = count + 1
    return count;
  };
}

val counter1: () -> Int = makeCounter()
val counter2: () -> Int = makeCounter()

println(counter1.call())  // 1
println(counter1.call())  // 2
println(counter2.call())  // 1
println(counter1.call())  // 3
println(counter2.call())  // 2
```

Each counter maintains its own `count` variable.

## Recursion

**File: `Factorial.on`**
```onion
import { java.lang.NumberFormatException; }

def fact(n :Int) :Int {
  if n < 0 {
    println("Illegal argument")
    return 0
  }
  if n == 0 {
    return 1
  }
  return n * fact(n - 1)
}

var line: String = null
while (line = IO::readln("Enter number: ")) != null {
  try {
    val value: Int = JInteger::parseInt(line)
    val result: Int = fact(value)
    println("Factorial: " + result)
  } catch e :NumberFormatException {
    println("Invalid number")
  }
}
```

**Topics:**
- Recursive function calls
- Base case handling
- Try-catch for error handling

## File Line Counter with Recursion

**File: `LineCounter.on`** (simplified)
```onion
import {
  java.io.File;
  java.io.FileReader;
  java.io.BufferedReader;
}

def countLines(file :File) :Int {
  if file == null {
    return 0
  }

  if file.isDirectory() {
    var total: Int = 0
    val files: File[] = file.listFiles()

    if files != null {
      foreach f :File in files {
        total = total + countLines(f)
      }
    }

    return total
  }

  if !file.name.endsWith(".on") {
    return 0
  }

  val reader: BufferedReader = new BufferedReader(
    new FileReader(file)
  )

  var count: Int = 0
  var line: String = null
  while (line = reader.readLine()) != null {
    count = count + 1
  }

  reader.close()
  println(file.name + ": " + count)
  return count
}

// Usage
val dir: File = new File("src")
val total: Int = countLines(dir)
println("Total lines: " + total)
```

**Topics:**
- Recursive directory traversal
- File operations
- Conditional logic

## Filter Function

Higher-order function that filters a list:

```onion
import {
  java.util.ArrayList;
  java.util.List;
}

def filter(items: List[String], predicate: (String) -> Boolean): ArrayList[String] {
  val result: ArrayList[String] = new ArrayList[String]

  foreach item: String in items {
    if predicate.call(item) {
      result << item
    }
  }

  return result
}

// Usage
val logs: List[String] = [
  "INFO: Started",
  "ERROR: Failed",
  "INFO: Processing",
  "ERROR: Timeout",
  "INFO: Complete"
]

val isError: (String) -> Boolean = (line: String) -> { return line.startsWith("ERROR"); }

val errors: ArrayList[String] = filter(logs, isError)

foreach error: String in errors {
  println(error)
}
```

**Output:**
```
ERROR: Failed
ERROR: Timeout
```

## Map Function

Transform each element in a collection:

```onion
import { java.util.ArrayList; }

def map(items: java.util.List[String], transform: (String) -> String): ArrayList[String] {
  val result: ArrayList[String] = new ArrayList[String]

  foreach item: String in items {
    result << transform.call(item)
  }

  return result
}

// Usage
val words: java.util.List[String] = ["hello", "world", "onion"]
val toUpper: (String) -> String = (s: String) -> { return s.toUpperCase(); }

val upper: ArrayList[String] = map(words, toUpper)

foreach word: String in upper {
  println(word)
}
```

**Output:**
```
HELLO
WORLD
ONION
```

## Reduce Function

Accumulate values:

```onion
import { java.util.List; }

def reduce(items: List[Int], operation: (Int, Int) -> Int, initial: Int): Int {
  var accumulator: Int = initial

  foreach item: Int in items {
    accumulator = operation.call(accumulator, item)
  }

  return accumulator
}

// Sum
val numbers: List[Int] = [1, 2, 3, 4, 5]
val sum: (Int, Int) -> Int = (acc: Int, n: Int) -> { return acc + n; }
val total: Int = reduce(numbers, sum, 0)
println("Sum: " + total)  // 15

// Product
val product: (Int, Int) -> Int = (acc: Int, n: Int) -> { return acc * n; }
val result: Int = reduce(numbers, product, 1)
println("Product: " + result)  // 120
```

## Composing Functions

Combine multiple operations:

```onion
def compose(f: (Int) -> Int, g: (Int) -> Int): (Int) -> Int {
  return (x: Int) -> { return f.call(g.call(x)); }
}

// Define functions
val addTen: (Int) -> Int = (x: Int) -> { return x + 10; }
val double: (Int) -> Int = (x: Int) -> { return x * 2; }

// Compose: double then add 10
val composed: (Int) -> Int = compose(addTen, double)

println(composed.call(5))  // (5 * 2) + 10 = 20
```

## Currying

Transform multi-parameter functions:

```onion
def add(x: Int): (Int) -> Int = (y: Int) -> { return x + y; }

val add5: (Int) -> Int = add(5)
val add10: (Int) -> Int = add(10)

println(add5.call(3))   // 8
println(add10.call(3))  // 13
```

## Practical: Log Analyzer

Complete example combining functional concepts:

```onion
import {
  java.io.BufferedReader;
  java.io.FileReader;
  java.util.ArrayList;
}

def analyzeLog(filename :String): void {
  val reader: BufferedReader = new BufferedReader(
    new FileReader(filename)
  )

  var errorCount: Int = 0
  var warningCount: Int = 0
  var infoCount: Int = 0

  val isError: (String) -> Boolean = (line: String) -> { return line.startsWith("ERROR"); }
  val isWarning: (String) -> Boolean = (line: String) -> { return line.startsWith("WARNING"); }
  val isInfo: (String) -> Boolean = (line: String) -> { return line.startsWith("INFO"); }

  var line: String = null
  while (line = reader.readLine()) != null {
    if isError.call(line) {
      errorCount = errorCount + 1
    } else if isWarning.call(line) {
      warningCount = warningCount + 1
    } else if isInfo.call(line) {
      infoCount = infoCount + 1
    }
  }

  reader.close()

  println("Errors: " + errorCount)
  println("Warnings: " + warningCount)
  println("Info: " + infoCount)
}

analyzeLog("app.log")
```

## Do Notation

Chain monadic operations with clean syntax.

### Option Chaining

```onion
def parseNumber(s: String): Option[Int] {
  try {
    return Option::some(JInteger::parseInt(s));
  } catch e: NumberFormatException {
    return Option::none();
  }
}

// Without do notation
val result1: Option[Int] = parseNumber("10").flatMap((x: Int) -> {
  return parseNumber("20").map((y: Int) -> {
    return x + y;
  });
})

// With do notation - much cleaner!
val result2: Option[Int] = do[Option] {
  x <- parseNumber("10")
  y <- parseNumber("20")
  ret x + y
}

println(result2.getOrElse(0))  // 30
```

### Result Error Handling

```onion
def divide(a: Int, b: Int): Result[Int, String] {
  if b == 0 {
    return Result::err("Division by zero");
  }
  return Result::ok(a / b);
}

val calculation: Result[Int, String] = do[Result] {
  x <- divide(100, 1)    // 100
  y <- divide(x, 5)      // 20
  z <- divide(y, 4)      // 5
  ret z * 2              // 10
}

if calculation.isOk() {
  println("Result: " + calculation.get())
} else {
  println("Error: " + calculation.getError())
}
```

### Nested Do Blocks

```onion
val nested: Option[Int] = do[Option] {
  x <- Option::some(10)
  inner <- do[Option] {
    y <- Option::some(20)
    ret y * 2
  }
  ret x + inner  // 10 + 40 = 50
}
```

## Asynchronous Programming with Future

### Creating Futures

```onion
// Already completed Future
val immediate: Future[Int] = Future::successful(42)

// Async computation
val async: Future[String] = Future::async(() -> {
  Thread::sleep(1000L);
  return "Hello after 1 second";
})

// From throwing operation
val risky: Future[Int] = Future::asyncThrowing(() -> {
  if Math::random() < 0.5 {
    throw new RuntimeException("Bad luck!");
  }
  return 100;
})
```

### Transforming Futures

```onion
val numbers: Future[Int] = Future::successful(10)

// Map: transform the value
val doubled: Future[Int] = numbers.map((x: Int) -> { return x * 2; })

// FlatMap: chain async operations
val chained: Future[String] = numbers.flatMap((x: Int) -> {
  return Future::async(() -> {
    return "Number is: " + x;
  });
})

// Filter: fail if predicate not met
val positive: Future[Int] = numbers.filter((x: Int) -> { return x > 0; })
```

### Error Handling

```onion
val failing: Future[Int] = Future::failed(new RuntimeException("Oops!"))

// Recover with a default value
val recovered: Future[Int] = failing.recover((error: Throwable) -> {
  println("Error: " + error.getMessage());
  return 0;
})

// Recover with another Future
val retried: Future[Int] = failing.recoverWith((error: Throwable) -> {
  return Future::successful(42);
})
```

### Combining Futures

```onion
val f1: Future[Int] = Future::async(() -> { Thread::sleep(100L); return 1; })
val f2: Future[Int] = Future::async(() -> { Thread::sleep(200L); return 2; })

// Wait for all
val all: Future[Object[]] = Future::all(f1, f2)
all.onSuccess((results: Object[]) -> {
  println("Results: " + results[0] + ", " + results[1]);
})

// Race: first to complete wins
val race: Future[Int] = f1.race(f2)
race.onSuccess((winner: Int) -> {
  println("Winner: " + winner);  // Usually f1
})

// Zip two futures
val zipped: Future[Object[]] = f1.zip(f2)
```

### Callbacks

```onion
val future: Future[String] = Future::async(() -> {
  return "Async result";
})

future
  .onSuccess((value: String) -> { println("Success: " + value); })
  .onFailure((error: Throwable) -> { println("Failed: " + error); })
```

### Blocking (Use Sparingly)

```onion
val future: Future[Int] = Future::async(() -> { return 42; })

// Block until complete
val result: Int = future.await()

// Block with timeout (in milliseconds)
val timed: Int = future.awaitTimeout(5000L)

// Get with default on failure
val safe: Int = future.getOrElse(0)
```

### Future with Do Notation

```onion
def fetchUser(id: Int): Future[String] {
  return Future::async(() -> { return "User" + id; });
}

def fetchProfile(name: String): Future[String] {
  return Future::async(() -> { return name + "'s profile"; });
}

val profile: Future[String] = do[Future] {
  user <- fetchUser(42)
  profile <- fetchProfile(user)
  ret profile.toUpperCase()
}

profile.onSuccess((p: String) -> { println(p); })
// Prints: USER42'S PROFILE
```

### Practical: Parallel API Calls

```onion
def fetchFromApi(url: String): Future[String] {
  return Future::async(() -> {
    // Simulate network request
    Thread::sleep((Math::random() * 1000L) as Long);
    return "Data from " + url;
  });
}

// Fire off multiple requests in parallel
val api1: Future[String] = fetchFromApi("/users")
val api2: Future[String] = fetchFromApi("/posts")
val api3: Future[String] = fetchFromApi("/comments")

// Wait for all to complete
Future::all(api1, api2, api3).onSuccess((results: Object[]) -> {
  println("Users: " + results[0])
  println("Posts: " + results[1])
  println("Comments: " + results[2])
})

// Or use do notation with zip
val combined: Future[String] = do[Future] {
  pair1 <- api1.zip(api2)
  data3 <- api3
  ret pair1[0] as String + " | " + pair1[1] as String + " | " + data3
}
```

## Trailing Lambda Syntax

Kotlin-style trailing lambda for cleaner method calls:

```onion
// Traditional syntax
list.map((x: Int) -> { return x * 2; })

// Trailing lambda - more concise
list.map { x => x * 2 }

// Works with any method taking a function last
future.onSuccess { result =>
  println("Got: " + result)
}

// With other arguments before the trailing lambda
api.request("GET", "/users") { response =>
  println(response.body())
}
```

## Primitive Generics with Java Functional Interfaces

Onion can convert lambdas to Java functional interfaces even when the type
arguments are primitive. The compiler boxes the type parameter internally and
generates the bridge method, so you can write `Int` parameters naturally:

```onion
import {
  java.util.Comparator
  java.util.function.Predicate
}

val numbers = Colls::mutableListOf(3, 1, 4, 1, 5, 9, 2, 6)

// Comparator[Int] with primitive Int parameters
Collections::sort(numbers, (a: Int, b: Int) -> a - b)
println(numbers)

// Predicate[Int] with a primitive parameter
val isEven: Predicate[Int] = (n: Int) -> n % 2 == 0
numbers.removeIf(isEven)
println(numbers)
```

This also works for `Supplier[Int]`, `Function[Int, Int]`, and other
single-abstract-method interfaces.

## Next Steps

- [Lambda Expressions Guide](../guide/lambda-expressions.md) - Detailed lambda documentation
- [Functions Guide](../guide/functions.md) - Function definitions
- [Standard Library](../reference/stdlib.md) - Option, Result, Future reference
- [Basic Examples](basic.md) - Simpler programs
