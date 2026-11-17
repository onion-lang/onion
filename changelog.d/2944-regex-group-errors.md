### Fixed

- **`Regex::matchGroups` and `Regex::captureGroup` name the call in their errors (#2944).**
  - Before: a bad pattern threw a raw multi-line `PatternSyntaxException`; a missing group threw `IndexOutOfBoundsException: No group 3`.
  - Now: `Regex::matchGroups: invalid pattern "(" (Unclosed group)`, and `Regex::captureGroup: group 3 does not exist; pattern ... has 1 group(s)`.
