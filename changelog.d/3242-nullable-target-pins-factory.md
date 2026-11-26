### Fixed

- **A nullable target type now pins a generic factory's type arguments (#3242).**
  - Before: `val b: Option[String]? = Option::none()` was E0000 ("`Option[Object]` is used"). A lambda with an early `return null` into such a target got a false W0012.
  - Now: both compile with no warning, and a lambda ending in an `if`/`select`/`try` is checked against a nullable target too.
