### Fixed

- **`requires null` gets the contract-clause hint, not cast advice.**
  - Before: E0000 ending in "Consider a cast or check return type".
  - Now: E0000 with the hint that `requires` takes a Boolean condition (`requires n > 0`). The same for `ensures`, `invariant` and `history`, in en and ja.
