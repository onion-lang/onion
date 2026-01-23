package onion.compiler.tools

import onion.tools.Shell

class RecordSpec extends AbstractShellSpec {
  describe("Record type") {
    it("creates record with components and getters") {
      val result = shell.run(
        """
          |record Person(name: String, age: Int);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Person("Alice", 30);
          |    return p.name() + ":" + p.age();
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Alice:30") == result)
    }

    it("supports nested records") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int);
          |record Line(start: Point, end: Point);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val line = new Line(new Point(0, 0), new Point(10, 20));
          |    return line.start().x() + "," + line.start().y() + "-" + line.end().x() + "," + line.end().y();
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("0,0-10,20") == result)
    }
  }

  // Data Class functionality: equals, hashCode, toString, copy
  describe("Record Data Class methods") {
    it("auto-generates equals method") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p1 = new Point(1, 2);
          |    val p2 = new Point(1, 2);
          |    val p3 = new Point(3, 4);
          |    return "" + p1.equals(p2) + "," + p1.equals(p3);
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("true,false") == result)
    }

    it("equals returns true for same instance") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p1 = new Point(1, 2);
          |    return "" + p1.equals(p1);
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("true") == result)
    }

    it("equals returns false for null") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p1 = new Point(1, 2);
          |    return "" + p1.equals(null);
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("false") == result)
    }

    it("equals works with object fields") {
      val result = shell.run(
        """
          |record Person(name: String, age: Int);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p1 = new Person("Alice", 30);
          |    val p2 = new Person("Alice", 30);
          |    val p3 = new Person("Bob", 30);
          |    return "" + p1.equals(p2) + "," + p1.equals(p3);
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("true,false") == result)
    }

    it("auto-generates hashCode method") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p1 = new Point(1, 2);
          |    val p2 = new Point(1, 2);
          |    return "" + (p1.hashCode() == p2.hashCode());
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("true") == result)
    }

    it("hashCode is different for different values") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p1 = new Point(1, 2);
          |    val p2 = new Point(3, 4);
          |    return "" + (p1.hashCode() != p2.hashCode());
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("true") == result)
    }

    it("auto-generates toString method") {
      val result = shell.run(
        """
          |record Person(name: String, age: Int);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Person("Alice", 30);
          |    return p.toString();
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Person(name=Alice, age=30)") == result)
    }

    it("toString works with multiple primitive types") {
      val result = shell.run(
        """
          |record Data(x: Int, y: Long, z: Double, flag: Boolean);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val d = new Data(42, 100L, 3.14, true);
          |    return d.toString();
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Data(x=42, y=100, z=3.14, flag=true)") == result)
    }

    it("auto-generates copy method") {
      val result = shell.run(
        """
          |record Person(name: String, age: Int);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p1 = new Person("Alice", 30);
          |    val p2 = p1.copy("Alice", 31);
          |    return p2.toString();
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Person(name=Alice, age=31)") == result)
    }

    it("copy creates new instance") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p1 = new Point(1, 2);
          |    val p2 = p1.copy(1, 2);
          |    return "" + (p1 === p2) + "," + p1.equals(p2);
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("false,true") == result)
    }

    it("all Data Class methods work together") {
      val result = shell.run(
        """
          |record Person(name: String, age: Int);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p1 = new Person("Alice", 30);
          |    val p2 = p1.copy("Alice", 30);
          |    val p3 = p1.copy("Bob", 25);
          |    val eq = p1.equals(p2);
          |    val hash = p1.hashCode() == p2.hashCode();
          |    val neq = !p1.equals(p3);
          |    val str = p1.toString();
          |    return "" + eq + "," + hash + "," + neq + "," + str;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("true,true,true,Person(name=Alice, age=30)") == result)
    }
  }
}
