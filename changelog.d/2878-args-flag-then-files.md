### Fixed

- **`Args::parse` no longer drops the first file in `--verbose a.txt b.txt` (#2878).**
  - Before: `flag("verbose")` was true but `positional()` was `[b.txt]`.
  - Now: if the script asks `flag("verbose")` and not `option("verbose")`, `a.txt` stays in `positional()`.
  - The guide no longer says `-v` satisfies `flag("verbose")`; it is `flag("v")`.
