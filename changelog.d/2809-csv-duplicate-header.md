### Fixed

- **`Csv::parseWithHeader` no longer drops a value whose header repeats (#2809).**
  - Before: `h,h,k` kept only the second `h`; the first column's values vanished without a word.
  - Now: it throws `IllegalArgumentException` naming the header, both columns and the row. A repeat whose earlier column is empty in every row (blank trailing headers in a spreadsheet export, say) still parses as before.
