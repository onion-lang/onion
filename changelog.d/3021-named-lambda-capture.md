### Fixed

- **A lambda passed as a named argument keeps its writes to a captured `var` (#3021).**
  - Before: `ap(f = (x: Int) -> { b += x; return b })` compiled, but `b` stayed `0`; the positional form worked.
  - Now: both forms share the variable, so `b` is `10`.
