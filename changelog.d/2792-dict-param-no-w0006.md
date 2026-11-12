### Fixed

- **A `[T: Trait]` function that forwards its constraint no longer warns W0006 (#2792).**
  - Before: `def total[T: Numeric](xs: List[T]): T = sum(xs)` warned about `dict$Numeric$T`, a parameter you never wrote; `--warn error` failed the build.
  - Now: no warning for the compiler's own constraint parameter. Your own unused parameters still warn.
