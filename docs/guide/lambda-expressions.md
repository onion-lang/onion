# Lambda Expressions

Lambda expressions provide a concise way to create anonymous functions in Onion.

## Lambda Syntax

Lambdas use the `#(parameters) { body }` syntax:

```onion
// Lambda with one parameter
def double = #(x :Int) { x * 2 }

// Lambda with multiple parameters
def add = #(x :Int, y :Int) { x + y }

// Lambda with no parameters
def greet = #() { IO::println("Hello!") }
```

## Calling Lambdas

Use the `.call()` method to invoke a lambda:

```onion
def square = #(x :Int) { x * x }

def result :Int = square.call(5)$Int  // 25
IO::println(result)
```

## Function Types

Lambdas are typed using `Function0` through `Function10` interfaces:

```onion
// Function with 0 parameters
def func0 :Function0 = #() { 42 }
def value :Int = func0.call()$Int

// Function with 1 parameter
def func1 :Function1 = #(x :Int) { x * 2 }
def doubled :Int = func1.call(10)$Int

// Function with 2 parameters
def func2 :Function2 = #(x :Int, y :Int) { x + y }
def sum :Int = func2.call(3, 7)$Int
```

## Closures

Lambdas can capture variables from their enclosing scope:

### Simple Closure

```onion
def multiplier :Int = 10
def multiply = #(x :Int) { x * multiplier }

IO::println(multiply.call(5))  // 50
```

### Mutable Closures

Closures can modify captured variables:

```onion
def count :Int = 0
def increment = #() {
  count = count + 1
  count
}

IO::println(increment.call())  // 1
IO::println(increment.call())  // 2
IO::println(increment.call())  // 3
```

### Counter Factory

```onion
def makeCounter {
  def count :Int = 0
  #() {
    count = count + 1
    count
  }
}

def counter1 = makeCounter()
def counter2 = makeCounter()

IO::println(counter1.call())  // 1
IO::println(counter1.call())  // 2
IO::println(counter2.call())  // 1
IO::println(counter1.call())  // 3
```

## Higher-Order Functions

Functions that accept lambdas as parameters:

### Filter Function

```onion
def filter(items :String[], predicate :Function1) :String[] {
  def result :ArrayList = new ArrayList

  foreach item :String in items {
    def keep :Boolean = predicate.call(item)$Boolean
    if keep {
      result << item
    }
  }

  // Convert to array
  result.toArray(new String[result.size()])
}

def lines :String[] = [
  "INFO: System started",
  "ERROR: Connection failed",
  "INFO: Processing data",
  "ERROR: Timeout"
]

def isError = #(line :String) {
  line.startsWith("ERROR")
}

def errors :String[] = filter(lines, isError)
foreach error :String in errors {
  IO::println(error)
}
// Output:
// ERROR: Connection failed
// ERROR: Timeout
```

### Map Function

```onion
def map(items :String[], transform :Function1) :String[] {
  def result :ArrayList = new ArrayList

  foreach item :String in items {
    def transformed :String = transform.call(item)$String
    result << transformed
  }

  result.toArray(new String[result.size()])
}

def words :String[] = ["hello", "world", "onion"]
def toUpper = #(s :String) { s.toUpperCase() }

def upper :String[] = map(words, toUpper)
foreach word :String in upper {
  IO::println(word)
}
// Output:
// HELLO
// WORLD
// ONION
```

### Reduce Function

```onion
def reduce(items :Int[], operation :Function2, initial :Int) :Int {
  def accumulator :Int = initial

  foreach item :Int in items {
    accumulator = operation.call(accumulator, item)$Int
  }

  accumulator
}

def numbers :Int[] = [1, 2, 3, 4, 5]
def sum = #(acc :Int, n :Int) { acc + n }

def total :Int = reduce(numbers, sum, 0)
IO::println(total)  // 15
```

## Practical Examples

### Line Filtering

```onion
import {
  java.io.BufferedReader;
  java.io.FileReader;
}

def filterFile(filename :String, predicate :Function1) {
  def reader :BufferedReader = new BufferedReader(
    new FileReader(filename)
  )

  def line :String = null
  while (line = reader.readLine()) != null {
    def keep :Boolean = predicate.call(line)$Boolean
    if keep {
      IO::println(line)
    }
  }

  reader.close()
}

// Filter lines starting with ERROR
def errorFilter = #(line :String) {
  line.startsWith("ERROR")
}

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
  @compareFunc :Function2

  public:
    def new(func :Function2) {
      @compareFunc = func
    }

    def compare(a :Object, b :Object) :Int {
      @compareFunc.call(a, b)$Int
    }
}

def list :ArrayList = new ArrayList
list << "banana"
list << "apple"
list << "cherry"

def alphabetical = #(a :Object, b :Object) {
  def s1 :String = a$String
  def s2 :String = b$String
  s1.compareTo(s2)
}

def comparator :LambdaComparator = new LambdaComparator(alphabetical)
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
  @handler :Function1

  public:
    def new(h :Function1) {
      @handler = h
    }

    def actionPerformed(event :ActionEvent) {
      @handler.call(event)
    }
}

def button :JButton = new JButton("Click me")

def onClick = #(event :ActionEvent) {
  IO::println("Button was clicked!")
}

def listener :LambdaActionListener = new LambdaActionListener(onClick)
button.addActionListener(listener)
```

## Lambda Best Practices

### Keep Lambdas Short

```onion
// Good: Simple, focused lambda
def isEven = #(n :Int) { n % 2 == 0 }

// Bad: Complex lambda (use named function instead)
def complex = #(n :Int) {
  def temp :Int = n * 2
  def result :Int = temp + 10
  if result > 100 {
    result / 2
  } else {
    result * 3
  }
}
```

### Use Descriptive Variable Names

```onion
// Good
def filterErrors = #(logLine :String) {
  logLine.startsWith("ERROR")
}

// Bad
def f = #(x :String) { x.startsWith("ERROR") }
```

### Avoid Side Effects When Possible

```onion
// Good: Pure function
def double = #(x :Int) { x * 2 }

// Less ideal: Side effect
def count :Int = 0
def incrementCounter = #() {
  count = count + 1  // Modifies external state
}
```

## Next Steps

- [Functions](functions.md) - Named functions and methods
- [Classes and Objects](classes-and-objects.md) - Object-oriented programming
- [Examples](../examples/functional.md) - Functional programming examples
