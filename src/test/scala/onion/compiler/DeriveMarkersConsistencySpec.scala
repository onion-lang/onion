package onion.compiler

import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * `derive!`'s marker whitelist was written out twice in `TypingOutlinePass` (the
 * `hasData` guard and the unknown-marker check) and a third time as literal text in
 * `errorMessage.properties`/`errorMessage_ja.properties` ("Supported markers: Json,
 * Yaml.") -- the exact drift risk `ShapeFormats` was introduced to close for
 * `shape name = <format>` (see its own doc comment). `DeriveMarkers` is the same fix
 * for `derive!`: one list, and the diagnostic interpolates its `supportedNames`
 * rather than repeating it.
 */
class DeriveMarkersConsistencySpec extends AnyFunSpec {

  it("lists Json and Yaml") {
    assert(DeriveMarkers.all == List("Json", "Yaml"))
  }

  it("reports every marker in DeriveMarkers.all when an unknown marker is used") {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val src =
      """
        |record U(a: String) derive!(Bogus)
        |class Main {
        |public:
        |  static def main(args: String[]): void {}
        |}
        |""".stripMargin
    val msgs = new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
    DeriveMarkers.all.foreach { marker =>
      assert(msgs.contains(marker), s"expected the E0063 message to name marker `$marker`, got: $msgs")
    }
  }
}
