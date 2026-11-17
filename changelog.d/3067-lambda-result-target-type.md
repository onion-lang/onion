### Fixed

- **`val q: Option[Int] = o.fold(() -> Option::none(), (s) -> Option::some(s.length()))` compiles (#3067).**
  - Before: E0000, `Option[Object]` expected. The first lambda fixed the result type before the target type was read.
  - Now: a lambda whose result is a generic call or a collection literal takes its type from the written target type.
