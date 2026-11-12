### Fixed

- **`x ?: 5` compiles when `x` is a `Long?` or `Double?` (#2727).**
  - Before: E0001, "operator ?: is not applicable for type Long?, Int".
  - Now: the `Int` fallback widens to the left's type, as in `val l: Long = 5`.
