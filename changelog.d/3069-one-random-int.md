### Breaking

- **`OnionMath::randomInt` is removed; `Rand::nextInt(min, max)` is the one random `Int` in a range (#3069).**
  - Before: `OnionMath::randomInt(1, 6)` could return 6, while `Rand::nextInt(1, 6)` never did.
  - Now: `OnionMath::randomInt` is E0005 with a hint. `onion migrate` rewrites it to `Rand::nextInt(1, 7)`.
