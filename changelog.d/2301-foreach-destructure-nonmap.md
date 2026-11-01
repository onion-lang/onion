### Fixed

- **`foreach (x, y) in ps` over a List of records is one clear error, E0103 (#2301).**
  - Before: two errors naming `entrySet()` and `K, V`, which you never wrote.
  - Now: "`foreach (a, b)` walks the entries of a Map", with the fix `foreach p in ps { val (x, y) = p ... }`. Also for a call result or a literal (`in getPs()`, `in [p]`).
