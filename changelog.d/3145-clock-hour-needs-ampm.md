### Fixed

- **`DateTime::parse(s, "…hh:mm…")` without an `a` field is an error, not midnight.**
  - Before: `"2026-11-11T09:30Z"` with `hh` silently dropped the time and returned midnight.
  - Now: `IllegalArgumentException` saying a 12-hour field needs an am/pm field `a`.
