package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

import java.util.Locale

/** `Timing::formatNanos`/`formatMillis` print `1.50s` whatever the JVM's default locale is. */
class TimingLocaleSpec extends AnyFunSpec {

  private def underGerman[A](body: => A): A = {
    val saved = Locale.getDefault
    Locale.setDefault(Locale.GERMANY)
    try body finally Locale.setDefault(saved)
  }

  it("formatNanos uses a '.' decimal separator under a comma-decimal locale") {
    underGerman {
      assert(onion.Timing.formatNanos(45670L) == "45.67us")
      assert(onion.Timing.formatNanos(12340000L) == "12.34ms")
      assert(onion.Timing.formatNanos(1500000000L) == "1.50s")
    }
  }

  it("formatMillis uses a '.' decimal separator under a comma-decimal locale") {
    underGerman {
      assert(onion.Timing.formatMillis(2500L) == "2.50s")
    }
  }
}
