### Fixed

- **`DateTime::parse("2026-11-11Z", "yyyy-MM-ddXXX")` keeps the offset (#3059).**
  - Before: a pattern with a date and an offset but no time gave local midnight.
  - Now: midnight at the offset written in the text, as the javadoc says.
