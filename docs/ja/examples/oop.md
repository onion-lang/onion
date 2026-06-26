# オブジェクト指向の例

Onionでのオブジェクト指向プログラミングを示す例です。

## クラスとオブジェクト

基本的なクラス定義:

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

**出力:**
```
Hello, I'm Alice and I'm 30 years old
Happy birthday! Now 31
```

## 継承

クラスの拡張:

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

**出力:**
```
Buddy barks: Woof!
Breed: Golden Retriever
```

## インターフェース

Javaインターフェースの実装:

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
      val otherStudent: Student = (other as Student)
      return this.grade - otherStudent.grade
    }

    def toString: String = this.name + ": " + this.grade
}

val students: Student[] = new Student[3]
students[0] = new Student("Alice", 85)
students[1] = new Student("Bob", 92)
students[2] = new Student("Charlie", 78)

// 成績順にソート
import { java.util.Arrays; }
Arrays::sort(students)

foreach s :Student in students {
  IO::println(s.toString())
}
```

**出力:**
```
Charlie: 78
Alice: 85
Bob: 92
```

## 委譲パターン

`forward` を使った委譲:

**ファイル: `Delegation.on`**
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

    static def main(args: String[]): void {
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

**トピック:**
- `forward` によるインターフェースメソッドの委譲
- `<<` 演算子による要素追加
- 委譲によるインターフェース実装

## JavaBean パターン

**ファイル: `Bean.on`**
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
      // bean用のデフォルトコンストラクタが必要
    }

    def this(name: String, value: Int) {
      this.name = name
      this.value = value
    }

    // ゲッター
    def getName: String = this.name
    def getValue: Int = this.value

    // セッター
    def setName(name: String) {
      this.name = name
    }

    def setValue(value: Int) {
      this.value = value
    }

    static def main(args: String[]): void {
      // beanの作成
      val bean: ExampleBean = new ExampleBean("Test", 42)

      // XMLへのシリアライズ
      val out: ByteArrayOutputStream = new ByteArrayOutputStream
      val encoder: XMLEncoder = new XMLEncoder(out)
      encoder.writeObject(bean)
      encoder.close()

      IO::println("Serialized bean")

      // XMLからのデシリアライズ
      val inStream: ByteArrayInputStream = new ByteArrayInputStream(
        out.toByteArray()
      )
      val decoder: XMLDecoder = new XMLDecoder(inStream)
      val restored: ExampleBean = decoder.readObject() as ExampleBean
      decoder.close()

      IO::println("Name: " + restored.getName())
      IO::println("Value: " + restored.getValue())
    }
}
```

**トピック:**
- JavaBeanの規約
- ゲッターとセッターメソッド
- XMLEncoder/XMLDecoder によるシリアライズ
- 複数のコンストラクタ

## GUI アプリケーション

**ファイル: `Calculator.on`**（簡略版）
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
      val button: JButton = event.getSource() as JButton
      val label: String = button.getText()

      // ボタンクリックの処理
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
        // 数字ボタン
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

    static def main(args :String[]): void {
      val calc: Calculator = new Calculator
    }
}
```

**トピック:**
- 多重継承（JFrameの拡張とActionListenerの実装）
- Swing GUIコンポーネント
- イベント処理
- `this` 参照
- 演算のための `select` 文

## 次のステップ

- [関数型の例](functional.md) - ラムダとクロージャ
- [基本例](basic.md) - よりシンプルなプログラム
- [継承ガイド](../guide/inheritance.md) - OOPの詳細
