### Fixed

- **`copy(a = .., a = ..)` is an error, and a record's own reordered `copy` binds named arguments by name.**
  - Before: the first duplicate was dropped unrun; `copy(a = "A", b = "B")` on `def copy(b, a)` swapped the values.
  - Now: `E0044` for a repeated name; on `def copy(b, a)`, `a = "A"` binds the parameter named `a`. A partial `copy(a = "A")` there is `E0005` (missing `b`) instead of a swapped copy.
  - Unchanged: partial copy through an own `copy(a, b)` or `copy(nx, ny)`, even beside other `copy` overloads.
