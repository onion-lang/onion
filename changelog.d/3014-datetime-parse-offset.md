### Fixed

- **`DateTime::parse(text, pattern)` honours an offset or zone in the text (#3014).**
  - Before: `parse("2026-11-11T09:30Z", "yyyy-MM-dd'T'HH:mmXXX")` read the time as local, so it was off by the local offset.
  - Now: the `Z`/`+09:00` in the text decides the instant, as `parse(text)` already did.
