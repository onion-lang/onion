# Functions

Functions in Onion allow you to encapsulate reusable code. Onion supports both named functions (methods) and anonymous functions (lambdas).

## Function Definitions

### Basic Function

Define functions with the `def` keyword:

```onion
def greet(name :String) :String {
  "Hello, " + name + "!"
}

def message :String = greet("Alice")
IO::println(message)  // "Hello, Alice!"
```

### Function with Multiple Parameters

```onion
def add(a :Int, b :Int) :Int {
  a + b
}

def sum :Int = add(5, 3)
IO::println(sum)  // 8
```

### Function with No Parameters

```onion
def getCurrentTime :String {
  System::currentTimeMillis()$String
}

IO::println(getCurrentTime())
```

### Function with No Return Value

Functions that don't return a value implicitly return nothing:

```onion
def printMessage(msg :String) {
  IO::println("Message: " + msg)
}

printMessage("Hello")
```

## Return Statements

### Explicit Return

Use `return` to exit a function early:

```onion
def max(a :Int, b :Int) :Int {
  if a > b {
    return a
  }
  return b
}
```

### Implicit Return

The last expression in a function is implicitly returned:

```onion
def square(x :Int) :Int {
  x * x  // Implicitly returned
}
```

## Lambda Expressions

### Lambda Syntax

Anonymous functions use the `(params) -> { body }` syntax:

```onion
def double = (x :Int) -> { return x * 2; }

def result :Int = double.call(5)$Int  // 10
```

### Lambda with Multiple Parameters

```onion
def add = (x :Int, y :Int) -> { return x + y; }

def sum :Int = add.call(3, 7)$Int  // 10
```

### Lambda with No Parameters

```onion
def sayHello = () -> { IO::println("Hello!") }

sayHello.call()
```

## Closures

Lambdas can capture variables from their enclosing scope:

```onion
def makeCounter {
  def count :Int = 0
  () -> {
    count = count + 1
    return count;
  }
}

def counter = makeCounter()
IO::println(counter.call())  // 1
IO::println(counter.call())  // 2
IO::println(counter.call())  // 3
```

### Capturing Loop Variables

```onion
def i :Int = 0
def filter = (line :String) -> {
  i = i + 1
  return line + " (line " + i + ")";
}

IO::println(filter.call("First"))   // "First (line 1)"
IO::println(filter.call("Second"))  // "Second (line 2)"
```

## Function Types

Functions are typed using `Function0` through `Function10` interfaces:

```onion
// Function with 1 parameter
def func1 :Function1 = (x :Int) -> { return x * 2; }

// Function with 2 parameters
def func2 :Function2 = (x :Int, y :Int) -> { return x + y; }

// Function with no parameters
def func0 :Function0 = () -> { return 42; }
```

The number indicates the parameter count:
- `Function0` - No parameters
- `Function1` - One parameter
- `Function2` - Two parameters
- ... up to `Function10` - Ten parameters

## Higher-Order Functions

Functions that accept or return other functions:

```onion
def applyTwice(f :Function1, value :Int) :Int {
  def temp :Int = f.call(value)$Int
  f.call(temp)$Int
}

def increment = (x :Int) -> { return x + 1; }
def result :Int = applyTwice(increment, 5)  // 7
```

## Recursive Functions

Functions can call themselves:

```onion
def factorial(n :Int) :Int {
  if n <= 1 {
    return 1
  }
  n * factorial(n - 1)
}

IO::println(factorial(5))  // 120
```

### Tail Recursion

While Onion doesn't optimize tail calls, you can write tail-recursive functions:

```onion
def factorialTail(n :Int, acc :Int) :Int {
  if n <= 1 {
    return acc
  }
  factorialTail(n - 1, n * acc)
}

def factorial(n :Int) :Int {
  factorialTail(n, 1)
}

IO::println(factorial(5))  // 120
```

## Method Overloading

Classes can have multiple methods with the same name but different parameter types:

```onion
class Calculator {
  public:
    def add(a :Int, b :Int) :Int {
      a + b
    }

    def add(a :Double, b :Double) :Double {
      a + b
    }

    def add(a :String, b :String) :String {
      a + b
    }
}

def calc :Calculator = new Calculator
IO::println(calc.add(5, 3))           // 8
IO::println(calc.add(2.5, 3.7))       // 6.2
IO::println(calc.add("Hello", "!"))   // "Hello!"
```

## Static Methods

Methods can be static (class-level) rather than instance-level:

```onion
class MathUtils {
  public:
    static def square(x :Int) :Int {
      x * x
    }

    static def cube(x :Int) :Int {
      x * x * x
    }
}

// Call static methods with ::
IO::println(MathUtils::square(5))  // 25
IO::println(MathUtils::cube(3))    // 27
```

## Function Examples

### Filter Function

```onion
def filterLines(lines :String[], predicate :Function1) :String[] {
  def result :ArrayList = new ArrayList

  foreach line :String in lines {
    def keep :Boolean = predicate.call(line)$Boolean
    if keep {
      result << line
    }
  }

  // Convert to array (simplified)
  result.toArray(new String[result.size()])
}

def startsWithError = (line :String) -> { return line.startsWith("ERROR"); }

def lines :String[] = ["INFO: OK", "ERROR: Failed", "ERROR: Timeout"]
def errors :String[] = filterLines(lines, startsWithError)
```

### Map Function

```onion
def mapLines(lines :String[], transform :Function1) :String[] {
  def result :ArrayList = new ArrayList

  foreach line :String in lines {
    def transformed :String = transform.call(line)$String
    result << transformed
  }

  result.toArray(new String[result.size()])
}

def toUpper = (s :String) -> { return s.toUpperCase(); }

def lines :String[] = ["hello", "world"]
def upper :String[] = mapLines(lines, toUpper)
// ["HELLO", "WORLD"]
```

## Best Practices

### Single Responsibility

Each function should do one thing well:

```onion
// Good: Each function has a single purpose
def readFile(path :String) :String { ... }
def parseData(content :String) :Data { ... }
def validateData(data :Data) :Boolean { ... }

// Bad: Function does too much
def processFile(path :String) :Boolean {
  // Reads, parses, validates, and saves
  ...
}
```

### Descriptive Names

Use clear, descriptive function names:

```onion
// Good
def calculateTotalPrice(items :Item[]) :Double { ... }
def isValidEmail(email :String) :Boolean { ... }

// Bad
def calc(arr :Item[]) :Double { ... }
def check(s :String) :Boolean { ... }
```

### Keep Functions Short

Aim for functions that fit on one screen:

```onion
def processOrder(order :Order) :Boolean {
  if !validateOrder(order) {
    return false
  }

  if !chargePayment(order) {
    return false
  }

  if !shipOrder(order) {
    return false
  }

  true
}
```

## Next Steps

- [Classes and Objects](classes-and-objects.md) - Methods in classes
- [Lambda Expressions](lambda-expressions.md) - Deep dive into lambdas
- [Examples](../examples/functional.md) - Functional programming examples
