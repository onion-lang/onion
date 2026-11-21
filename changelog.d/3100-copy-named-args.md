### Fixed

- **`copy(a = .., a = ..)` is an error, and a record's own `copy` binds named arguments by name.**
  - Before: the first duplicate was dropped unrun; `copy(a = "A", b = "B")` on `def copy(b, a)` swapped the values.
  - Now: `E0044` for a repeated name; `a = "A"` binds the parameter named `a`, also when another `copy` overload takes the components in order.
  - A record's own `copy` uses its own parameter names: `copy(y = 5)` on `def copy(nx, ny)`, which compiled before, is now `E0043`.
