### Fixed

- **An override that calls `super.dec()` no longer slips past a subclass `history` (#2782).**
  - Before: `override def dec(): void { super.dec() }` compiled, and `Base.dec` lowered `n` under `history n >= old(n)`.
  - Now: E0109 at the superclass write, naming the `super` call to change (W0024 when the write is not settled by syntax).
