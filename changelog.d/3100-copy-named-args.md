### Fixed

- **`copy(a = .., a = ..)` is an error, and a record's own `copy` binds named arguments by name.**
  - Before: the first duplicate was dropped unrun; `copy(a = "A", b = "B")` on `def copy(b, a)` swapped the values.
  - Now: `E0044` for a repeated name; `a = "A"` binds the parameter named `a`.
