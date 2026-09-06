package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Guards against `DateTime` and `Http`'s class-level Javadoc still claiming
 * "All methods are static and can be used without import" after #360 narrowed
 * the default static import set to pure classes only. Both classes are
 * effectful and were deliberately dropped from
 * `src/main/resources/onion/default-static-imports.txt`; their own doc
 * comment must not tell readers the opposite of what
 * `DefaultStaticImportSpec` ("no longer resolves bare calls into ... Http,
 * DateTime") actually enforces.
 */
class EffectfulStdlibJavadocSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  describe("class Javadoc for stdlib classes narrowed out of the default static imports (#360)") {
    it("does not claim DateTime methods can be used without import") {
      val src = read("src/main/java/onion/DateTime.java")
      assert(
        !src.contains("can be used without import"),
        "onion.DateTime is not in default-static-imports.txt; its class doc must not claim bare calls work"
      )
      assert(src.contains("DateTime::"), "DateTime's class doc should show the qualified call form")
    }

    it("does not claim Http methods can be used without import") {
      val src = read("src/main/java/onion/Http.java")
      assert(
        !src.contains("can be used without import"),
        "onion.Http is not in default-static-imports.txt; its class doc must not claim bare calls work"
      )
      assert(src.contains("Http::"), "Http's class doc should show the qualified call form")
    }

    it("still lets Regex's doc claim it, since Regex genuinely is a default import") {
      val src = read("src/main/java/onion/Regex.java")
      assert(src.contains("can be used without import"))
    }
  }
}
