### Fixed

- **`Http::decodeUrl("100%zz")` names the bad escape, as `Codec::urlDecode` does (#2455).**
  - Before: the JDK's `Illegal hex characters in escape (%) pattern - Error at index 0 in: "zz"`.
  - Now: `invalid % escape at index 3 in "100%zz": a % must be followed by two hex digits`; a long input is quoted as 20 characters either side of the escape, not in full.
