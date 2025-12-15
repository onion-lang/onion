# Basic Examples

Simple programs demonstrating fundamental Onion features.

## Hello World

The simplest Onion program:

**File: `Hello.on`**
```onion
IO::println("Hello")
```

**Run:**
```bash
onion Hello.on
```

**Output:**
```
Hello
```

## Arrays

Working with arrays:

**File: `Array.on`**
```onion
val array: String[] = new String[3]
array[0] = "A"
array[1] = "B"
array[2] = "C"

for var i: Int = 0; i < array.length; i = i + 1 {
  IO::println(array[i])
}
```

**Topics:**
- Array declaration and initialization
- Index-based access
- For loop with `.length` property

## String Concatenation

String operations and list iteration:

**File: `StringCat.on`**
```onion
val list: List = ["a", "b", "c", "d", "e", "f", "g"];
for var i: Int = 0; i < list.size; i = i + 1 {
  System::out.println("list[" + i + "] = " + list[i]);
}
```

**Topics:**
- Array literals
- String concatenation with `+`
- `.size` property
- System output

## User Input

Reading user input:

**File: `ReadLine.on`**
```onion
val name: String = IO::readln("What's your name? ")
IO::println("Hello, " + name + "!")
```

**Run:**
```bash
onion ReadLine.on
```

**Interaction:**
```
What's your name? Alice
Hello, Alice!
```

**Topics:**
- `IO::readln()` for reading input
- String concatenation
- Interactive programs

## Dynamic Lists

Using ArrayList with the `<<` append operator:

**File: `List.on`**
```onion
val list: List = [];

list << "a";
list << "b";
list << "c";
list << "d";

for var i: Int = 0; i < list.size; i = i + 1 {
  System::out.println(list[i]);
}
```

**Topics:**
- Importing Java classes
- ArrayList creation
- `<<` append operator
- Index-based access on collections

## Foreach Loop

Enhanced for loop with collections:

**File: `Foreach.on`**
```onion
import { java.util.ArrayList; }

val list: ArrayList[String] = new ArrayList[String]
list << "HELLO";
list << "WORLD";
list << "ONION";

foreach object :String in list {
  IO::println(object.toLowerCase())
}
```

**Output:**
```
hello
world
onion
```

**Topics:**
- `foreach` loop syntax
- Type annotations in loops
- Method calls on loop variables

## Pattern Matching

Using the `select` statement:

**File: `Select.on`**
```onion
val value: Int = (Math::random() * 10)$Int

select value {
  case 0, 1, 2, 3:
    IO::println("Low: " + value)
  case 4, 5, 6:
    IO::println("Medium: " + value)
  case 7, 8, 9:
    IO::println("High: " + value)
  else:
    IO::println("Out of range: " + value)
}
```

**Topics:**
- `select` statement
- Multiple values per case
- `else` default clause
- Type casting with `$`
- `Math::random()` function

## Complete Example: Array Processing

Combining multiple concepts:

```onion
import { java.util.ArrayList; }

// Create numbers list
val numbers: java.util.List = [10, 20, 30, 40, 50]

// Filter even numbers
val evens: ArrayList = new ArrayList
foreach num :Int in numbers {
  if num % 2 == 0 {
    evens << num
  }
}

// Print results
IO::println("Even numbers:")
foreach even :Object in evens {
  IO::println(even$Int)
}
```

**Output:**
```
Even numbers:
10
20
30
40
50
```

## Complete Example: Simple Calculator

```onion
val x: Int = 10
val y: Int = 3

IO::println("Addition: " + (x + y))
IO::println("Subtraction: " + (x - y))
IO::println("Multiplication: " + (x * y))
IO::println("Division: " + (x / y))
IO::println("Modulo: " + (x % y))
```

**Output:**
```
Addition: 13
Subtraction: 7
Multiplication: 30
Division: 3
Modulo: 1
```

## Next Steps

- [OOP Examples](oop.md) - Object-oriented programs
- [Functional Examples](functional.md) - Lambdas and higher-order functions
- [Language Guide](../guide/overview.md) - Deep dive into features
