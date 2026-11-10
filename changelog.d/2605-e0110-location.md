### Fixed

- **E0110 points at the field an `invariant` or `history` names (#2605).**
  - Before: `C.on:: [E0110] ...`, with no line and no snippet.
  - Now: `C.on:4:13: [E0110] ...`, underlining `xs` in `invariant xs.size >= 0`.
