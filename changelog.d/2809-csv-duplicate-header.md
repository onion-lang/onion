### Fixed

- **`Csv::parseWithHeader` no longer drops a column whose header repeats (#2809).**
  - Before: `h,h,k` kept only the second `h`; the first value vanished without a word.
  - Now: it throws `IllegalArgumentException` naming the header and both column numbers. A second blank header counts as a repeat.
