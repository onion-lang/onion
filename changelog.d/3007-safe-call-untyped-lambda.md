### Fixed

- **`xs?.map { x -> x * 2 }` compiles: a lambda passed through `?.` takes its parameter types from the method (#3007).**
  - Before: E0052 "lambda parameter x must specify a type", although the same call with `.` compiled. E0070's hint ("Use ?. to access it safely") led straight to it.
  - Now: it prints `[2, 4, 6]`, or `null` when `xs` is null. The result is nullable, as for any `?.` call.
