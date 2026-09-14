package onion.compiler.tools

import onion.tools.Shell

/**
 * A constructor call with at least one named argument (`typeNewObjectWithNamedArgs`
 * / `processNamedArgsForConstructor`) typed every argument with no expected
 * type at all, unlike the positional path (`typeNewObject`), which resolves the
 * target constructor's substituted formal types and both retypes malleable
 * arguments against them (issue #232) and adapts the result with boxing/widening
 * conversions (`adaptToFormals`). `filterConstructorsByNamedArgs` also only
 * checks argument count and parameter names, never argument types, so an
 * incompatible argument was never rejected the way `findConstructor` rejects
 * one on the positional path.
 *
 * This let two distinct bugs through a named argument that plain positional
 * arguments have never allowed since:
 *
 *   - A value whose static type doesn't fit the (possibly generic) formal type
 *     compiled successfully and threw a `ClassCastException` at run time
 *     instead of being rejected with a compile-time diagnostic, e.g.
 *     `new Box[Int](value = "wrong")` against `record Box[T](value: T)`.
 *   - A value needing ordinary numeric widening (an `Int` literal for a
 *     `Double`/`Float`/`Long` parameter) left the argument term unconverted,
 *     corrupting the JVM stack map frames and crashing `BytecodeGeneration`
 *     with an internal `I0000` error -- the exact `ConstructorNumericWideningSpec`
 *     bug, but reachable through a named argument.
 */
class GenericConstructorNamedArgSpec extends AbstractShellSpec {

  describe("a named constructor argument with an incompatible type") {
    it("is rejected at compile time for a generic record, not thrown as a runtime ClassCastException") {
      val result = shell.run(
        """
          |record Box[T](value: T)
          |def main(args: String[]): Int {
          |  val b: Box[Int] = new Box[Int](value = "wrong")
          |  return b.value()
          |}
          |""".stripMargin,
        "NamedArgTypeMismatch.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("is rejected at compile time when mixed with a valid positional argument") {
      val result = shell.run(
        """
          |record Pair[T](first: T, second: T)
          |def main(args: String[]): Int {
          |  val p: Pair[Int] = new Pair[Int](1, second = "wrong")
          |  return p.first()
          |}
          |""".stripMargin,
        "NamedArgTypeMismatchMixed.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }

  describe("a named constructor argument needing numeric widening") {
    it("widens an Int literal to a Double record component instead of crashing codegen") {
      assert(Shell.Success(9900) == shell.run(
        """
          |record Game(name: String, price: Double)
          |def main(args: String[]): Int { return (new Game(name = "Chess", price = 99).price() * 100.0) as Int }
        """.stripMargin, "None", Array()))
    }

    it("widens an Int literal to a Double parameter reached via a default-argument fallback") {
      assert(Shell.Success(9900) == shell.run(
        """
          |class Game {
          |  public: val price: Double
          |  def this(price: Double, tag: String = "x") { this.price = price }
          |}
          |def main(args: String[]): Int { return (new Game(price = 99).price * 100.0) as Int }
        """.stripMargin, "None", Array()))
    }
  }

  describe("an empty list literal in a named constructor argument") {
    it("resolves List[T] against the constructor's own type parameter") {
      val result = shell.run(
        """
          |record Simple[T](value: T, items: List[T])
          |def main(args: String[]): Int {
          |  val s: Simple[Int] = new Simple[Int](items = [], value = 1)
          |  return s.value() + s.items().size
          |}
          |""".stripMargin,
        "EmptyListNamedCtor.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }
  }

  describe("existing named-argument constructor behavior is preserved") {
    it("still supports named arguments in constructor") {
      val result = shell.run(
        """
          |class Person {
          |public:
          |  var name: String
          |  var age: Int
          |  def this(name: String, age: Int) {
          |    this.name = name;
          |    this.age = age;
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Person(age = 30, name = "Alice");
          |    return p.name + ":" + p.age;
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Alice:30") == result)
    }

    it("still supports default values with named args in constructor") {
      val result = shell.run(
        """
          |class Config {
          |public:
          |  var host: String
          |  var port: Int
          |  def this(host: String, port: Int = 8080) {
          |    this.host = host;
          |    this.port = port;
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val c = new Config(host = "localhost");
          |    return c.host + ":" + c.port;
          |  }
          |}
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("localhost:8080") == result)
    }
  }
}
