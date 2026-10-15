package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

import java.util.Locale

/** `Strings::upper`/`lower`/`containsIgnoreCase` must not depend on the JVM's default locale. */
class StringsCaseLocaleSpec extends AnyFunSpec {

  private def underTurkish[A](body: => A): A = {
    val saved = Locale.getDefault
    Locale.setDefault(new Locale("tr", "TR"))
    try body finally Locale.setDefault(saved)
  }

  it("upper() gives ASCII-stable casing under a Turkish locale") {
    underTurkish {
      assert(onion.Strings.upper("i") == "I")
    }
  }

  it("lower() gives ASCII-stable casing under a Turkish locale") {
    underTurkish {
      assert(onion.Strings.lower("I") == "i")
    }
  }

  it("containsIgnoreCase() still matches under a Turkish locale") {
    underTurkish {
      assert(onion.Strings.containsIgnoreCase("I", "i"))
    }
  }
}
