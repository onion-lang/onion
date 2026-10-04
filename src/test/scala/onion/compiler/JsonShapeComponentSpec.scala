package onion.compiler

import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Which record components a `shape name = json` clause accepts (lists, nested json
 * records, nullables) and which it still rejects with E0061, naming the clause that
 * cannot read them. Every other shape format keeps to the scalars.
 *
 * Assertions are on the error code and on clause text spliced verbatim into the message,
 * so they hold in both locales.
 */
class JsonShapeComponentSpec extends AnyFunSpec {

  private def errors(src: String): Seq[CompileError] =
    new OnionCompiler(new CompilerConfig(List("."), null, "UTF-8", "", 10))
      .compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs
      case _                                => Seq.empty
    }

  private def e0061(src: String): Seq[String] = errors(src).filter(_.errorCode.contains("E0061")).map(_.message)

  private def program(components: String, clauses: String, prelude: String = ""): String =
    s"""$prelude
       |record R($components) {
       |$clauses
       |}
       |println("x")
       |""".stripMargin

  private val json = "  shape doc = json"

  private val nestedRecord =
    """record Action(owner: String, task: String) {
      |  shape doc = json
      |}""".stripMargin

  describe("a json shape accepts") {
    val accepted = Seq(
      "a scalar list"             -> ("a: List[String], b: List[Int], c: List[Boolean]", ""),
      "a nested json record"      -> ("a: Action", nestedRecord),
      "a list of json records"    -> ("a: List[Action]", nestedRecord),
      "nullable scalars"          -> ("a: String?, b: Int?, c: Double?", ""),
      "a nullable list"           -> ("a: List[String]?", ""),
      "a nullable nested record"  -> ("a: Action?", nestedRecord),
      "a record declared later"   -> ("a: Later", "record Later(x: Int) {\n  shape doc = json\n}"),
      "a record containing itself"-> ("name: String, kids: List[R]", "")
    )
    accepted.foreach { case (what, (components, prelude)) =>
      it(what) {
        val errs = errors(program(components, json, prelude))
        assert(errs.isEmpty, errs.map(e => s"${e.errorCode}: ${e.message}").mkString("\n"))
      }
    }
  }

  describe("a json shape still rejects with E0061") {
    val rejected = Seq(
      "a list of nullable elements"       -> ("a: List[String?]", ""),
      "a map"                             -> ("a: Map[String, String]", "import { java.util.Map }"),
      "a record without a json shape"     -> ("a: Inner", "record Inner(x: Int)"),
      "a record with only a regex shape"  -> ("a: Inner", "record Inner(x: Int) {\n  shape line = re\"(\\d+)\"\n}"),
      "a list of lists"                   -> ("a: List[List[String]]", ""),
      "an array"                          -> ("a: String[]", ""),
      "a type alias of a list"            -> ("a: Tags", "type Tags = List[String]")
    )
    rejected.foreach { case (what, (components, prelude)) =>
      it(what) {
        val msgs = e0061(program(components, json, prelude))
        assert(msgs.nonEmpty, s"expected E0061 for $components")
        assert(msgs.forall(_.contains("shape doc = json")), msgs.mkString("\n"))
      }
    }
  }

  describe("every other shape keeps the scalar restriction") {
    it("a regex shape beside a json shape rejects a list, naming the regex clause") {
      val msgs = e0061(program("a: List[String]", json + "\n  shape line = re\"(.*)\""))
      assert(msgs.size == 1, msgs.mkString("\n"))
      assert(msgs.head.contains("shape line = re\"...\""), msgs.head)
    }

    it("a yaml shape rejects a list") {
      val msgs = e0061(program("a: List[String]", "  shape y = yaml"))
      assert(msgs.size == 1 && msgs.head.contains("shape y = yaml"), msgs.mkString("\n"))
    }

    it("a yaml shape accepts a nullable scalar (#1969)") {
      val errs = errors(program("a: String?", "  shape y = yaml"))
      assert(errs.isEmpty, errs.map(e => s"${e.errorCode}: ${e.message}").mkString("\n"))
    }

    it("a config shape rejects a nullable scalar") {
      val msgs = e0061(program("a: String?", "  shape c = config"))
      assert(msgs.size == 1 && msgs.head.contains("shape c = config"), msgs.mkString("\n"))
    }

    it("a regex shape rejects a nested json record") {
      val msgs = e0061(program("a: Action", "  shape line = re\"(.*)\"", nestedRecord))
      assert(msgs.size == 1 && msgs.head.contains("shape line = re\"...\""), msgs.mkString("\n"))
    }

    it("`from re\"...\"` rejects a list") {
      val msgs = e0061(
        """record R(a: List[String]) from re"(.*)"
          |println("x")
          |""".stripMargin)
      assert(msgs.nonEmpty && msgs.head.contains("from re\"...\""), msgs.mkString("\n"))
    }
  }

  it("lists the extended component types for a json clause, in both bundles, with no unfilled placeholder") {
    for (bundle <- Seq(MessageBundles.english, MessageBundles.japanese)) {
      val text = java.text.MessageFormat.format(bundle.getString("error.semantic.jsonShapeComponentTypes"), "String, Int")
      assert(!text.matches("(?s).*\\{\\d+\\}.*"), s"unfilled placeholder: $text")
      assert(text.contains("List[S]") && text.contains("List[R]") && text.contains("T?"), text)
    }
  }
}
