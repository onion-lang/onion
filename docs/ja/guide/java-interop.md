# Javaとの相互運用

OnionはJavaとシームレスに連携できます。あらゆるJavaライブラリをOnionから直接使用できます。

## Javaクラスのインポート

`import { }` でJavaクラスをインポートします。エイリアスも使えます：

```onion
import {
  java.util.ArrayList;
  java.util.HashMap;
  java.io.File;
  java.lang.Long as JLong;
}

val list: ArrayList = new ArrayList
val map: HashMap = new HashMap
val file: File = new File("data.txt")
val str: String = JLong::toString(42L)
```

インポートなしで完全修飾名を使うこともできます：

```onion
val list: java.util.ArrayList = new java.util.ArrayList
```

## Javaオブジェクトの生成とメソッド呼び出し

`new` でオブジェクトを生成し、メソッドを通常どおり呼び出せます：

```onion
import { java.lang.StringBuilder; }

val result: String = new StringBuilder("Hello")
  .append(" ")
  .append("World")
  .append("!")
  .toString()

IO::println(result)  // "Hello World!"
```

staticメソッドには `::` を使います：

```onion
val random: Double = Math::random()
val sqrt: Double = Math::sqrt(16.0)
val time: Long = System::currentTimeMillis()
val number: Int = JInteger::parseInt("42")
```

## Javaインターフェースの実装

`<:` でJavaインターフェースを実装します：

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

## Javaクラスの拡張

`:` でJavaクラスを継承できます：

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

## 型キャスト

`as` 演算子でJavaオブジェクトをキャストします：

```onion
val obj: Object = "Hello"
val str: String = (obj as String)

val list: ArrayList = new ArrayList
list.add("Item")
val item: String = list.get(0) as String

val random: Double = Math::random()
val randomInt: Int = (random * 100) as Int
```

## よく使うJavaクラス

### String

```onion
val text: String = "Hello, World!"
val upper: String = text.toUpperCase()
val lower: String = text.toLowerCase()
val length: Int = text.length()
val sub: String = text.substring(0, 5)
val contains: Boolean = text.contains("World")
```

### 数値ユーティリティ

```onion
val i: Int = JInteger::parseInt("42")
val l: Long = JLong::parseLong("1234567890")
val max: Int = JInteger::MAX_VALUE
```

### Math

```onion
val abs: Int = Math::abs(-10)
val ceil: Double = Math::ceil(3.14)
val pow: Double = Math::pow(2.0, 3.0)
```

## ファイルI/O

```onion
import {
  java.io.File;
  java.io.FileReader;
  java.io.BufferedReader;
  java.io.IOException;
}

def readFile(filename: String) {
  try {
    val reader: BufferedReader = new BufferedReader(
      new FileReader(filename)
    )

    var line: String = null
    while (line = reader.readLine()) != null {
      IO::println(line)
    }

    reader.close()
  } catch e: IOException {
    IO::println("Error reading file: " + e.getMessage())
  }
}
```

なお、Onionには `file"path"` リテラルを使った簡潔なファイル操作もあります：

```onion
val text: String = file"data.txt".text()
val lines: List = file"data.txt".lines()
```

## 次のステップ

- [継承](inheritance.md) - Javaクラスの拡張方法
- [クラスとオブジェクト](classes-and-objects.md) - Onionのクラス設計
- [標準ライブラリ](../reference/stdlib.md) - Onion組み込みのユーティリティ
