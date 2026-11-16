### Fixed

- **`val d: Int = xs[0]` no longer warns W0015 when `xs` is a `List[Int]` (#3071).**
  - Before: `xs[0]`, `opt.get()` and `res.getOrElse(0)` on `List[Int]`, `Option[Int]` and `Result[Int, E]` warned "NullPointerException at runtime".
  - Now: no warning, since the type argument `Int` is not null. A `Map` lookup and `Json::getInt` still warn.
