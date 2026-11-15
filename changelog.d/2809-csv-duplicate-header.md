### Fixed

- **`Csv::parseWithHeader` no longer drops a value whose header repeats (#2809).**
  - Before: `h,h,k` kept only the second `h`; the first column's values vanished without a word.
  - Now: it throws `IllegalArgumentException` naming the header, both columns and the row, when an earlier column holds a different value.
  - Unchanged: a repeat that loses nothing still parses, such as blank trailing headers or `id,name,id` with equal ids.
