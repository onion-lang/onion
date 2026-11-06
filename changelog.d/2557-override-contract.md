### Added

- **W0025: an override can break the contract it inherits.**
  - The effective contract is `pre_super or pre_sub` and `(old(pre_super) => post_super) and (old(pre_sub) => post_sub)`; the override body is checked against it.
  - A counterexample is a warning, not an error: it may rest on an integer wrap-around the program never reaches.
