package onion

import java.time.{LocalDateTime, ZoneId}
import org.scalatest.funspec.AnyFunSpec

/** `DateTime.diffDays` and `addDays` count calendar days, so a DST change does not shorten or lengthen a day. */
class DateTimeDstSpec extends AnyFunSpec {
  private val ny = ZoneId.of("America/New_York")
  private def at(y: Int, m: Int, d: Int, h: Int = 0): Long =
    LocalDateTime.of(y, m, d, h, 0).atZone(ny).toInstant.toEpochMilli

  describe("DateTime.diffDays") {
    it("counts the 23-hour spring-forward day as one day") {
      assert(DateTime.diffDays(at(2026, 3, 9), at(2026, 3, 8), ny) == 1)
    }
    it("counts the 25-hour fall-back day as one day") {
      assert(DateTime.diffDays(at(2026, 11, 2), at(2026, 11, 1), ny) == 1)
    }
    it("is negative when the first time is earlier, and truncates partial days") {
      assert(DateTime.diffDays(at(2026, 3, 8), at(2026, 3, 9), ny) == -1)
      assert(DateTime.diffDays(at(2026, 3, 9, 12), at(2026, 3, 8), ny) == 1)
    }
  }

  describe("DateTime.addDays") {
    it("lands on the next date across the 25-hour fall-back day (#2832)") {
      val tomorrow = DateTime.addDays(at(2026, 11, 1), 1, ny)
      assert(tomorrow == at(2026, 11, 2))
      assert(DateTime.diffDays(tomorrow, at(2026, 11, 1), ny) == 1)
    }
    it("keeps the wall-clock time across the 23-hour spring-forward day (#2832)") {
      assert(DateTime.addDays(at(2026, 3, 7, 9), 1, ny) == at(2026, 3, 8, 9))
    }
    it("steps backwards and over several days by the calendar") {
      assert(DateTime.addDays(at(2026, 11, 2), -1, ny) == at(2026, 11, 1))
      assert(DateTime.addDays(at(2026, 3, 1, 9), 14, ny) == at(2026, 3, 15, 9))
    }
  }
}
