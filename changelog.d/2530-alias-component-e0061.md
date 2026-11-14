### Fixed

- **A json-shape record component typed with an alias from another file now names the alias in E0061 (#2530).**
  - Before: `record T(n: Name)` with `type Name = JS` in another file said only `has type java.lang.String`, a type the message also lists as supported.
  - Now: `has type Name (= java.lang.String)`.
