### Fixed

- **W0028 no longer prints an empty example.**
  - Before: `can break its ensures: for example when .`
  - Now: `can break its ensures: for every call.` when the body breaks the clause for any input.
