# Functional Programming Examples

Examples demonstrating functional programming features in Onion.

## Lambda Expressions

Basic lambda syntax:

```onion
// Simple lambda
def double = (x :Int) -> { return x * 2; }
IO::println(double.call(5))  // 10

// Lambda with multiple parameters
def add = (x :Int, y :Int) -> { return x + y; }
IO::println(add.call(3, 7))  // 10

// Lambda with no parameters
def greet = () -> { IO::println("Hello!") }
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

def i :Int = 0
def filter = (line :String) -> {
  i = i + 1
  return line + " (" + i + ")";
}

def reader :BufferedReader = new BufferedReader(
  new StringReader("First\nSecond\nThird")
)

def line :String = null
while (line = reader.readLine()) != null {
  IO::println(filter.call(line)$String)
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
def makeCounter {
  def count :Int = 0
  () -> {
    count = count + 1
    return count;
  }
}

def counter1 = makeCounter()
def counter2 = makeCounter()

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
import {
  java.io.BufferedReader;
  java.io.InputStreamReader;
  java.lang.NumberFormatException;
}

def fact(n :Int) :Int {
  if n < 0 {
    IO::println("Illegal argument")
    return 0
  }
  if n == 0 {
    return 1
  }
  n * fact(n - 1)
}

def reader :BufferedReader = new BufferedReader(
  new InputStreamReader(System::in)
)

def line :String = null
while (line = IO::input("Enter number: ")) != null {
  try {
    def value :Int = JInteger::parseInt(line)
    def result :Int = fact(value)
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
    def total :Int = 0
    def files :File[] = file.listFiles()

    if files != null {
      foreach f :File in files {
        total = total + countLines(f$File)
      }
    }

    return total
  }

  if !file.name.endsWith(".on") {
    return 0
  }

  def reader :BufferedReader = new BufferedReader(
    new FileReader(file)
  )

  def count :Int = 0
  def line :String = null
  while (line = reader.readLine()) != null {
    count = count + 1
  }

  reader.close()
  IO::println(file.name + ": " + count)
  count
}

// Usage
def dir :File = new File("src")
def total :Int = countLines(dir)
IO::println("Total lines: " + total)
```

**Topics:**
- Recursive directory traversal
- File operations
- Conditional logic

## Filter Function

Higher-order function that filters a list:

```onion
import { java.util.ArrayList; }

def filter(items :String[], predicate :Function1) :ArrayList {
  def result :ArrayList = new ArrayList

  foreach item :String in items {
    def keep :Boolean = predicate.call(item)$Boolean
    if keep {
      result << item
    }
  }

  result
}

// Usage
def logs :String[] = [
  "INFO: Started",
  "ERROR: Failed",
  "INFO: Processing",
  "ERROR: Timeout",
  "INFO: Complete"
]

def isError = (line :String) -> { return line.startsWith("ERROR"); }

def errors :ArrayList = filter(logs, isError)

foreach error :Object in errors {
  IO::println(error$String)
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

def map(items :String[], transform :Function1) :ArrayList {
  def result :ArrayList = new ArrayList

  foreach item :String in items {
    def transformed :String = transform.call(item)$String
    result << transformed
  }

  result
}

// Usage
def words :String[] = ["hello", "world", "onion"]
def toUpper = (s :String) -> { return s.toUpperCase(); }

def upper :ArrayList = map(words, toUpper)

foreach word :Object in upper {
  IO::println(word$String)
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
def reduce(items :Int[], operation :Function2, initial :Int) :Int {
  def accumulator :Int = initial

  foreach item :Int in items {
    accumulator = operation.call(accumulator, item)$Int
  }

  accumulator
}

// Sum
def numbers :Int[] = [1, 2, 3, 4, 5]
def sum = (acc :Int, n :Int) -> { return acc + n; }
def total :Int = reduce(numbers, sum, 0)
IO::println("Sum: " + total)  // 15

// Product
def product = (acc :Int, n :Int) -> { return acc * n; }
def result :Int = reduce(numbers, product, 1)
IO::println("Product: " + result)  // 120
```

## Composing Functions

Combine multiple operations:

```onion
def compose(f :Function1, g :Function1) :Function1 {
  (x :Object) -> { return f.call(g.call(x)); }
}

// Define functions
def addTen = (x :Int) -> { return x + 10; }
def double = (x :Int) -> { return x * 2; }

// Compose: double then add 10
def composed = compose(addTen, double)

IO::println(composed.call(5)$Int)  // (5 * 2) + 10 = 20
```

## Currying

Transform multi-parameter functions:

```onion
def add(x :Int) :Function1 {
  (y :Int) -> { return x + y; }
}

def add5 = add(5)
def add10 = add(10)

IO::println(add5.call(3)$Int)   // 8
IO::println(add10.call(3)$Int)  // 13
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
  def reader :BufferedReader = new BufferedReader(
    new FileReader(filename)
  )

  def errorCount :Int = 0
  def warningCount :Int = 0
  def infoCount :Int = 0

  def isError = (line :String) -> { return line.startsWith("ERROR"); }
  def isWarning = (line :String) -> { return line.startsWith("WARNING"); }
  def isInfo = (line :String) -> { return line.startsWith("INFO"); }

  def line :String = null
  while (line = reader.readLine()) != null {
    if isError.call(line)$Boolean {
      errorCount = errorCount + 1
    } else if isWarning.call(line)$Boolean {
      warningCount = warningCount + 1
    } else if isInfo.call(line)$Boolean {
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
