### Fixed

- **A bare `sum(xs)` or `sha256(s)` now says to write `Stats::sum(...)` or `Hash::sha256(...)`.**
  - Before: E0005 "function not found", with no pointer.
  - Now: E0005 plus a hint naming the class (`Stats`, `Hash`, `Codec`, `Format`).
