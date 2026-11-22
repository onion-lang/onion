### Fixed

- **A subclass can assign an inherited `protected` static var by its bare name (#3175).**
  - Before: `K = 6` and `K += 1` were E0002 "local variable K is not found"; the read of `K` worked.
  - Now: with no top-level `var K`, the write reaches the inherited field. A top-level `var K` still takes the write, as before.
