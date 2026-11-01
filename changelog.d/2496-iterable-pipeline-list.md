### Breaking

- **`map`/`filter` on a range, `map.values()` or another `Iterable` that is not a `List` or `Set` are typed `List[T]` (#2496).**
  - Before: `Iterable[T]`, so `(1..10).filter { ... }.size`, `[i]` and `.take(n)` were E0004/E0005 unless you added `.toList()`.
  - Now: `List[T]`, the list they already returned.
  - Breaks: `var xs = (1..3).map { ... }` is a `List[Int]`, so a later `xs = someSet` is E0000. The error says to declare `var xs: Iterable[Int] = ...`.
  - Changes meaning: an overload or extension on both `Iterable` and `List` now picks the `List` one.
  - Recompile: a class compiled against an older `onion.jar` that calls these methods throws `NoSuchMethodError`.
  - See `docs/migration/pre-1.0.md`.
