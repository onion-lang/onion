### Fixed

- **The E0013/E0014 hint for a protected Java member no longer calls it "private" (#2904).**
  - Before: `a.removeRange(0, 0)` said the member "is private ... cannot be changed from here".
  - Now: "not public in a class that is not Onion source, so it cannot be used from here".
