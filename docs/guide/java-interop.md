# Java Interoperability

One of Onion's key strengths is seamless interoperability with Java. You can use any Java library directly in your Onion code.

## Importing Java Classes

### Import Statement

Use the `import` statement to import Java classes:

```onion
import {
  java.util.ArrayList;
  java.util.HashMap;
  java.io.File;
}

val list: ArrayList = new ArrayList
val map: HashMap = new HashMap
val file: File = new File("data.txt")
```

### Multiple Imports

Group related imports together:

```onion
import {
  java.io.File;
  java.io.FileReader;
  java.io.BufferedReader;
  java.io.IOException;
}
```

### Fully Qualified Names

You can use fully qualified names without importing:

```onion
val list: java.util.ArrayList = new java.util.ArrayList
val file: java.io.File = new java.io.File("data.txt")
```

## Creating Java Objects

### Constructor Calls

Create Java objects with the `new` keyword:

```onion
import {
  java.util.ArrayList;
  java.util.Date;
  java.lang.StringBuilder;
}

val list: ArrayList = new ArrayList
val date: Date = new Date
val builder: StringBuilder = new StringBuilder("Hello")
```

### Constructor with Parameters

```onion
import {
  java.io.File;
  java.awt.Dimension;
}

val file: File = new File("/path/to/file.txt")
val size: Dimension = new Dimension(800, 600)
```

## Calling Java Methods

### Instance Methods

Call methods on Java objects normally:

```onion
import { java.util.ArrayList; }

val list: ArrayList = new ArrayList
list.add("First")
list.add("Second")
list.add("Third")

val size: Int = list.size()  // 3
val item: Object = list.get(0)  // "First"
val exists: Boolean = list.contains("Second")  // true
```

### Static Methods

Use `::` to call static methods:

```onion
// Math class
val random: Double = Math::random()
val sqrt: Double = Math::sqrt(16.0)
val max: Int = Math::max(10, 20)

// System class
val time: Long = System::currentTimeMillis()
val property: String = System::getProperty("os.name")

// Integer parsing
val number: Int = JInteger::parseInt("42")
```

### Method Chaining

```onion
import { java.lang.StringBuilder; }

val result: String = new StringBuilder("Hello")
  .append(" ")
  .append("World")
  .append("!")
  .toString()

IO::println(result)  // "Hello World!"
```

## Working with Java Collections

### ArrayList

```onion
import { java.util.ArrayList; }

val list: ArrayList = new ArrayList
list << "Apple"     // << operator for add()
list << "Banana"
list << "Cherry"

foreach fruit: String in list {
  IO::println(fruit)
}

val first: String = list.get(0)$String
val size: Int = list.size()
```

### HashMap

```onion
import { java.util.HashMap; }

val map: HashMap = new HashMap
map.put("name", "Alice")
map.put("age", 30)
map.put("city", "Tokyo")

val name: String = map.get("name")$String
val age: Int = map.get("age")$Int

val hasKey: Boolean = map.containsKey("name")
```

### HashSet

```onion
import { java.util.HashSet; }

val set: HashSet = new HashSet
set.add("red")
set.add("green")
set.add("blue")
set.add("red")  // Duplicate, won't be added

IO::println(set.size())  // 3
```

## Implementing Java Interfaces

### ActionListener Example

```onion
import {
  java.awt.event.ActionListener;
  java.awt.event.ActionEvent;
  javax.swing.JButton;
}

class MyButtonHandler <: ActionListener {
  public:
    def actionPerformed(event :ActionEvent) {
      IO::println("Button clicked!")
      val source: JButton = event.getSource()$JButton
      IO::println("Button text: " + source.getText())
    }
}

val button: JButton = new JButton("Click me")
val handler: MyButtonHandler = new MyButtonHandler
button.addActionListener(handler)
```

### Runnable Example

```onion
import { java.lang.Runnable; }

class MyTask <: Runnable {
  val name: String

  public:
    def this(n: String) {
      this.name = n
    }

    def run {
      IO::println("Task " + this.name + " is running")
    }
}

val task: MyTask = new MyTask("Background Task")
val thread: Thread = new Thread(task)
thread.start()
```

### Comparable Example

```onion
import { java.lang.Comparable; }

class Person <: Comparable {
  val name: String
  val age: Int

  public:
    def this(n: String, a: Int) {
      this.name = n
      this.age = a
    }

    def compareTo(other: Object): Int {
      val otherPerson: Person = other$Person
      return this.age - otherPerson.age
    }
}
```

## Extending Java Classes

### JFrame Example

```onion
import {
  javax.swing.JFrame;
  javax.swing.JButton;
  java.awt.FlowLayout;
}

class MyWindow : JFrame {
  public:
    def this {
      setTitle("My Window")
      setSize(400, 300)
      setLayout(new FlowLayout())

      val button: JButton = new JButton("Click me")
      add(button)

      setDefaultCloseOperation(JFrame::EXIT_ON_CLOSE)
      setVisible(true)
    }
}

val window: MyWindow = new MyWindow
```

## Java I/O

### File Reading

```onion
import {
  java.io.File;
  java.io.FileReader;
  java.io.BufferedReader;
  java.io.IOException;
}

def readFile(filename :String) {
  try {
    val reader: BufferedReader = new BufferedReader(
      new FileReader(filename)
    )

    var line: String = null
    while (line = reader.readLine()) != null {
      IO::println(line)
    }

    reader.close()
  } catch e :IOException {
    IO::println("Error reading file: " + e.getMessage())
  }
}

readFile("data.txt")
```

### File Writing

```onion
import {
  java.io.FileWriter;
  java.io.BufferedWriter;
  java.io.IOException;
}

def writeFile(filename :String, content :String) {
  try {
    val writer: BufferedWriter = new BufferedWriter(
      new FileWriter(filename)
    )

    writer.write(content)
    writer.newLine()
    writer.close()

    IO::println("File written successfully")
  } catch e :IOException {
    IO::println("Error writing file: " + e.getMessage())
  }
}

writeFile("output.txt", "Hello, World!")
```

## Java Swing GUI

### Complete GUI Application

```onion
import {
  javax.swing.JFrame;
  javax.swing.JButton;
  javax.swing.JTextField;
  javax.swing.JPanel;
  java.awt.BorderLayout;
  java.awt.event.ActionListener;
  java.awt.event.ActionEvent;
}

class CalculatorApp : JFrame <: ActionListener {
  val textField: JTextField
  var currentValue: Double

  public:
    def this {
      setTitle("Simple Calculator")
      setSize(300, 200)
      setLayout(new BorderLayout())

      this.textField = new JTextField()
      add(this.textField, BorderLayout::NORTH)

      val panel: JPanel = new JPanel()
      val button: JButton = new JButton("Calculate")
      button.addActionListener(this)
      panel.add(button)

      add(panel, BorderLayout::CENTER)

      this.currentValue = 0.0
      setDefaultCloseOperation(JFrame::EXIT_ON_CLOSE)
      setVisible(true)
    }

    def actionPerformed(event :ActionEvent) {
      val text: String = this.textField.getText()
      IO::println("Calculating: " + text)
    }
}

val app: CalculatorApp = new CalculatorApp
```

## Type Casting

### Casting Objects

Use the `$` operator to cast Java objects:

```onion
val obj: Object = "Hello"
val str: String = obj$String

val list: ArrayList = new ArrayList
list.add("Item")
val item: String = list.get(0)$String
```

### Numeric Casting

```onion
val random: Double = Math::random()
val randomInt: Int = (random * 100)$Int

val longValue: Long = 123456789L
val intValue: Int = longValue$Int
```

## Common Java Classes

### String Operations

```onion
val text: String = "Hello, World!"
val upper: String = text.toUpperCase()
val lower: String = text.toLowerCase()
val length: Int = text.length()
val sub: String = text.substring(0, 5)
val contains: Boolean = text.contains("World")
```

### Integer and Long

```onion
// Parsing
val i: Int = JInteger::parseInt("42")
val l: Long = JLong::parseLong("1234567890")

// Conversion
val iStr: String = JInteger::toString(42)
val lStr: String = JLong::toString(1234567890L)

// Min/Max
val max: Int = JInteger::MAX_VALUE
val min: Int = JInteger::MIN_VALUE
```

### Math Operations

```onion
val abs: Int = Math::abs(-10)        // 10
val ceil: Double = Math::ceil(3.14)   // 4.0
val floor: Double = Math::floor(3.14) // 3.0
val round: Long = Math::round(3.14)   // 3
val pow: Double = Math::pow(2.0, 3.0) // 8.0
```

## Next Steps

- [Classes and Objects](classes-and-objects.md) - OOP in Onion
- [Inheritance](inheritance.md) - Extending Java classes
- [Examples](../examples/oop.md) - Real-world examples
