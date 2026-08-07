package onion.compiler.tools

import onion.tools.Shell

class GenericArrayTypeInferenceSpec extends AbstractShellSpec {
  describe("Generic type parameter inference over array-typed arguments") {
    it("infers T from a 1D T[] argument") {
      val result = shell.run(
        """
          |class Util {
          |public:
          |  static def first[T](arr: T[]): T {
          |    return arr[0];
          |  }
          |
          |  static def main(args: String[]): Int {
          |    var arr: String[] = new String[3];
          |    arr[0] = "seven";
          |    var r: String = Util::first(arr);
          |    if (r == "seven") { return 1; }
          |    return 0;
          |  }
          |}
        """.stripMargin,
        "GenericArray1D.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }

    it("infers T from a 2D T[][] argument") {
      val result = shell.run(
        """
          |class Util {
          |public:
          |  static def first[T](arr: T[][]): T {
          |    return arr[0][0];
          |  }
          |
          |  static def main(args: String[]): Int {
          |    var arr: String[][] = new String[3][4];
          |    arr[0][0] = "forty-two";
          |    var r: String = Util::first(arr);
          |    if (r == "forty-two") { return 1; }
          |    return 0;
          |  }
          |}
        """.stripMargin,
        "GenericArray2D.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }

    it("infers T from a 3D T[][][] argument") {
      val result = shell.run(
        """
          |class Util {
          |public:
          |  static def first[T](arr: T[][][]): T {
          |    return arr[0][0][0];
          |  }
          |
          |  static def main(args: String[]): Int {
          |    var arr: String[][][] = new String[2][3][4];
          |    arr[0][0][0] = "ninety-nine";
          |    var r: String = Util::first(arr);
          |    if (r == "ninety-nine") { return 1; }
          |    return 0;
          |  }
          |}
        """.stripMargin,
        "GenericArray3D.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }
  }
}
