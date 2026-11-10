### Fixed

- **`val y: Comparable[String] = b` with `b: JInteger` is a compile error (#2649).**
  - Before: it compiled, then threw `ClassCastException` at the first `compareTo`. So did `b as Comparable[String]`, `"s" as Comparable[JInteger]`, `[b]` or `[5]` as a `List[Comparable[String]]`, `o ?: 5`, and a `Comparable[String]?` target.
  - Now: E0000. A class whose own view is `Comparable[Integer]` fits only that view, as a primitive `5` already did (#2594). `Comparable[JInteger]`, `Object` and `Number` still take it.
