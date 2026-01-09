package onion.compiler.tools

import onion.tools.Shell

class BitwiseCompoundAssignmentSpec extends AbstractShellSpec {
  describe("Bitwise AND assignment (&=)") {
    it("computes bitwise AND with assignment") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = 15;
          |    x &= 10;
          |    return x;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      // 15 & 10 = 0b1111 & 0b1010 = 0b1010 = 10
      assert(Shell.Success(10) == result)
    }

    it("works with larger values") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = 255;
          |    x &= 15;
          |    return x;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(15) == result)
    }
  }

  describe("Bitwise OR assignment (|=)") {
    it("computes bitwise OR with assignment") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = 10;
          |    x |= 5;
          |    return x;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      // 10 | 5 = 0b1010 | 0b0101 = 0b1111 = 15
      assert(Shell.Success(15) == result)
    }

    it("works with zero") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = 0;
          |    x |= 42;
          |    return x;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(42) == result)
    }
  }

  describe("Bitwise XOR assignment (^=)") {
    it("computes bitwise XOR with assignment") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = 15;
          |    x ^= 5;
          |    return x;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      // 15 ^ 5 = 0b1111 ^ 0b0101 = 0b1010 = 10
      assert(Shell.Success(10) == result)
    }

    it("XOR with same value results in zero") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = 12345;
          |    x ^= 12345;
          |    return x;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(0) == result)
    }
  }

  describe("Left shift assignment (<<=)") {
    it("shifts bits left with assignment") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = 1;
          |    x <<= 4;
          |    return x;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(16) == result)
    }

    it("works with multiple shifts") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = 3;
          |    x <<= 2;
          |    x <<= 1;
          |    return x;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(24) == result)
    }
  }

  describe("Arithmetic right shift assignment (>>=)") {
    it("shifts bits right with sign extension") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = 32;
          |    x >>= 2;
          |    return x;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(8) == result)
    }

    it("preserves sign for negative numbers") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = -16;
          |    x >>= 2;
          |    return x;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(-4) == result)
    }
  }

  describe("Logical right shift assignment (>>>=)") {
    it("shifts bits right without sign extension for positive") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = 64;
          |    x >>>= 3;
          |    return x;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(8) == result)
    }

    it("fills with zeros for negative numbers") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = -1;
          |    x >>>= 28;
          |    return x;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(15) == result)
    }
  }

  describe("Combined bitwise operations") {
    it("chains multiple bitwise compound assignments") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = 240;
          |    x &= 252;
          |    x |= 3;
          |    x ^= 15;
          |    return x;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      // 240 & 252 = 240 (0b11110000)
      // 240 | 3 = 243 (0b11110011)
      // 243 ^ 15 = 252 (0b11111100)
      assert(Shell.Success(252) == result)
    }

    it("works with Long type") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Long {
          |    var x: Long = 1L;
          |    x <<= 32;
          |    x |= 1L;
          |    return x;
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(4294967297L) == result)
    }
  }
}
