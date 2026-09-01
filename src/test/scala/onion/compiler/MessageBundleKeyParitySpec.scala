package onion.compiler

import org.scalatest.diagrams.Diagrams
import org.scalatest.funspec.AnyFunSpec

import java.io.FileInputStream
import java.util.Properties

/**
 * Regression guard for key drift between `errorMessage.properties` (English) and
 * `errorMessage_ja.properties` (Japanese).
 *
 * `ResourceBundle.getBundle("errorMessage", Locale.JAPANESE)` chains the Japanese file to
 * the English one as its parent, so a key present only in English still *resolves* through
 * the Japanese bundle -- silently, in English, with no test failure. That makes a dropped
 * or renamed translation invisible to every existing bundle spec (`LawCheckMessageI18nSpec`,
 * the many `*HintI18nSpec`s), all of which only check that a key resolves in both bundles,
 * never that both files actually declare it. This reads the two `.properties` files
 * directly -- bypassing `ResourceBundle`'s fallback chain entirely -- so a key missing from
 * either side is caught instead of quietly falling back.
 */
class MessageBundleKeyParitySpec extends AnyFunSpec with Diagrams {

  private def rawKeys(path: String): Set[String] = {
    val props = new Properties()
    val in = new FileInputStream(path)
    try props.load(in) finally in.close()
    import scala.jdk.CollectionConverters._
    props.stringPropertyNames().asScala.toSet
  }

  private lazy val enKeys = rawKeys("src/main/resources/errorMessage.properties")
  private lazy val jaKeys = rawKeys("src/main/resources/errorMessage_ja.properties")

  it("declares every English key in the Japanese bundle") {
    assert(enKeys.nonEmpty, "no keys read from errorMessage.properties -- the scan has rotted")
    val missing = (enKeys -- jaKeys).toSeq.sorted
    assert(missing.isEmpty, s"errorMessage_ja.properties is missing key(s): ${missing.mkString(", ")}")
  }

  it("declares no Japanese-only key absent from the English bundle") {
    assert(jaKeys.nonEmpty, "no keys read from errorMessage_ja.properties -- the scan has rotted")
    val stale = (jaKeys -- enKeys).toSeq.sorted
    assert(stale.isEmpty, s"errorMessage_ja.properties declares key(s) not in errorMessage.properties: ${stale.mkString(", ")}")
  }
}
