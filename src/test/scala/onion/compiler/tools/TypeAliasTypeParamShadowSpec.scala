package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression test for issue #2510: a module `type` alias named like a declaration's own
 * type parameter (`type T = Int` next to `class Box[T]`) won, so inside `Box` the name `T`
 * meant `Int` and `new Box[String]("x")` was E0021 against a constructor `Box(Int)` nobody
 * wrote. A type parameter now shadows a same-named alias inside its declaration, as in Java
 * and Scala; outside it, the alias still applies.
 */
class TypeAliasTypeParamShadowSpec extends AbstractShellSpec {

  private def runMain(body: String): Shell.Result =
    shell.run(body.stripMargin, "None", Array())

  describe("a type parameter named like a module alias") {
    it("shadows the alias inside a generic class") {
      val result = runMain(
        """type T = Int
          |class Box[T] {
          |  val v: T
          |public:
          |  def this(x: T) { v = x }
          |  def get: T = v
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    return new Box[String]("x").get()
          |  }
          |}
          |""")
      assert(Shell.Success("x") == result)
    }

    it("shadows the alias inside a generic record") {
      val result = runMain(
        """type T = Int
          |record Box[T](v: T)
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    return new Box[String]("y").v()
          |  }
          |}
          |""")
      assert(Shell.Success("y") == result)
    }

    it("shadows the alias inside a generic method") {
      val result = runMain(
        """type T = Int
          |class Main {
          |public:
          |  static def id[T](x: T): T = x
          |  static def main(args: String[]): String {
          |    return id("z")
          |  }
          |}
          |""")
      assert(Shell.Success("z") == result)
    }

    it("shadows a parameterized alias of the same name") {
      val result = runMain(
        """type K[A] = List[A]
          |class Holder[K] {
          |  val k: K
          |public:
          |  def this(x: K) { k = x }
          |  def get: K = k
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    return new Holder[String]("w").get()
          |  }
          |}
          |""")
      assert(Shell.Success("w") == result)
    }

    it("still resolves the alias outside the declaration") {
      val result = runMain(
        """type T = Int
          |class Box[T] {
          |  val v: T
          |public:
          |  def this(x: T) { v = x }
          |  def get: T = v
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val n: T = 41
          |    return new Box[String]("x").get().length() + n
          |  }
          |}
          |""")
      assert(Shell.Success(42) == result)
    }

    it("is a parameter in a static member too, since the class's [T] reaches it") {
      // Onion scopes a class's type parameters over its static members as well, so the
      // one rule holds there: the alias is shadowed wherever `[T]` is in scope.
      val result = runMain(
        """type T = Int
          |class Box[T] {
          |public:
          |  static def echo(x: T): T = x
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    return Box::echo("s") as String
          |  }
          |}
          |""")
      assert(Shell.Success("s") == result)
    }
  }

  // An alias's target is resolved where the alias is declared, with only the alias's own
  // type parameters in scope: never with the [T] of the class or method that uses it.
  describe("an alias used inside a generic declaration") {
    it("keeps a non-generic alias chain's meaning (no ClassCastException)") {
      val result = runMain(
        """type T = Int
          |type U = T
          |class Reg[T] {
          |public:
          |  def this {}
          |  def one(): U = 1
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    return new Reg[String]().one() + 1
          |  }
          |}
          |""")
      assert(Shell.Success(2) == result)
    }

    it("keeps an alias argument that names a module alias") {
      val result = runMain(
        """type S = String
          |type Wrap = List[S]
          |class C[S] {
          |public:
          |  def this {}
          |  def f(w: Wrap): String = w[0]
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    return new C[Int]().f(["hi"])
          |  }
          |}
          |""")
      assert(Shell.Success("hi") == result)
    }

    it("keeps an alias argument that names a module class") {
      val result = runMain(
        """class S {
          |public:
          |  def this {}
          |  def tag: String = "s"
          |}
          |type Wrap = List[S]
          |class C[S] {
          |public:
          |  def this {}
          |  def f(w: Wrap): String = w[0].tag()
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    return new C[Int]().f([new S()])
          |  }
          |}
          |""")
      assert(Shell.Success("s") == result)
    }

    it("keeps an alias used inside a generic method") {
      val result = runMain(
        """type T = String
          |type Names = List[T]
          |class Main {
          |public:
          |  static def first[T](n: Names, x: T): Int = n[0].length()
          |  static def main(args: String[]): Int {
          |    return first(["abc"], 7)
          |  }
          |}
          |""")
      assert(Shell.Success(3) == result)
    }

    it("does not let a generic alias's parameter meet a class's same-named [A]") {
      val result = runMain(
        """type Pair[A] = Map[String, A]
          |class C[A] {
          |public:
          |  def this {}
          |  def get(p: Pair[Int]): Int = p["k"]
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    return new C[String]().get(["k": 5]) + 1
          |  }
          |}
          |""")
      assert(Shell.Success(6) == result)
    }

    it("does not let a generic alias's parameter meet a method's same-named [A]") {
      val result = runMain(
        """type Pair[A] = Map[String, A]
          |class Main {
          |public:
          |  static def f[A](p: Pair[Int], x: A): Int = p["k"]
          |  static def main(args: String[]): Int {
          |    return f(["k": 3], "z") + 1
          |  }
          |}
          |""")
      assert(Shell.Success(4) == result)
    }
  }
}
