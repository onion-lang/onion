### Fixed

- **Nested lambdas passed to an overloaded extension method compile in linear time.**
  - Before: depth 20 took 19 s, and each level doubled the time every two levels.
  - Now: depth 20 compiles in about a second.
