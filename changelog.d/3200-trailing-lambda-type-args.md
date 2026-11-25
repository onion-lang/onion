### Fixed

- **A trailing lambda can follow explicit type arguments: `Comparator::nullsFirst[String] { a, b -> a.compareTo(b) }` (#3200).**
  - Before: a syntax error at the `{`; only `nullsFirst[String]((a, b) -> ...)` worked.
  - Now: it is a call with the lambda as its argument, for `Type::m[T]`, `f[T]`, `obj.m[T]`, `obj?.m[T]` and `super.m[T]`.
