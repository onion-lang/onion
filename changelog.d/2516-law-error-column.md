### Fixed

- **A failing `law` or `example` in a record body is reported at the clause, not column 1.**
  - Before: `2:1` with the caret under the indentation.
  - Now: the caret sits on the `law` name (`2:7`), so an editor jumps to the clause.
