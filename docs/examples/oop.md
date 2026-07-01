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

    def haveBirthday: void {
      this.age = this.age + 1
      println("Happy birthday! Now " + this.age)
    }
}

val person: Person = new Person("Alice", 30)
println(person.greet())
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
println(dog.speak())
println("Breed: " + dog.getBreed())
```

**Output:**
```
Buddy barks: Woof!
Breed: Golden Retriever
```

## Interfaces

Implementing Java interfaces:

```onion
import {
  java.lang.Comparable;
  java.util.Arrays;
}

class Student <: Comparable[Object] {
  val name: String
  val grade: Int

  public:
    def this(n: String, g: Int) {
      this.name = n
      this.grade = g
    }

    def compareTo(other: Object): Int {
      val otherStudent: Student = (other as Student)
      return this.grade - otherStudent.grade
    }

    def toString: String = this.name + ": " + this.grade
}

val students: Student[] = new Student[3]
students[0] = new Student("Alice", 85)
students[1] = new Student("Bob", 92)
students[2] = new Student("Charlie", 78)

// Sort students by grade
Arrays::sort(students)

foreach s :Student in students {
  println(s.toString())
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
interface Logger {
  def log(message: String): void
  def count(): Int
}

class BasicLogger <: Logger {
  var n: Int

  public:
    def this { this.n = 0 }
    def log(message: String): void {
      this.n = this.n + 1
      println(message)
    }
    def count(): Int = n
}

class Delegation <: Logger {
  forward val delegate: Logger

  public:
    def this {
      this.delegate = new BasicLogger();
    }

    static def main(args: String[]): void {
      val logger: Logger = new Delegation;
      logger.log("a");
      logger.log("b");
      logger.log("c");

      println("total: " + logger.count());
    }
}
```

**Topics:**
- `forward` directive delegates interface methods
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
    def setName(name: String): void {
      this.name = name
    }

    def setValue(value: Int): void {
      this.value = value
    }

    static def main(args: String[]): void {
      // Create bean
      val bean: ExampleBean = new ExampleBean("Test", 42)

      // Serialize to XML
      val out: ByteArrayOutputStream = new ByteArrayOutputStream
      val encoder: XMLEncoder = new XMLEncoder(out)
      encoder.writeObject(bean)
      encoder.close()

      println("Serialized bean")

      // Deserialize from XML
      val inStream: ByteArrayInputStream = new ByteArrayInputStream(
        out.toByteArray()
      )
      val decoder: XMLDecoder = new XMLDecoder(inStream)
      val restored: ExampleBean = decoder.readObject() as ExampleBean
      decoder.close()

      println("Name: " + restored.getName())
      println("Value: " + restored.getValue())
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

      val buttons: java.util.List[String] = [
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

    def actionPerformed(event :ActionEvent): void {
      val button: JButton = event.getSource() as JButton
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

    def calculate: void {
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

    static def main(args :String[]): void {
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
