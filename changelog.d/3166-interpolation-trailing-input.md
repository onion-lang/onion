### Fixed

- **`"#{ (1)(2) }"` is a syntax error instead of printing `1` (#3166).**
  - Before: a call `(...)` left over after the term inside `#{...}` was dropped without a word.
  - Now: a leftover call is a syntax error, as outside a string. Other leftovers (`#{ 1 2 }`, `#{x,5}`) are still ignored (#3207).
