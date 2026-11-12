### Fixed

- **`DateTime::addDays(d, 1)` is the next date, even across a daylight-saving change (#2832).**
  - Before: it added 24 hours. In New York, "tomorrow" from 2026-11-01 was still 11-01, and 09:00 became 10:00 after spring-forward.
  - Now: it moves the calendar date and keeps the local time, as `addMonths` does. `addHours` still adds elapsed time.
