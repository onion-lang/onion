### Fixed

- **`record R(a: Int, b: Int = 2)` can be built as `new R(1)` or `new R(a = 1)`.**
  - Before: the default parsed, then every construction that omitted `b` was E0021.
  - Now: `b` is filled with `2`, as a constructor default would be. A data-enum parameter default is still unsupported (#2948).
