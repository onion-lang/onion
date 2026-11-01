### Fixed

- **An `as` cast to or from an array that can never succeed is a compile error, also when one side is nullable (#2309).**
  - Before: `(xs as String[])` with `xs: List[String]?`, or `(ys as Integer[])` with `ys: String[]`, compiled and always failed at run time with `invalid type cast`.
  - Now: E0000. The List/array hint names your variable and is null-safe when needed (`xs!!.toArray(new String[0])`). Casts that can work (`Int[]? as Int[]`, `Object[] as String[]`) still compile.
