### Fixed

- **`val y: Comparable[String] = b` with `b: JInteger` is a compile error (#2649).**
  - Before: it compiled, then threw `ClassCastException` at the first `compareTo`. So did `b as Comparable[String]`, `"s" as Comparable[JInteger]`, `[b]` or `[5]` as a `List[Comparable[String]]`, a `Comparable[String]?` target, `o ?: 5`, and `new D(b)` / `new R(b)` / `extends Base(b)` into a `Comparable[String]` parameter.
  - Now: E0000 (E0001 for `?:`, E0005 for a method argument, E0021 for a constructor argument). It applies only when the type arguments can share no value (`String` vs `Integer`; an interface such as `CharSequence` vs the final `Integer`). `Comparable[JInteger]`, `Object`, `Number`, and related arguments (`new Properties()` as `Map[String, String]`) still compile.
