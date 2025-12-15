# Functional Programming Examples

Examples demonstrating functional programming features in Onion.

## Lambda Expressions

Basic lambda syntax:

```onion
// Simple lambda
val double: (Int) -> Int = (x: Int) -> { return x * 2; }
IO::println(double.call(5))  // 10

// Lambda with multiple parameters
val add: (Int, Int) -> Int = (x: Int, y: Int) -> { return x + y; }
IO::println(add.call(3, 7))  // 10

// Lambda with no parameters
val greet: () -> String = () -> { IO::println("Hello!"); return "done"; }
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
  IO::println(filter.call(line))
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

IO::println(counter1.call())  // 1
IO::println(counter1.call())  // 2
IO::println(counter2.call())  // 1
IO::println(counter1.call())  // 3
IO::println(counter2.call())  // 2
```

Each counter maintains its own `count` variable.

## Recursion

**File: `Factorial.on`**
```onion
import { java.lang.NumberFormatException; }

def fact(n :Int) :Int {
  if n < 0 {
    IO::println("Illegal argument")
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
    IO::println("Factorial: " + result)
  } catch e :NumberFormatException {
    IO::println("Invalid number")
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
  IO::println(file.name + ": " + count)
  return count
}

// Usage
val dir: File = new File("src")
val total: Int = countLines(dir)
IO::println("Total lines: " + total)
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

def filter(items: List, predicate: (String) -> Boolean): ArrayList {
  val result: ArrayList = new ArrayList

  foreach item: String in items {
    if predicate.call(item) {
      result << item
    }
  }

  return result
}

// Usage
val logs: List = [
  "INFO: Started",
  "ERROR: Failed",
  "INFO: Processing",
  "ERROR: Timeout",
  "INFO: Complete"
]

val isError: (String) -> Boolean = (line: String) -> { return line.startsWith("ERROR"); }

val errors: ArrayList = filter(logs, isError)

foreach error: String in errors {
  IO::println(error)
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

def map(items: java.util.List, transform: (String) -> String): ArrayList {
  val result: ArrayList = new ArrayList

  foreach item: String in items {
    result << transform.call(item)
  }

  return result
}

// Usage
val words: java.util.List = ["hello", "world", "onion"]
val toUpper: (String) -> String = (s: String) -> { return s.toUpperCase(); }

val upper: ArrayList = map(words, toUpper)

foreach word: String in upper {
  IO::println(word)
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

def reduce(items: List, operation: (Int, Int) -> Int, initial: Int): Int {
  var accumulator: Int = initial

  foreach item: Int in items {
    accumulator = operation.call(accumulator, item)
  }

  return accumulator
}

// Sum
val numbers: List = [1, 2, 3, 4, 5]
val sum: (Int, Int) -> Int = (acc: Int, n: Int) -> { return acc + n; }
val total: Int = reduce(numbers, sum, 0)
IO::println("Sum: " + total)  // 15

// Product
val product: (Int, Int) -> Int = (acc: Int, n: Int) -> { return acc * n; }
val result: Int = reduce(numbers, product, 1)
IO::println("Product: " + result)  // 120
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

IO::println(composed.call(5))  // (5 * 2) + 10 = 20
```

## Currying

Transform multi-parameter functions:

```onion
def add(x: Int): (Int) -> Int = (y: Int) -> { return x + y; }

val add5: (Int) -> Int = add(5)
val add10: (Int) -> Int = add(10)

IO::println(add5.call(3))   // 8
IO::println(add10.call(3))  // 13
```

## Practical: Log Analyzer

Complete example combining functional concepts:

```onion
import {
  java.io.BufferedReader;
  java.io.FileReader;
  java.util.ArrayList;
}

def analyzeLog(filename :String) {
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

  IO::println("Errors: " + errorCount)
  IO::println("Warnings: " + warningCount)
  IO::println("Info: " + infoCount)
}

analyzeLog("app.log")
```

## Next Steps

- [Lambda Expressions Guide](../guide/lambda-expressions.md) - Detailed lambda documentation
- [Functions Guide](../guide/functions.md) - Function definitions
- [Basic Examples](basic.md) - Simpler programs
