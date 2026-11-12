### Fixed

- **`DateTime::parse("2026-11-11T10:00:00Z")` parses (#2833).**
  - Before: a timestamp with `Z` or an offset (`+09:00`) was rejected.
  - Now: it parses to that exact instant; the error for other text lists the accepted forms.
