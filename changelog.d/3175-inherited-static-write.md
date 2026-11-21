### Fixed

- **A subclass can assign an inherited `protected` static var by its bare name (#3175).**
  - Before: `K = 6` and `K += 1` were E0002 "local variable K is not found"; the read of `K` worked.
  - Now: the write reaches the same field as the read. An inherited `private` static is E0014, as for the read.
