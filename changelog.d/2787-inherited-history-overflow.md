### Fixed

- **An inherited `n = n + 1` that can overflow a subclass `history n >= old(n)` is reported (#2787).**
  - Before: it compiled cleanly, while the same method written in the subclass was E0112.
  - Now: W0021/E0112 with the overflow counterexample, naming the subclass and the superclass and offering the override.
