# Lambda Expressions

Lambda expressions provide a concise way to create anonymous functions in Onion.

## Lambda Syntax

Lambdas use the `(parameters) -> { body }` syntax:

```onion
// Lambda with one parameter
val double: (Int) -> Int = (x: Int) -> { return x * 2; }

// Lambda with multiple parameters
val add: (Int, Int) -> Int = (x: Int, y: Int) -> { return x + y; }

// Lambda with no parameters
val greet: () -> String = () -> { IO::println("Hello!"); return "done"; }
```

## Type Inference

When the target function type is known, parameter types can be omitted:

```onion
val add: (Int, Int) -> Int = (x, y) -> { return x + y; }
```

If the lambda has no explicit function type, the return type is inferred from its body:

```onion
val upper = (s: String) -> { return s.toUpperCase(); }
```

When no target function type is available, parameter types must be explicit.

## Calling Lambdas

Use the `.call()` method to invoke a lambda:

```onion
val square: (Int) -> Int = (x: Int) -> { return x * x; }

val result: Int = square.call(5)  // 25
IO::println(result)
```

## Function Types

Lambdas can be typed using the arrow type syntax `(A, B) -> R`. For a single parameter, parentheses are optional (`A -> R`):

```onion
// Function with 0 parameters
val func0: () -> Int = () -> { return 42; }
val value: Int = func0.call()

// Function with 1 parameter
val func1: Int -> Int = (x: Int) -> { return x * 2; }
val doubled: Int = func1.call(10)

// Function with 2 parameters
val func2: (Int, Int) -> Int = (x: Int, y: Int) -> { return x + y; }
val sum: Int = func2.call(3, 7)
```

## Closures

Lambdas can capture variables from their enclosing scope:

### Simple Closure

```onion
val multiplier: Int = 10
val multiply: (Int) -> Int = (x: Int) -> { return x * multiplier; }

IO::println(multiply.call(5))  // 50
```

### Mutable Closures

Closures can modify captured variables:

```onion
var count: Int = 0
val increment: () -> Int = () -> {
  count = count + 1
  return count;
}

IO::println(increment.call())  // 1
IO::println(increment.call())  // 2
IO::println(increment.call())  // 3
```

### Counter Factory

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
```

## Higher-Order Functions

Functions that accept lambdas as parameters:

### Filter Function

```onion
import {
  java.util.ArrayList;
  java.util.List;
}

def filter(items: List, predicate: (String) -> Boolean): List {
  val result: ArrayList = new ArrayList

  foreach item: String in items {
    if predicate.call(item) {
      result << item
    }
  }

  return result
}

val lines: List = [
  "INFO: System started",
  "ERROR: Connection failed",
  "INFO: Processing data",
  "ERROR: Timeout"
]

val isError: (String) -> Boolean = (line: String) -> { return line.startsWith("ERROR"); }

val errors: List = filter(lines, isError)
foreach error: String in errors {
  IO::println(error)
}
// Output:
// ERROR: Connection failed
// ERROR: Timeout
```

### Map Function

```onion
import {
  java.util.ArrayList;
  java.util.List;
}

def map(items: List, transform: (String) -> String): List {
  val result: ArrayList = new ArrayList

  foreach item: String in items {
    result << transform.call(item)
  }

  return result
}

val words: List = ["hello", "world", "onion"]
val toUpper: (String) -> String = (s: String) -> { return s.toUpperCase(); }

val upper: List = map(words, toUpper)
foreach word: String in upper {
  IO::println(word)
}
// Output:
// HELLO
// WORLD
// ONION
```

### Reduce Function

```onion
import { java.util.List; }

def reduce(items: List, operation: (Int, Int) -> Int, initial: Int): Int {
  var accumulator: Int = initial

  foreach item: Int in items {
    accumulator = operation.call(accumulator, item)
  }

  return accumulator
}

val numbers: List = [1, 2, 3, 4, 5]
val sum: (Int, Int) -> Int = (acc: Int, n: Int) -> { return acc + n; }

val total: Int = reduce(numbers, sum, 0)
IO::println(total)  // 15
```

## Practical Examples

### Line Filtering

```onion
import {
  java.io.BufferedReader;
  java.io.FileReader;
}

def filterFile(filename: String, predicate: (String) -> Boolean) {
  val reader: BufferedReader = new BufferedReader(
    new FileReader(filename)
  )

  var line: String = null
  while (line = reader.readLine()) != null {
    if predicate.call(line) {
      IO::println(line)
    }
  }

  reader.close()
}

// Filter lines starting with ERROR
val errorFilter: (String) -> Boolean = (line: String) -> { return line.startsWith("ERROR"); }

filterFile("logfile.txt", errorFilter)
```

### Custom Sort Comparator

```onion
import {
  java.util.ArrayList;
  java.util.Collections;
  java.util.Comparator;
}

class LambdaComparator <: Comparator {
  val compareFunc: (Object, Object) -> Int

  public:
    def this(func: (Object, Object) -> Int) {
      this.compareFunc = func
    }

    def compare(a: Object, b: Object): Int = this.compareFunc.call(a, b)
}

val list: ArrayList = new ArrayList
list << "banana"
list << "apple"
list << "cherry"

val alphabetical: (Object, Object) -> Int = (a: Object, b: Object) -> {
  val s1: String = a$String
  val s2: String = b$String
  return s1.compareTo(s2);
}

val comparator: LambdaComparator = new LambdaComparator(alphabetical)
Collections::sort(list, comparator)

foreach item :Object in list {
  IO::println(item$String)
}
// Output:
// apple
// banana
// cherry
```

### Event Handlers

```onion
import {
  javax.swing.JButton;
  java.awt.event.ActionListener;
  java.awt.event.ActionEvent;
}

class LambdaActionListener <: ActionListener {
  val handler: (ActionEvent) -> Int

  public:
    def this(h: (ActionEvent) -> Int) {
      this.handler = h
    }

    def actionPerformed(event :ActionEvent) {
      this.handler.call(event)
    }
}

val button: JButton = new JButton("Click me")

val onClick: (ActionEvent) -> Int = (event: ActionEvent) -> {
  IO::println("Button was clicked!")
  return 0
}

val listener: LambdaActionListener = new LambdaActionListener(onClick)
button.addActionListener(listener)
```

## Lambda Best Practices

### Keep Lambdas Short

```onion
// Good: Simple, focused lambda
val isEven: (Int) -> Boolean = (n: Int) -> { return n % 2 == 0; }

// Bad: Complex lambda (use named function instead)
val complex: (Int) -> Int = (n: Int) -> {
  val temp: Int = n * 2
  val result: Int = temp + 10
  if result > 100 {
    return result / 2;
  } else {
    return result * 3;
  }
}
```

### Use Descriptive Variable Names

```onion
// Good
val filterErrors: (String) -> Boolean = (logLine: String) -> { return logLine.startsWith("ERROR"); }

// Bad
val f: (String) -> Boolean = (x: String) -> { return x.startsWith("ERROR"); }
```

### Avoid Side Effects When Possible

```onion
// Good: Pure function
val double: (Int) -> Int = (x: Int) -> { return x * 2; }

// Less ideal: Side effect
var count: Int = 0
val incrementCounter: () -> Int = () -> {
  count = count + 1  // Modifies external state
  return count
}
```

## Next Steps

- [Functions](functions.md) - Named functions and methods
- [Classes and Objects](classes-and-objects.md) - Object-oriented programming
- [Examples](../examples/functional.md) - Functional programming examples
