### Fixed

- **An erasure-duplicate extension method is one error, not one per call.**
  - Before: `p((Int) -> Int)` beside `p((String) -> String)` gave E0084 and an E0000 at each call.
  - Now: E0084 at the declaration only.
