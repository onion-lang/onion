### Fixed

- **`DateTime::format(t, "... z")` and `nowString("...XXX")` work.**
  - Before: a raw `DateTimeException` for any zone or offset letter (`z`, `X`, `VV`).
  - Now: the time is shown in the local zone, e.g. `2026-11-11 09:30 JST`. An invalid pattern names itself.
