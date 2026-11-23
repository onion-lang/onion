### Fixed

- **`Text::wrap` no longer ends a line with `「` that is followed by a space.**
  - Before: `Text::wrap("あいう「 えお", 4)` gave `[あい, う「, えお]`.
  - Now: the bracket moves down with the next character, as the docs say.
