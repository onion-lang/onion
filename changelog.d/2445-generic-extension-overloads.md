### Fixed

- **Generic overloads that differ only after a type parameter both work: `tw[T](x: T, y: Int)` and `tw[T](x: T, y: String)` (#2445).**
  - Before: in an `extension` block, E0084 "duplicated extension method"; on a class, a static or a top-level function, E0005 at the call that needed the second one.
  - Now: each call picks its overload, as in Java. Two overloads with the same parameters are still a duplicate, and two that erase to the same JVM method (`f[T](x: T, y: T)` beside `f[T](x: T, y: Object)`) are E0035.
