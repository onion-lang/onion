### Fixed

- **A subclass's `history` now checks the methods it inherits without overriding (#2603).**
  - Before: `class Up extends Base { history n >= old(n) }` compiled cleanly while an inherited `Base.dec` lowered `n`.
  - Now: E0109 at that write, with the fix (override `dec` in `Up`, or declare the history in `Base`). A write the syntax does not settle is W0024.
