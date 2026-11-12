### Fixed

- **An interface `history` over method calls now gets W0024 at a method that writes a field.**
  - Before: `history x() >= old(x())` in an interface was accepted with no error or warning.
  - Now: each implementing method that writes a field gets W0024, "nothing checks the history".
