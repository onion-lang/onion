package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression test for the maxBy/minBy type inference bug:
 * when the result of maxBy/minBy is assigned to a nullable variable
 * (`val r: T? = list.maxBy { ... }`), the compiler previously widened
 * T to T? and then rejected the receiver as `List[T?]` instead of
 * `List[T]`, violating the left-to-right inference rule (E0000).
 */
class MaxByNullableReturnSpec extends AbstractShellSpec {

  private def run(src: String): Shell.Result = shell.run(src, "MaxBy.on", Array())

  describe("maxBy with nullable result annotation") {

    it("maxBy on List[interface type] with explicit nullable return type compiles and returns the right element") {
      val src =
        """
          |interface Named {
          |  def name(): String
          |  def age(): Int
          |}
          |record Person(name: String, age: Int) conforms Named
          |def main(): void {
          |  val ps: List[Named] = [new Person("Alice", 30), new Person("Bob", 45), new Person("Carol", 25)]
          |  val oldest: Named? = ps.maxBy { p -> p?.age() ?: 0 }
          |  val youngest: Named? = ps.minBy { p -> p?.age() ?: 0 }
          |  IO::println(if oldest != null { oldest.name() } else { "none" })
          |  IO::println(if youngest != null { youngest.name() } else { "none" })
          |}
          |""".stripMargin
      assert(Shell.Success(null) == run(src))
    }

    it("maxBy on List[record type] with explicit nullable return type compiles correctly") {
      val src =
        """
          |record Item(label: String, score: Int)
          |def main(): void {
          |  val items: List[Item] = [new Item("A", 10), new Item("B", 30), new Item("C", 20)]
          |  val best: Item? = items.maxBy { it -> it?.score() ?: 0 }
          |  val worst: Item? = items.minBy { it -> it?.score() ?: 0 }
          |  IO::println(if best != null { best.label() } else { "none" })
          |  IO::println(if worst != null { worst.label() } else { "none" })
          |}
          |""".stripMargin
      assert(Shell.Success(null) == run(src))
    }

    it("minBy on List[interface type] assigned to nullable does not raise E0000") {
      val src =
        """
          |interface Metric { def value(): Double }
          |record Sensor(id: String, reading: Double) conforms Metric {
          |public:
          |  def value(): Double = reading()
          |}
          |def main(): void {
          |  val sensors: List[Metric] = [new Sensor("s1", 3.5), new Sensor("s2", 1.2), new Sensor("s3", 2.8)]
          |  val lowest: Metric? = sensors.minBy { s -> s?.value() ?: 0.0 }
          |  IO::println(if lowest != null { lowest.value() } else { -1.0 })
          |}
          |""".stripMargin
      assert(Shell.Success(null) == run(src))
    }
  }
}
