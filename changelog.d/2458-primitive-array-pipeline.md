### Fixed

- **`intArray.map { ... }` gets one clear error with a fix that compiles (#2458).**
  - Before: E0000 "type Int[] is expected, but type Int[] is used".
  - Now: E0005 with a hint: `Arrays::stream(xs).boxed().toList()` for `Int[]`/`Long[]`/`Double[]`, a `foreach` copy into a List for the other primitive arrays.
