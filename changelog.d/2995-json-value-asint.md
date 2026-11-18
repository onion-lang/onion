### Fixed

- **`Json.Value.asInt()` and `asLong()` no longer truncate or wrap (#2995).**
  - Before: `3.7` gave `3`, and `10000000000` as an `Int` gave `1410065408`.
  - Now: both give `0`, the same "no value" answer as a missing key. `Json::getInt` already returned `null` for these.
