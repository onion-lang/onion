### Fixed

- **`"#{ (1)(2) }"` is a syntax error instead of printing `1` (#3166).**
  - Before: input left over after the first term inside `#{...}` was dropped without a word.
  - Now: it is a syntax error, as the same expression is outside a string.
