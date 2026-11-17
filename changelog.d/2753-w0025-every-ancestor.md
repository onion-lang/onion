### Fixed

- **W0025 now sees an override two levels down, and one that renames the parameter.**
  - Before: a contract-free middle class, or `def f(x: Int)` over `def f(k: Int)`, hid the break.
  - Now: the override is checked against every ancestor it overrides. A `static` method inherits no contract, so it no longer gets W0025.
