# REPL Shell

The Onion REPL (Read-Eval-Print Loop) provides an interactive environment for experimenting with Onion code.

## Starting the REPL

Launch the interactive shell:

```bash
# Using SBT (for development)
sbt
> runMain onion.tools.Shell

# Or if you have the onion-shell command
onion-shell
```

## Basic Usage

### Simple Expressions

```
onion> 2 + 2
4

onion> "Hello" + " " + "World"
Hello World

onion> 10 * 5
50
```

### Variable Definitions

```
onion> def x :Int = 42
x: Int = 42

onion> def name :String = "Alice"
name: String = Alice

onion> IO::println(name)
Alice
```

### Function Calls

```
onion> Math::sqrt(16.0)
4.0

onion> "hello".toUpperCase()
HELLO

onion> Math::max(10, 20)
20
```

## Defining Functions

```
onion> def square(x :Int) :Int { x * x }
square: (Int) => Int

onion> square(5)
25

onion> def greet(name :String) :String { "Hello, " + name }
greet: (String) => String

onion> greet("World")
Hello, World
```

## Working with Classes

### Import Classes

```
onion> import { java.util.ArrayList; }

onion> def list :ArrayList = new ArrayList
list: ArrayList = []

onion> list.add("First")
true

onion> list.add("Second")
true

onion> list.size()
2
```

### Define Classes

```
onion> class Point {
         @x :Int
         @y :Int
         public:
           def new(x :Int, y :Int) {
             @x = x
             @y = y
           }
           def distance :Double {
             Math::sqrt(@x * @x + @y * @y)
           }
       }

onion> def p :Point = new Point(3, 4)
p: Point = Point@1a2b3c4d

onion> p.distance()
5.0
```

## Lambda Expressions

```
onion> def double = #(x :Int) { x * 2 }
double: Function1

onion> double.call(5)
10

onion> def add = #(a :Int, b :Int) { a + b }
add: Function2

onion> add.call(3, 7)
10
```

## Multi-line Input

For complex expressions, use multiple lines:

```
onion> def factorial(n :Int) :Int {
         if n <= 1 {
           1
         } else {
           n * factorial(n - 1)
         }
       }

onion> factorial(5)
120
```

## Viewing Previous Results

The REPL stores results for quick reference:

```
onion> 2 + 2
res0: Int = 4

onion> res0 * 10
res1: Int = 40

onion> res1 + res0
44
```

## Helpful Commands

### Get Help

```
onion> :help
Available commands:
  :help    - Show this help message
  :quit    - Exit the REPL
  :reset   - Reset the session
  :imports - Show current imports
```

### Show Imports

```
onion> :imports
Current imports:
  java.util.ArrayList
  java.io.File
```

### Exit the REPL

```
onion> :quit
Goodbye!
```

Or use `Ctrl+D` (Unix) / `Ctrl+Z` (Windows).

## Practical Examples

### Quick Math

```
onion> def hypotenuse(a :Double, b :Double) :Double {
         Math::sqrt(a * a + b * b)
       }

onion> hypotenuse(3.0, 4.0)
5.0

onion> hypotenuse(5.0, 12.0)
13.0
```

### String Manipulation

```
onion> def text :String = "Hello, World!"
onion> text.length()
13

onion> text.toUpperCase()
HELLO, WORLD!

onion> text.substring(0, 5)
Hello
```

### Collections

```
onion> import { java.util.ArrayList; }

onion> def numbers :ArrayList = new ArrayList
onion> numbers << 10
onion> numbers << 20
onion> numbers << 30

onion> numbers.size()
3

onion> numbers.get(1)
20
```

### Testing Code Snippets

```
onion> def isPrime(n :Int) :Boolean {
         if n <= 1 {
           return false
         }
         for i = 2; i * i <= n; i = i + 1 {
           if n % i == 0 {
             return false
           }
         }
         true
       }

onion> isPrime(7)
true

onion> isPrime(10)
false

onion> isPrime(17)
true
```

## Tips and Tricks

### Quick Prototyping

Use the REPL to test ideas before writing full programs:

```
onion> // Test string formatting
onion> def format(name :String, age :Int) :String {
         name + " is " + age + " years old"
       }

onion> format("Alice", 30)
Alice is 30 years old
```

### Debugging

Test individual functions:

```
onion> def calculate(x :Int) :Int {
         def temp :Int = x * 2
         IO::println("temp = " + temp)
         temp + 10
       }

onion> calculate(5)
temp = 10
20
```

### Learning

Experiment with language features:

```
onion> // Test type casting
onion> def x :Double = 3.14
onion> def y :Int = x$Int
onion> IO::println(y)
3

onion> // Test closures
onion> def makeAdder(n :Int) {
         #(x :Int) { x + n }
       }
onion> def add5 = makeAdder(5)
onion> add5.call(10)
15
```

## Limitations

- Session state is lost when you exit
- No command history persistence (use arrow keys during session)
- Limited debugging capabilities
- Performance not optimized for large computations

## Use Cases

Perfect for:
- Learning Onion syntax
- Testing code snippets
- Quick calculations
- Prototyping functions
- Exploring Java libraries

Not ideal for:
- Large programs
- File I/O heavy tasks
- Long-running processes
- Production code

## Next Steps

- [Quick Start Guide](../getting-started/quick-start.md) - Learn more syntax
- [Examples](../examples/overview.md) - See example programs
- [Language Guide](../guide/overview.md) - Deep dive into features
