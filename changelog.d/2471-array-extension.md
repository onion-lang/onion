### Fixed

- **A method in `extension String[]` or `extension Int[]` is found at the call.**
  - Before: E0005 on every call.
  - Now: it resolves; `extension Int[]` stays off `Long[]`, and `extension String[]` stays off `Integer[]`.
