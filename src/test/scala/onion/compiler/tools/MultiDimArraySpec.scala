package onion.compiler.tools

import onion.tools.Shell

class MultiDimArraySpec extends AbstractShellSpec {
  describe("Multi-dimensional array support") {
    it("creates and accesses 2D Int arrays") {
      val result = shell.run(
        """
          |class Test1 {
          |public:
          |  static def main(args: String[]): Int {
          |    var arr: Int[][] = new Int[3][4];
          |    arr[0][0] = 10;
          |    arr[1][2] = 25;
          |    arr[2][3] = 99;
          |    var sum: Int = arr[0][0] + arr[1][2] + arr[2][3];
          |    return sum;
          |  }
          |}
        """.stripMargin,
        "TwoDArray.on",
        Array()
      )
      assert(Shell.Success(134) == result)
    }

    it("creates and accesses 3D String arrays") {
      val result = shell.run(
        """
          |class Test1 {
          |public:
          |  static def main(args: String[]): Int {
          |    var arr: String[][][] = new String[2][3][4];
          |    arr[0][0][0] = "Hello";
          |    arr[1][2][3] = "World";
          |
          |    if (arr[0][0][0] == "Hello" && arr[1][2][3] == "World") {
          |      return 42;
          |    }
          |    return 0;
          |  }
          |}
        """.stripMargin,
        "ThreeDArray.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("creates 2D arrays of different sizes per dimension") {
      val result = shell.run(
        """
          |class Test1 {
          |public:
          |  static def main(args: String[]): Int {
          |    var arr: Int[][] = new Int[5][10];
          |    arr[4][9] = 123;
          |    return arr[4][9];
          |  }
          |}
        """.stripMargin,
        "JaggedArray.on",
        Array()
      )
      assert(Shell.Success(123) == result)
    }

    it("works with array length for multi-dimensional arrays") {
      val result = shell.run(
        """
          |class Test1 {
          |public:
          |  static def main(args: String[]): Int {
          |    var arr: Int[][] = new Int[3][4];
          |    var rows: Int = arr.length;
          |    var cols: Int = arr[0].length;
          |    return rows * 10 + cols;  // Should return 34
          |  }
          |}
        """.stripMargin,
        "ArrayLength.on",
        Array()
      )
      assert(Shell.Success(34) == result)
    }

    it("supports multiple assignments to 2D arrays") {
      val result = shell.run(
        """
          |class Test1 {
          |public:
          |  static def main(args: String[]): Int {
          |    var arr: Int[][] = new Int[3][3];
          |
          |    arr[0][0] = 1;
          |    arr[0][1] = 2;
          |    arr[0][2] = 3;
          |    arr[1][0] = 4;
          |    arr[1][1] = 5;
          |    arr[1][2] = 6;
          |    arr[2][0] = 7;
          |    arr[2][1] = 8;
          |    arr[2][2] = 9;
          |
          |    var sum: Int = arr[0][0] + arr[0][1] + arr[0][2] +
          |                   arr[1][0] + arr[1][1] + arr[1][2] +
          |                   arr[2][0] + arr[2][1] + arr[2][2];
          |
          |    return sum;  // 1+2+3+4+5+6+7+8+9 = 45
          |  }
          |}
        """.stripMargin,
        "MultipleAssignments.on",
        Array()
      )
      assert(Shell.Success(45) == result)
    }
  }
}
