### Fixed

- **A `shape` named like a record component is rejected at the `shape` clause (#2531).**
  - Before: `record Pt(x: Int) { shape x = re"..." }` compiled, then `Pt::x()` was E0005 listing `x(), x()`.
  - Now: the record is an error that names the clash and says to rename one.
