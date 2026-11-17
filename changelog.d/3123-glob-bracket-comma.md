### Fixed

- **`Files::glob("d", "{[a,b].on,*.md}")` returns the matches again (#3123).**
  - Before: `PatternSyntaxException`, since a comma inside `[...]` split the brace group.
  - Now: a bracket class is kept whole, as the JDK matcher does; `[{,}]*` works too.
