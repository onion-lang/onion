# Lambda Expressions

Lambda expressions provide a concise way to create anonymous functions in Onion.

## Lambda Syntax

Lambdas are written `(parameters) -> body`, where the body is either an
expression or a block:

```onion
// Expression body
val double = (x: Int) -> x * 2

// Bare single parameter (type inferred from the expected function type)
val triple: Int -> Int = x -> x * 3

// Multiple parameters
val add = (a: Int, b: Int) -> a + b

// Block body for multiple statements
val greet: () -> String = () -> { println("Hello!"); return "done"; }
```

### Trailing Lambdas

When a lambda is the last argument of a call, it can follow the call as a
brace block. Parameters go before `->` inside the braces, and a lambda with no
parameters needs no arrow at all: the block's last expression is its value.
This works on instance, static and unqualified calls, with or without an
argument list in between:

```onion
list.map { x -> x * 2 }
list.fold(0) { acc, x -> acc + x }
val f: Future[Int] = Future::async { compute() }
val g: Future[Int] = Future::async { -> compute() }   // same, with an explicit arrow
Timing::measure("step") { expensiveOperation() }
```

In a condition position -- the condition of `if`, `while` and `do ... while`,
the collection of `foreach ... in`, the scrutinee of `select`, the `for`
header -- a bare `{` is the statement block, so `if flag { ... }` and
`foreach x in xs { ... }` mean what they always did. Only the arrow form is a
lambda there; to pass an arrow-less lambda inside a condition, parenthesize
the call:

```onion
if xs.any { x -> x > 0 } { println("positive") }   // arrow form: a lambda
if (once { 1 }) == 1 { println("one") }             // parenthesized: a lambda
if ready { println("go") }                          // block of the if
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

Function values are called directly with function-call syntax:

```onion
val double = (x: Int) -> x * 2
println(double(21))   // 42
```

Under the hood a function value is an object implementing `onion.Function0` ..
`onion.Function10`, whose single method is `call`; `double(21)` compiles to
`double(21)`. You only need to spell `.call` yourself when handing a function
value to Java code that expects to invoke it by that name.

## Function Types

Lambdas can be typed using the arrow type syntax `(A, B) -> R`. For a single parameter, parentheses are optional (`A -> R`):

```onion
// Function with 0 parameters
val func0: () -> Int = () -> { return 42; }
val value: Int = func0()

// Function with 1 parameter
val func1: Int -> Int = (x: Int) -> { return x * 2; }
val doubled: Int = func1(10)

// Function with 2 parameters
val func2: (Int, Int) -> Int = (x: Int, y: Int) -> { return x + y; }
val sum: Int = func2(3, 7)
```

### Void-Returning Functions

For side-effect-only lambdas, the return type can be written as `void` or `Unit`. At runtime these erase to `Object`, so the lambda body returns `null`:

```onion
def repeat(n: Int, block: () -> Unit): void {
  for var i: Int = 0; i < n; i = i + 1 {
    block()
  }
}

repeat(3, () -> { println("tick") })
```

## Java Functional Interfaces (SAM Conversion)

Lambdas convert to any Java interface with a single abstract method:

```onion
val r: Runnable = () -> println("ran")
new Thread(r).start()

val cmp: Comparator[Integer] = (a, b) -> (b as Int) - (a as Int)
Collections::sort(xs, cmp)

// Argument position works too
Collections::sort(xs, (a, b) -> (a as Int) - (b as Int))
```

### Primitive Type Arguments

Onion boxes primitive type arguments at the generic-interface level, so
`Comparator[Int]` is represented as `Comparator[Integer]` internally. You can
still write the lambda parameters with their primitive types:

```onion
import { java.util.Comparator }

val cmp: Comparator[Int] = (a: Int, b: Int) -> a - b
Collections::sort(xs, cmp)

// The same lambda can be written directly at argument position.
Collections::sort(xs, (a: Int, b: Int) -> a - b)
```

The compiler matches the primitive parameter types against the boxed interface
signature and generates the necessary bridge method automatically. This also
works for primitive return types:

```onion
import { java.util.function.Supplier }

val s: Supplier[Int] = () -> 42
```

## Closures

Lambdas can capture variables from their enclosing scope:

### Simple Closure

```onion
val multiplier: Int = 10
val multiply: (Int) -> Int = (x: Int) -> { return x * multiplier; }

println(multiply(5))  // 50
```

### Mutable Closures

Closures can modify captured variables:

```onion
var count: Int = 0
val increment: () -> Int = () -> {
  count = count + 1
  return count;
}

println(increment())  // 1
println(increment())  // 2
println(increment())  // 3
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

println(counter1())  // 1
println(counter1())  // 2
println(counter2())  // 1
println(counter1())  // 3
```

## Higher-Order Functions

Functions that accept lambdas as parameters:

### Filter Function

```onion
import {
  java.util.ArrayList;
  java.util.List;
}

def filter(items: List[String], predicate: (String) -> Boolean): List[String] {
  val result: ArrayList[String] = new ArrayList[String]()

  foreach item: String in items {
    if predicate(item) {
      result << item
    }
  }

  return result
}

val lines: List[String] = [
  "INFO: System started",
  "ERROR: Connection failed",
  "INFO: Processing data",
  "ERROR: Timeout"
]

val isError: (String) -> Boolean = (line: String) -> { return line.startsWith("ERROR"); }

val errors: List[String] = filter(lines, isError)
foreach error: String in errors {
  println(error)
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

def map(items: List[String], transform: (String) -> String): List[String] {
  val result: ArrayList[String] = new ArrayList[String]()

  foreach item: String in items {
    result << transform(item)
  }

  return result
}

val words: List[String] = ["hello", "world", "onion"]
val toUpper: (String) -> String = (s: String) -> { return s.toUpperCase(); }

val upper: List[String] = map(words, toUpper)
foreach word: String in upper {
  println(word)
}
// Output:
// HELLO
// WORLD
// ONION
```

### Reduce Function

```onion
import { java.util.List; }

def reduce(items: List[Int], operation: (Int, Int) -> Int, initial: Int): Int {
  var accumulator: Int = initial

  foreach item: Int in items {
    accumulator = operation(accumulator, item)
  }

  return accumulator
}

val numbers: List[Int] = [1, 2, 3, 4, 5]
val sum: (Int, Int) -> Int = (acc: Int, n: Int) -> { return acc + n; }

val total: Int = reduce(numbers, sum, 0)
println(total)  // 15
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
    if predicate(line) {
      println(line)
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

class LambdaComparator conforms Comparator[Object] {
  val compareFunc: (Object, Object) -> Int

  public:
    def this(func: (Object, Object) -> Int) {
      this.compareFunc = func
    }

    def compare(a: Object, b: Object): Int = compareFunc(a, b)
}

val list: ArrayList[String] = new ArrayList[String]()
list << "banana"
list << "apple"
list << "cherry"

val alphabetical: (Object, Object) -> Int = (a: Object, b: Object) -> {
  val s1: String = (a as String)
  val s2: String = (b as String)
  return s1.compareTo(s2);
}

val comparator: LambdaComparator = new LambdaComparator(alphabetical)
Collections::sort(list, comparator)

foreach item :Object in list {
  println((item as String))
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

class LambdaActionListener conforms ActionListener {
  val handler: (ActionEvent) -> Int

  public:
    def this(h: (ActionEvent) -> Int) {
      this.handler = h
    }

    def actionPerformed(event: ActionEvent): void {
      handler(event)
    }
}

val button: JButton = new JButton("Click me")

val onClick: (ActionEvent) -> Int = (event: ActionEvent) -> {
  println("Button was clicked!")
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
