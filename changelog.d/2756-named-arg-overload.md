### Fixed

- **`c.f(name = "s")` compiles when `f(name: Long)` and `f(name: String)` both exist (#2756).**
  - Before: E0006, because a named argument chose an overload by parameter name only.
  - Now: the argument's type picks `f(name: String)`, as for the positional call `c.f("s")`. Two overloads that fit equally well are still E0006.
