### Fixed

- **E0113 no longer blames a correct predicate.**
  - Before: a sample whose `toString` throws gave "did not hold" (English even in ja); an unsampleable parameter type read "did not hold" too.
  - Now: the first reports nothing; the second says the predicate was not run because the type cannot be sampled.
