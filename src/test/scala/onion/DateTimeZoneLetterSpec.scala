package onion

import java.time.{Instant, ZoneId}
import org.scalatest.funspec.AnyFunSpec

/** `DateTime::format` accepts zone and offset pattern letters (#2834). */
class DateTimeZoneLetterSpec extends AnyFunSpec {
  private val t = DateTime.of(2026, 11, 11, 9, 30, 0)
  private val zone = ZoneId.systemDefault()
  private def offset(letters: String): String =
    java.time.format.DateTimeFormatter.ofPattern(letters, java.util.Locale.ENGLISH)
      .format(Instant.ofEpochMilli(t).atZone(zone))

  describe("DateTime.format with a zone letter") {
    it("writes a zone name and an offset in the local zone") {
      assert(DateTime.format(t, "yyyy-MM-dd HH:mm z") == "2026-11-11 09:30 " + offset("z"))
      assert(DateTime.format(t, "yyyy-MM-dd'T'HH:mmXXX") == "2026-11-11T09:30" + offset("XXX"))
      assert(DateTime.format(t, "VV") == zone.getId)
    }
    it("leaves the text of every other letter unchanged") {
      assert(DateTime.format(t, "yyyy-MM-dd HH:mm") == "2026-11-11 09:30")
    }
    it("names the pattern when it is invalid") {
      val e = intercept[IllegalArgumentException](DateTime.format(t, "yyyy-bb-qq"))
      assert(e.getMessage.contains("yyyy-bb-qq"))
    }
    it("nowString accepts a zone letter") {
      assert(DateTime.nowString("yyyy z").endsWith(offset("z")))
    }
  }
}
