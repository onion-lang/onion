### Fixed

- **`intArray.map { ... }` gets one clear error with a fix that compiles (#2458).**
  - Before: E0000 "type Int[] is expected, but type Int[] is used".
  - Now: E0005, plus a hint for `Int[]`/`Long[]`/`Double[]`: `java.util.Arrays::stream(xs).boxed().toList().map(...)`.
