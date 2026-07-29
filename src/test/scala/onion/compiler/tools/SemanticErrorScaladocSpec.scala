package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Drift guard for the Scaladoc header on [[onion.compiler.SemanticError]]: the header
 * states the code range as prose ("E0000-E00NN"), which does not update itself as new
 * codes are added. This pins the stated maximum to the true maximum declared code so a
 * future addition (without a header update) fails loudly instead of leaving stale docs.
 */
class SemanticErrorScaladocSpec extends AnyFunSpec {
  describe("SemanticError.scala's header comment") {
    it("states the true highest declared error code") {
      val path = java.nio.file.Path.of("src/main/scala/onion/compiler/SemanticError.scala")
      val text = java.nio.file.Files.readString(path)

      val objectStart = text.indexOf("object SemanticError")
      assert(objectStart >= 0, "could not find `object SemanticError` in the file")
      val header = text.substring(0, objectStart)
      val body = text.substring(objectStart)

      val declaredCodes = """extends SemanticError\((\d+)\)""".r
        .findAllMatchIn(body).map(_.group(1).toInt).toList
      assert(declaredCodes.nonEmpty, "no `case object ... extends SemanticError(N)` declarations found")
      val maxCode = declaredCodes.max

      val stated = """E0000-E(\d{4})""".r.findFirstMatchIn(header)
        .getOrElse(fail("header no longer states an \"E0000-Ennnn\" range — update this guard too"))
        .group(1).toInt

      assert(stated == maxCode,
        f"header says E0000-E$stated%04d but the highest declared code is E$maxCode%04d — update the Scaladoc header on SemanticError")
    }
  }
}
