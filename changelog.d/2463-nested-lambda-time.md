### Fixed

- **Nested lambdas passed to an overloaded extension method compile in linear time.**
  - Before: depth 20 did not finish in 60 s; the time doubled every two levels.
  - Now: depth 20 compiles in about a second.
