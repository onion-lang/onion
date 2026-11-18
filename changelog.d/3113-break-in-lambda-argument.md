### Fixed

- **`break`/`continue` in a lambda passed to a call is now E0048/E0049 (#3113).**
  - Before: `call { -> break }` compiled; the loop ran on and the lambda returned `null`.
  - Now: the same error as `val f: () -> String = () -> { break }`. A loop inside the lambda can still `break` out of itself.
