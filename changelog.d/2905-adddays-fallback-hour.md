### Fixed

- **`DateTime::addDays(t, 0)` returns `t` in the repeated hour of a fall-back night (#2905).**
  - Before: the second 01:30 moved to the first 01:30, one hour earlier. `addMonths` and `addYears` did the same.
  - Now: the offset is kept when the new wall time is still valid with it.
