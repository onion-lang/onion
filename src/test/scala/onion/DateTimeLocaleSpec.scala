package onion

import java.util.Locale
import org.scalatest.funspec.AnyFunSpec

/** Pattern-based DateTime formatting and parsing do not follow the JVM default locale (#2134). */
class DateTimeLocaleSpec extends AnyFunSpec {
  private def underLocale[A](l: Locale)(body: => A): A = {
    val saved = Locale.getDefault
    Locale.setDefault(l)
    try body finally Locale.setDefault(saved)
  }

  describe("DateTime with a text pattern") {
    val t = DateTime.of(2026, 3, 8, 12, 0, 0)
    for (l <- Seq(Locale.JAPAN, Locale.GERMANY, Locale.FRANCE)) {
      it(s"formats in English under $l") {
        assert(underLocale(l)(DateTime.format(t, "EEE MMM d")) == "Sun Mar 8")
      }
      it(s"parses English month names under $l") {
        val parsed = underLocale(l)(DateTime.parse("08 Mar 2026 12:00:00", "dd MMM yyyy HH:mm:ss"))
        assert(parsed == t)
      }
    }
  }
}
