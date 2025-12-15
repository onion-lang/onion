# Object-Oriented Examples

Examples demonstrating object-oriented programming in Onion.

## Classes and Objects

Basic class definition:

```onion
class Person {
  val name: String
  var age: Int

  public:
    def this(n: String, a: Int) {
      this.name = n
      this.age = a
    }

    def greet: String = "Hello, I'm " + this.name + " and I'm " + this.age + " years old"

    def haveBirthday {
      this.age = this.age + 1
      IO::println("Happy birthday! Now " + this.age)
    }
}

val person: Person = new Person("Alice", 30)
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
protected:
  val name: String

  public:
    def this(n: String) {
      this.name = n
    }

    def speak: String = this.name + " makes a sound"
}

class Dog : Animal {
  val breed: String

  public:
    def this(n: String, b: String): (n) {
      this.breed = b
    }

    def speak: String = this.name + " barks: Woof!"

    def getBreed: String = this.breed
}

val dog: Dog = new Dog("Buddy", "Golden Retriever")
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
  val name: String
  val grade: Int

  public:
    def this(n: String, g: Int) {
      this.name = n
      this.grade = g
    }

    def compareTo(other: Object): Int {
      val otherStudent: Student = other$Student
      return this.grade - otherStudent.grade
    }

    def toString: String = this.name + ": " + this.grade
}

val students: Student[] = new Student[3]
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
  var name: String
  var value: Int

  public:
    def this {
      // Default constructor required for beans
    }

    def this(name: String, value: Int) {
      this.name = name
      this.value = value
    }

    // Getters
    def getName: String = this.name
    def getValue: Int = this.value

    // Setters
    def setName(name: String) {
      this.name = name
    }

    def setValue(value: Int) {
      this.value = value
    }

    static def main(args: String[]) {
      // Create bean
      val bean: ExampleBean = new ExampleBean("Test", 42)

      // Serialize to XML
      val out: ByteArrayOutputStream = new ByteArrayOutputStream
      val encoder: XMLEncoder = new XMLEncoder(out)
      encoder.writeObject(bean)
      encoder.close()

      IO::println("Serialized bean")

      // Deserialize from XML
      val inStream: ByteArrayInputStream = new ByteArrayInputStream(
        out.toByteArray()
      )
      val decoder: XMLDecoder = new XMLDecoder(inStream)
      val restored: ExampleBean = decoder.readObject()$ExampleBean
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
  val text: JTextField
  var currentValue: Long
  var operator: String

  public:
    def this {
      setTitle("Simple Calculator")
      setSize(300, 400)
      setLayout(new BorderLayout())

      this.text = new JTextField("0")
      add(this.text, BorderLayout::NORTH)

      val panel: JPanel = new JPanel()
      panel.setLayout(new GridLayout(4, 4))

      val buttons: java.util.List = [
        "7", "8", "9", "/",
        "4", "5", "6", "*",
        "1", "2", "3", "-",
        "0", "C", "=", "+"
      ]

      foreach label: String in buttons {
        val button: JButton = new JButton(label)
        button.addActionListener(this)
        panel.add(button)
      }

      add(panel, BorderLayout::CENTER)

      this.currentValue = 0L
      this.operator = null

      setDefaultCloseOperation(JFrame::EXIT_ON_CLOSE)
      setVisible(true)
    }

    def actionPerformed(event :ActionEvent) {
      val button: JButton = event.getSource()$JButton
      val label: String = button.getText()

      // Handle button click
      if label == "C" {
        this.text.setText("0")
        this.currentValue = 0L
        this.operator = null
      } else if label == "=" {
        calculate()
      } else if label == "+" || label == "-" ||
                label == "*" || label == "/" {
        this.operator = label
        this.currentValue = JLong::parseLong(this.text.getText())
        this.text.setText("0")
      } else {
        // Number button
        val current: String = this.text.getText()
        if current == "0" {
          this.text.setText(label)
        } else {
          this.text.setText(current + label)
        }
      }
    }

    def calculate {
      if this.operator != null {
        val value: Long = JLong::parseLong(this.text.getText())
        var result: Long = 0L

        select this.operator {
          case "+":
            result = this.currentValue + value
          case "-":
            result = this.currentValue - value
          case "*":
            result = this.currentValue * value
          case "/":
            result = this.currentValue / value
        }

        this.text.setText(JLong::toString(result))
        this.operator = null
      }
    }

    static def main(args :String[]) {
      val calc: Calculator = new Calculator
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
