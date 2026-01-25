package onion.compiler.tools

import onion.tools.Shell

class RandomSpec extends AbstractShellSpec {
  describe("Rand module") {
    describe("integer random") {
      it("generates random int in range with bound") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    var allInRange: Boolean = true;
            |    for var i: Int = 0; i < 100; i = i + 1 {
            |      val n = Rand::nextInt(10);
            |      if (n < 0 || n >= 10) {
            |        allInRange = false;
            |      }
            |    }
            |    return "" + allInRange;
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }

      it("generates random int in range with min and max") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    var allInRange: Boolean = true;
            |    for var i: Int = 0; i < 100; i = i + 1 {
            |      val n = Rand::nextInt(5, 15);
            |      if (n < 5 || n >= 15) {
            |        allInRange = false;
            |      }
            |    }
            |    return "" + allInRange;
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }
    }

    describe("double random") {
      it("generates random double between 0 and 1") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    var allInRange: Boolean = true;
            |    for var i: Int = 0; i < 100; i = i + 1 {
            |      val d = Rand::nextDouble();
            |      if (d < 0.0 || d >= 1.0) {
            |        allInRange = false;
            |      }
            |    }
            |    return "" + allInRange;
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }

      it("generates random double with bound") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    var allInRange: Boolean = true;
            |    for var i: Int = 0; i < 100; i = i + 1 {
            |      val d = Rand::nextDouble(5.0);
            |      if (d < 0.0 || d >= 5.0) {
            |        allInRange = false;
            |      }
            |    }
            |    return "" + allInRange;
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }
    }

    describe("boolean random") {
      it("generates both true and false over many iterations") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    var trueCount: Int = 0;
            |    var falseCount: Int = 0;
            |    for var i: Int = 0; i < 100; i = i + 1 {
            |      if (Rand::nextBoolean()) {
            |        trueCount = trueCount + 1;
            |      } else {
            |        falseCount = falseCount + 1;
            |      }
            |    }
            |    return "" + (trueCount > 0 && falseCount > 0);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }
    }

    describe("choice") {
      it("chooses element from array") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val items = new String[3];
            |    items[0] = "a";
            |    items[1] = "b";
            |    items[2] = "c";
            |    val chosen = Rand::choice(items);
            |    return "" + (chosen.equals("a") || chosen.equals("b") || chosen.equals("c"));
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }

      it("returns null for empty array") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val items = new String[0];
            |    val chosen = Rand::choice(items);
            |    return "" + (chosen == null);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }
    }

    describe("shuffle") {
      it("shuffles array preserving all elements") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val items = new String[3];
            |    items[0] = "a";
            |    items[1] = "b";
            |    items[2] = "c";
            |    val shuffled = Rand::shuffle(items);
            |    if (shuffled.size() != 3) {
            |      return "wrong size";
            |    }
            |    var hasA: Boolean = false;
            |    var hasB: Boolean = false;
            |    var hasC: Boolean = false;
            |    for var i: Int = 0; i < shuffled.size(); i = i + 1 {
            |      val s: String = shuffled.get(i);
            |      if (s.equals("a")) { hasA = true; }
            |      if (s.equals("b")) { hasB = true; }
            |      if (s.equals("c")) { hasC = true; }
            |    }
            |    return "" + (hasA && hasB && hasC);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }
    }

    describe("uuid") {
      it("generates valid UUID format") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val uuid = Rand::uuid();
            |    return "" + (uuid.length() == 36);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }

      it("generates unique UUIDs") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val uuid1 = Rand::uuid();
            |    val uuid2 = Rand::uuid();
            |    return "" + !uuid1.equals(uuid2);
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }
    }

    describe("long random") {
      it("generates random long with bound") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    var allInRange: Boolean = true;
            |    for var i: Int = 0; i < 100; i = i + 1 {
            |      val n = Rand::nextLong(1000L);
            |      if (n < 0L || n >= 1000L) {
            |        allInRange = false;
            |      }
            |    }
            |    return "" + allInRange;
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("true") == result)
      }
    }
  }
}
