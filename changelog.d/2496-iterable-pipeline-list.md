### Changed

- **`(1..10).filter { ... }` is a `List[Int]`, so `.size`, `[i]` and `.take(n)` work on it (#2496).**
  - Before: `map`/`filter` on a range or `map.values()` were typed `Iterable[T]`: E0004/E0005 unless you added `.toList()`.
  - Now: they are typed `List[T]`, the list they already returned.
