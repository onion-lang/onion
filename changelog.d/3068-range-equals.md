### Fixed

- **`(1..3) == (1..3)` is `true` (#3068).**
  - Before: `false`, because `Range` had no `equals`/`hashCode`.
  - Now: ranges that produce the same values are equal and hash alike.
