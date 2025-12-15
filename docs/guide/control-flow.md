# Control Flow

Onion provides several control flow constructs for conditionals, loops, and pattern matching.

## Conditional Statements

### If-Else

Basic conditional execution:

```onion
val age: Int = 18

if age >= 18 {
  IO::println("Adult")
} else {
  IO::println("Minor")
}
```

### If-Else If-Else

Multiple conditions:

```onion
val score: Int = 85

if score >= 90 {
  IO::println("Grade: A")
} else if score >= 80 {
  IO::println("Grade: B")
} else if score >= 70 {
  IO::println("Grade: C")
} else if score >= 60 {
  IO::println("Grade: D")
} else {
  IO::println("Grade: F")
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
  IO::println("Read: " + line)
}
```

## Loops

### While Loop

Execute while a condition is true:

```onion
var count: Int = 0
while count < 5 {
  IO::println("Count: " + count)
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
  IO::println(i)
}
```

Syntax: `for init; condition; update { body }`

### Foreach Loop

Iterate over arrays and collections:

```onion
val fruits: String[] = new String[3]
fruits[0] = "apple"
fruits[1] = "banana"
fruits[2] = "cherry"

foreach fruit :String in fruits {
  IO::println("Fruit: " + fruit)
}
```

With collections:

```onion
import { java.util.ArrayList; }

val list: ArrayList = new ArrayList
list << "First"
list << "Second"
list << "Third"

foreach item :Object in list {
  IO::println(item$String)
}
```

## Pattern Matching

### Select Statement

Switch-style pattern matching with multiple values per case:

```onion
val day: Int = 3

select day {
  case 1:
    IO::println("Monday")
  case 2:
    IO::println("Tuesday")
  case 3:
    IO::println("Wednesday")
  case 4:
    IO::println("Thursday")
  case 5:
    IO::println("Friday")
  case 6, 7:
    IO::println("Weekend")
  else:
    IO::println("Invalid day")
}
```

### Multiple Values Per Case

Group multiple values in a single case:

```onion
val num: Int = (Math::random() * 10)$Int

select num {
  case 0, 1, 2, 3:
    IO::println("Low")
  case 4, 5, 6:
    IO::println("Medium")
  case 7, 8, 9:
    IO::println("High")
  else:
    IO::println("Other")
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

IO::println("Grade: " + grade)
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
  IO::println(i)
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
  IO::println(i)  // Print odd numbers only
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
  IO::println("Parsed: " + number)
} catch e :NumberFormatException {
  IO::println("Invalid number: " + e.getMessage())
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
  IO::println("File not found: " + e.getMessage())
} catch e :IOException {
  IO::println("IO error: " + e.getMessage())
}
```

### Accessing Exception Information

```onion
try {
  val result: Int = 10 / 0
} catch e :ArithmeticException {
  IO::println("Error: " + e.getMessage())
  IO::println("Type: " + e.getClass().getName())
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
    IO::println("Error: Division by zero")
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
