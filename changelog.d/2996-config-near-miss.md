### Fixed

- **A misspelled key in a `shape name = config` file now shows up in the error (#2996).**
  - Before: `port: expected Int, found absent`, with no hint that the file has `prot`.
  - Now: `port: expected Int, found absent (did you mean "prot" on line 2?)`.
