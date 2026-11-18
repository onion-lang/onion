### Fixed

- **`Strings::truncate` never returns half an emoji when the cut lands inside the suffix.**
  - Before: `Strings::truncate("abcdef", 1, "😀")` returned a lone surrogate.
  - Now: it returns `""`.
