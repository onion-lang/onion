### Fixed

- **Two error types in one `do[Result]` block get an error that names the `Result` types (#2670).**
  - Before: `type Function1[Int, Result[Int, String]] is expected ...`, with the caret on the first bind.
  - Now: `type Result[Int, String] is expected, but type Result[Int, Int] is used`, with the caret on the clause that returned the other error type and a hint to convert it with `.mapError`.
