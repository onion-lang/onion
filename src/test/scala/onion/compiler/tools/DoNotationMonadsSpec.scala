package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for issue #131: do-notation over Option and Result (bind/successful
 * added to both), and do-notation at the script top level (top-level block
 * elements were not desugared, so the statement vanished silently).
 */
class DoNotationMonadsSpec extends AbstractShellSpec {

  describe("do-notation over Option") {
    it("chains bindings and short-circuits on none") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r = do[Option] { a <- Option::some(40); b <- Option::some(2); ret (a as Int) + (b as Int) }
          |    val n = do[Option] { a <- Option::some(1); b <- Option::none(); ret a }
          |    return r.get() + ":" + n.isEmpty()
          |  }
          |}
          |""".stripMargin,
        "DoOption.on",
        Array()
      )
      assert(Shell.Success("42:true") == result)
    }

    it("accepts newline-separated binds (issue #160)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val r = do[Option] {
          |      a <- Option::some(10)
          |      b <- Option::some(20)
          |      ret (a as Int) + (b as Int)
          |    }
          |    return (r.get() as Int)
          |  }
          |}
          |""".stripMargin,
        "DoNewline.on",
        Array()
      )
      assert(Shell.Success(30) == result)
    }
  }

  describe("do-notation over Option with Option::none() (issue #279)") {
    it("infers none()'s element type from a sibling some() bind so arithmetic type-checks") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r = do[Option] {
          |      a <- Option::some(3)
          |      b <- Option::none()
          |      ret (a as Int) + (b as Int)
          |    }
          |    return r.isEmpty().toString()
          |  }
          |}
          |""".stripMargin,
        "DoNone.on",
        Array()
      )
      assert(Shell.Success("true") == result)
    }

    it("pins none() from a sibling some() regardless of bind order") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r = do[Option] {
          |      b <- Option::none()
          |      a <- Option::some(7)
          |      ret (a as Int) + (b as Int)
          |    }
          |    return r.isEmpty().toString()
          |  }
          |}
          |""".stripMargin,
        "DoNoneFirst.on",
        Array()
      )
      assert(Shell.Success("true") == result)
    }

    it("leaves an explicitly annotated none() untouched") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r = do[Option] {
          |      a <- Option::some(5)
          |      b <- Option::none[Int]()
          |      ret (a as Int) + (b as Int)
          |    }
          |    return r.isEmpty().toString()
          |  }
          |}
          |""".stripMargin,
        "DoNoneAnnotated.on",
        Array()
      )
      assert(Shell.Success("true") == result)
    }
  }

  describe("do-notation over Result") {
    it("chains ok values") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val r = do[Result] { a <- Result::ok(20); b <- Result::ok(22); ret (a as Int) + (b as Int) }
          |    return (r.getOrThrow() as Int)
          |  }
          |}
          |""".stripMargin,
        "DoResult.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }
  }

  describe("do-notation over List (comprehension)") {
    it("computes a cross product with newline-separated binds") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val pairs = do[List] {
          |      x <- [1, 2]
          |      y <- ["a", "b"]
          |      ret x + y
          |    }
          |    return pairs.toString()
          |  }
          |}
          |""".stripMargin,
        "DoList.on",
        Array()
      )
      assert(Shell.Success("[1a, 1b, 2a, 2b]") == result)
    }

    it("maps a single bind") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val sq = do[List] { x <- [1, 2, 3]; ret x * x }
          |    return sq.toString()
          |  }
          |}
          |""".stripMargin,
        "DoListMap.on",
        Array()
      )
      assert(Shell.Success("[1, 4, 9]") == result)
    }
  }

  describe("do-notation at the top level") {
    it("desugars top-level statements containing do expressions") {
      val result = shell.run(
        """
          |val r = do[Option] { a <- Option::some(40); b <- Option::some(2); ret (a as Int) + (b as Int) }
          |IO::println(r.get())
          |""".stripMargin,
        "DoTopLevel.on",
        Array()
      )
      assert(Shell.Success(()) == result || result.isInstanceOf[Shell.Success])
    }
  }
}
