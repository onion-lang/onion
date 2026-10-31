### Fixed

- **`this(...)` or `super(...)` in a constructor body now says where delegation goes (#2466).**
  - Before: a bare syntax error at `(`, plus a second one at the closing `}`.
  - Now: a hint to write `def this(s: Int) : this(s, s) { }`, or to pass superclass arguments on the `extends` clause.
