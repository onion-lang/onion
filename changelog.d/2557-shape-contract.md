### Added

- **A `Shape` that does not round-trip a value it says it can print is E0114.**
  - The contract is `parse(print(v)) == Ok(v)` for each value `canPrint()` and `printable(v)` admit; the build samples the value type.
  - `Shape` gains `printable(v)` (default: every value). `run/FixedWidthDemo.on` states its domain with it.
  - A value type that cannot be sampled is the warning W0022, never a silent pass.
