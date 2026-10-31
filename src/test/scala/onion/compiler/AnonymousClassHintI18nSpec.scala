package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Java's anonymous class (`new Runnable { ... }`, `new Comparator[String]() { ... }`) is a
 * syntax error in Onion. The hint names the two ways out that compile today: a lambda for a
 * functional interface, and a named class with its methods under `public:` (#2514).
 */
class AnonymousClassHintI18nSpec extends AnyFunSpec with Diagrams {
  private val key = "error.parsing.hint.anonymous_class"
  private val en = MessageBundles.english
  private val ja = MessageBundles.japanese

  private def compileErrors(src: String): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
  }

  it("resolves in the English bundle") {
    assert(en.getString(key).contains("Hint:"))
  }

  it("resolves in the Japanese bundle with actual Japanese text") {
    val text = ja.getString(key)
    assert(text.contains("ヒント"), s"expected a Japanese hint, got: $text")
    assert(!text.contains("Hint:"), s"hint leaked untranslated English, got: $text")
    assert(text != en.getString(key))
  }

  // The example code in the hint is literal and identical in both bundles, so these
  // assertions hold whatever the JVM's default locale is.
  private val cases = Seq(
    "a bare `new T {` at a val" ->
      ("""val r = new Runnable {
         |  def run(): void { println("hi") }
         |}
         |""".stripMargin, "Runnable", "RunnableImpl"),
    "Java's `new T() {` with parentheses" ->
      ("""val r = new Runnable() {
         |  public void run() { println("hi") }
         |};
         |""".stripMargin, "Runnable", "RunnableImpl"),
    "a generic type after `return` in a method" ->
      ("""class C {
         |public:
         |  def it(): Iterator[Int] {
         |    return new Iterator[Int] {
         |      def hasNext(): Boolean = false
         |      def next(): Int = 0
         |    }
         |  }
         |}
         |""".stripMargin, "Iterator[Int]", "IteratorImpl"),
    "a qualified generic type with constructor parentheses" ->
      ("""val c = new java.util.Comparator[String]() { def compare(a: String, b: String): Int = 0 }
         |""".stripMargin, "java.util.Comparator[String]", "ComparatorImpl"),
    "a call argument" ->
      ("""val t = new Thread(new Runnable { def run(): void { println("x") } })
         |""".stripMargin, "Runnable", "RunnableImpl")
  )

  cases.foreach { case (label, (src, typeName, implName)) =>
    it(s"fires for $label") {
      val msgs = compileErrors(src)
      assert(msgs.contains(s"val f: $typeName = () -> ..."), s"expected the lambda example, got: $msgs")
      assert(msgs.contains(s"class $implName conforms $typeName '{' public:") || msgs.contains(s"class $implName conforms $typeName { public:"),
        s"expected the named-class example, got: $msgs")
    }
  }

  it("does not fire for a constructor call followed by a block statement") {
    val msgs = compileErrors(
      """val xs = new java.util.ArrayList[String]()
        |if xs.isEmpty() { println("empty") }
        |{ println(1) } }
        |""".stripMargin)
    assert(!msgs.contains("RunnableImpl") && !msgs.contains("ArrayListImpl"), s"unexpected hint: $msgs")
  }
}
