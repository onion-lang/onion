### Fixed

- **`Json::stringify(1..3)` writes `[1,2,3]` (#3010).**
  - Before: a range was written as the string `"Range(1..<4)"`.
  - Now: any `Iterable` is written as a JSON array.
