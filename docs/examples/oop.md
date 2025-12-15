# Object-Oriented Examples

Examples demonstrating object-oriented programming in Onion.

## Classes and Objects

Basic class definition:

```onion
class Person {
  @name :String
  @age :Int

  public:
    def new(n :String, a :Int) {
      @name = n
      @age = a
    }

    def greet :String {
      "Hello, I'm " + @name + " and I'm " + @age + " years old"
    }

    def haveBirthday {
      @age = @age + 1
      IO::println("Happy birthday! Now " + @age)
    }
}

def person :Person = new Person("Alice", 30)
IO::println(person.greet())
person.haveBirthday()
```

**Output:**
```
Hello, I'm Alice and I'm 30 years old
Happy birthday! Now 31
```

## Inheritance

Extending classes:

```onion
class Animal {
  @name :String

  public:
    def new(n :String) {
      @name = n
    }

    def speak :String {
      @name + " makes a sound"
    }
}

class Dog : Animal {
  @breed :String

  public:
    def new(n :String, b :String) {
      super.new(n)
      @breed = b
    }

    def speak :String {
      @name + " barks: Woof!"
    }

    def getBreed :String {
      @breed
    }
}

def dog :Dog = new Dog("Buddy", "Golden Retriever")
IO::println(dog.speak())
IO::println("Breed: " + dog.getBreed())
```

**Output:**
```
Buddy barks: Woof!
Breed: Golden Retriever
```

## Interfaces

Implementing Java interfaces:

```onion
import { java.lang.Comparable; }

class Student <: Comparable {
  @name :String
  @grade :Int

  public:
    def new(n :String, g :Int) {
      @name = n
      @grade = g
    }

    def compareTo(other :Object) :Int {
      def otherStudent :Student = other$Student
      @grade - otherStudent.@grade
    }

    def toString :String {
      @name + ": " + @grade
    }
}

def students :Student[] = new Student[3]
students[0] = new Student("Alice", 85)
students[1] = new Student("Bob", 92)
students[2] = new Student("Charlie", 78)

// Sort students by grade
import { java.util.Arrays; }
Arrays::sort(students)

foreach s :Student in students {
  IO::println(s.toString())
}
```

**Output:**
```
Charlie: 78
Alice: 85
Bob: 92
```

## Delegation Pattern

Using `forward` for delegation:

**File: `Delegation.on`**
```onion
import {
  java.util.List;
  java.util.ArrayList;
}

class Delegation <: List {
  forward val n: List;

  public:
    def this {
      this.n = new ArrayList;
    }

    static def main(args: String[]) {
      val list: List = new Delegation;
      list << "a";
      list << "b";
      list << "c";

      for var i: Int = 0; i < list.size; i = i + 1 {
        IO::println(list[i]);
      }
    }
}
```

**Topics:**
- `forward` directive delegates interface methods
- `<<` operator for adding elements
- Implementing interfaces via delegation

## JavaBean Pattern

**File: `Bean.on`**
```onion
import {
  java.io.Serializable;
  java.beans.XMLEncoder;
  java.beans.XMLDecoder;
  java.io.ByteArrayOutputStream;
  java.io.ByteArrayInputStream;
}

class ExampleBean <: Serializable {
  @name :String
  @value :Int

  public:
    def new {
      // Default constructor required for beans
    }

    def this(name :String, value :Int) {
      @name = name
      @value = value
    }

    // Getters
    def getName :String {
      @name
    }

    def getValue :Int {
      @value
    }

    // Setters
    def setName(name :String) {
      @name = name
    }

    def setValue(value :Int) {
      @value = value
    }

    static def main(args :String[]) {
      // Create bean
      def bean :ExampleBean = new ExampleBean("Test", 42)

      // Serialize to XML
      def out :ByteArrayOutputStream = new ByteArrayOutputStream
      def encoder :XMLEncoder = new XMLEncoder(out)
      encoder.writeObject(bean)
      encoder.close()

      IO::println("Serialized bean")

      // Deserialize from XML
      def in :ByteArrayInputStream = new ByteArrayInputStream(
        out.toByteArray()
      )
      def decoder :XMLDecoder = new XMLDecoder(in)
      def restored :ExampleBean = decoder.readObject()$ExampleBean
      decoder.close()

      IO::println("Name: " + restored.getName())
      IO::println("Value: " + restored.getValue())
    }
}
```

**Topics:**
- JavaBean conventions
- Getter and setter methods
- Serialization with XMLEncoder/XMLDecoder
- Multiple constructors

## GUI Application

**File: `Calculator.on`** (simplified)
```onion
import {
  javax.swing.JFrame;
  javax.swing.JButton;
  javax.swing.JTextField;
  javax.swing.JPanel;
  java.awt.event.ActionListener;
  java.awt.event.ActionEvent;
  java.awt.GridLayout;
  java.awt.BorderLayout;
}

class Calculator : JFrame <: ActionListener {
  @text :JTextField
  @currentValue :Long
  @operator :String

  public:
    def new {
      setTitle("Simple Calculator")
      setSize(300, 400)
      setLayout(new BorderLayout())

      @text = new JTextField("0")
      add(@text, BorderLayout::NORTH)

      def panel :JPanel = new JPanel()
      panel.setLayout(new GridLayout(4, 4))

      def buttons :String[] = [
        "7", "8", "9", "/",
        "4", "5", "6", "*",
        "1", "2", "3", "-",
        "0", "C", "=", "+"
      ]

      foreach label :String in buttons {
        def button :JButton = new JButton(label)
        button.addActionListener(self)
        panel.add(button)
      }

      add(panel, BorderLayout::CENTER)

      @currentValue = 0L
      @operator = null

      setDefaultCloseOperation(JFrame::EXIT_ON_CLOSE)
      setVisible(true)
    }

    def actionPerformed(event :ActionEvent) {
      def button :JButton = event.getSource()$JButton
      def label :String = button.getText()

      // Handle button click
      if label == "C" {
        @text.setText("0")
        @currentValue = 0L
        @operator = null
      } else if label == "=" {
        calculate()
      } else if label == "+" || label == "-" ||
                label == "*" || label == "/" {
        @operator = label
        @currentValue = JLong::parseLong(@text.getText())
        @text.setText("0")
      } else {
        // Number button
        def current :String = @text.getText()
        if current == "0" {
          @text.setText(label)
        } else {
          @text.setText(current + label)
        }
      }
    }

    def calculate {
      if @operator != null {
        def value :Long = JLong::parseLong(@text.getText())
        def result :Long = 0L

        select @operator {
          case "+":
            result = @currentValue + value
          case "-":
            result = @currentValue - value
          case "*":
            result = @currentValue * value
          case "/":
            result = @currentValue / value
        }

        @text.setText(JLong::toString(result))
        @operator = null
      }
    }

    static def main(args :String[]) {
      def calc :Calculator = new Calculator
    }
}
```

**Topics:**
- Multiple inheritance (extends JFrame, implements ActionListener)
- Swing GUI components
- Event handling
- `self` reference
- `select` statement for operations

## Next Steps

- [Functional Examples](functional.md) - Lambdas and closures
- [Basic Examples](basic.md) - Simpler programs
- [Inheritance Guide](../guide/inheritance.md) - Learn more about OOP
