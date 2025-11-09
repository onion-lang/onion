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

def list :ArrayList = new ArrayList
def map :HashMap = new HashMap
def file :File = new File("data.txt")
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
def list :java.util.ArrayList = new java.util.ArrayList
def file :java.io.File = new java.io.File("data.txt")
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

def list :ArrayList = new ArrayList
def date :Date = new Date
def builder :StringBuilder = new StringBuilder("Hello")
```

### Constructor with Parameters

```onion
import {
  java.io.File;
  java.awt.Dimension;
}

def file :File = new File("/path/to/file.txt")
def size :Dimension = new Dimension(800, 600)
```

## Calling Java Methods

### Instance Methods

Call methods on Java objects normally:

```onion
import { java.util.ArrayList; }

def list :ArrayList = new ArrayList
list.add("First")
list.add("Second")
list.add("Third")

def size :Int = list.size()  // 3
def item :Object = list.get(0)  // "First"
def exists :Boolean = list.contains("Second")  // true
```

### Static Methods

Use `::` to call static methods:

```onion
// Math class
def random :Double = Math::random()
def sqrt :Double = Math::sqrt(16.0)
def max :Int = Math::max(10, 20)

// System class
def time :Long = System::currentTimeMillis()
def property :String = System::getProperty("os.name")

// Integer parsing
def number :Int = JInteger::parseInt("42")
```

### Method Chaining

```onion
import { java.lang.StringBuilder; }

def result :String = new StringBuilder("Hello")
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

def list :ArrayList = new ArrayList
list << "Apple"     // << operator for add()
list << "Banana"
list << "Cherry"

foreach fruit :Object in list {
  IO::println(fruit$String)
}

def first :String = list.get(0)$String
def size :Int = list.size()
```

### HashMap

```onion
import { java.util.HashMap; }

def map :HashMap = new HashMap
map.put("name", "Alice")
map.put("age", 30)
map.put("city", "Tokyo")

def name :String = map.get("name")$String
def age :Int = map.get("age")$Int

def hasKey :Boolean = map.containsKey("name")
```

### HashSet

```onion
import { java.util.HashSet; }

def set :HashSet = new HashSet
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
      def source :JButton = event.getSource()$JButton
      IO::println("Button text: " + source.getText())
    }
}

def button :JButton = new JButton("Click me")
def handler :MyButtonHandler = new MyButtonHandler
button.addActionListener(handler)
```

### Runnable Example

```onion
import { java.lang.Runnable; }

class MyTask <: Runnable {
  @name :String

  public:
    def new(n :String) {
      @name = n
    }

    def run {
      IO::println("Task " + @name + " is running")
    }
}

def task :MyTask = new MyTask("Background Task")
def thread :Thread = new Thread(task)
thread.start()
```

### Comparable Example

```onion
import { java.lang.Comparable; }

class Person <: Comparable {
  @name :String
  @age :Int

  public:
    def new(n :String, a :Int) {
      @name = n
      @age = a
    }

    def compareTo(other :Object) :Int {
      def otherPerson :Person = other$Person
      @age - otherPerson.@age
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
    def new {
      setTitle("My Window")
      setSize(400, 300)
      setLayout(new FlowLayout())

      def button :JButton = new JButton("Click me")
      add(button)

      setDefaultCloseOperation(JFrame::EXIT_ON_CLOSE)
      setVisible(true)
    }
}

def window :MyWindow = new MyWindow
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
    def reader :BufferedReader = new BufferedReader(
      new FileReader(filename)
    )

    def line :String = null
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
    def writer :BufferedWriter = new BufferedWriter(
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
  @textField :JTextField
  @currentValue :Double

  public:
    def new {
      setTitle("Simple Calculator")
      setSize(300, 200)
      setLayout(new BorderLayout())

      @textField = new JTextField()
      add(@textField, BorderLayout::NORTH)

      def panel :JPanel = new JPanel()
      def button :JButton = new JButton("Calculate")
      button.addActionListener(self)
      panel.add(button)

      add(panel, BorderLayout::CENTER)

      setDefaultCloseOperation(JFrame::EXIT_ON_CLOSE)
      setVisible(true)
    }

    def actionPerformed(event :ActionEvent) {
      def text :String = @textField.getText()
      IO::println("Calculating: " + text)
    }
}

def app :CalculatorApp = new CalculatorApp
```

## Type Casting

### Casting Objects

Use the `$` operator to cast Java objects:

```onion
def obj :Object = "Hello"
def str :String = obj$String

def list :ArrayList = new ArrayList
list.add("Item")
def item :String = list.get(0)$String
```

### Numeric Casting

```onion
def random :Double = Math::random()
def randomInt :Int = (random * 100)$Int

def longValue :Long = 123456789L
def intValue :Int = longValue$Int
```

## Common Java Classes

### String Operations

```onion
def text :String = "Hello, World!"
def upper :String = text.toUpperCase()
def lower :String = text.toLowerCase()
def length :Int = text.length()
def sub :String = text.substring(0, 5)
def contains :Boolean = text.contains("World")
```

### Integer and Long

```onion
// Parsing
def i :Int = JInteger::parseInt("42")
def l :Long = JLong::parseLong("1234567890")

// Conversion
def iStr :String = JInteger::toString(42)
def lStr :String = JLong::toString(1234567890L)

// Min/Max
def max :Int = JInteger::MAX_VALUE
def min :Int = JInteger::MIN_VALUE
```

### Math Operations

```onion
def abs :Int = Math::abs(-10)        // 10
def ceil :Double = Math::ceil(3.14)   // 4.0
def floor :Double = Math::floor(3.14) // 3.0
def round :Long = Math::round(3.14)   // 3
def pow :Double = Math::pow(2.0, 3.0) // 8.0
```

## Next Steps

- [Classes and Objects](classes-and-objects.md) - OOP in Onion
- [Inheritance](inheritance.md) - Extending Java classes
- [Examples](../examples/oop.md) - Real-world examples
