### Fixed

- **`Future::delay(deadline - Timing::millis())` completes at once when the deadline has passed (#2453).**
  - Before: a negative delay failed with `IllegalArgumentException: timeout value is negative`.
  - Now: a zero or negative delay gives a Future that has already completed, as `Timing::sleep` returns at once.
