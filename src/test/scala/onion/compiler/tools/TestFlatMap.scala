package onion.compiler.tools

import onion.tools.Shell

object TestFlatMap {
  def main(args: Array[String]): Unit = {
    val shell = Shell(Seq())

    // まずmapをテスト（これは動作する）
    val mapCode = """
      |import { onion.Future; }
      |class Test {
      |public:
      |  static def main(args: String[]): String {
      |    val f = Future::successful[String]("hello");
      |    val f2 = f.map((s: String) -> { s + " world" });
      |    return f2.await()
      |  }
      |}
    """.stripMargin
    println("=== Testing map ===")
    val mapResult = shell.run(mapCode, "Test", Array())
    println(s"map Result: $mapResult")

    // flatMapのテスト
    val flatMapCode = """
      |import { onion.Future; }
      |class Test {
      |public:
      |  static def main(args: String[]): String {
      |    val f1 = Future::successful[String]("hello");
      |    val f2 = f1.flatMap((s: String) -> { Future::successful[String](s + "!") });
      |    return f2.await()
      |  }
      |}
    """.stripMargin
    println("=== Testing flatMap ===")
    val flatMapResult = shell.run(flatMapCode, "Test", Array())
    println(s"flatMap Result: $flatMapResult")

    // filterのテスト
    val filterCode = """
      |import { onion.Future; }
      |class Test {
      |public:
      |  static def main(args: String[]): String {
      |    val f = Future::successful[String]("hello");
      |    val f2 = f.filter((s: String) -> { s.length() > 3 });
      |    return f2.await()
      |  }
      |}
    """.stripMargin
    println("=== Testing filter ===")
    val filterResult = shell.run(filterCode, "Test", Array())
    println(s"filter Result: $filterResult")
  }
}
