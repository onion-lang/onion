### Fixed

- **`file(p).readLossless(shape)` hands a user-written shape the text without the file's BOM, as `read(shape)` does.**
  - Before: a file `\uFEFFalice` gave `read` `alice` (5) but `readLossless` `\uFEFFalice` (6).
  - Now: both give `alice`. A shape that keeps the mark in its residue says so with `keepsByteOrderMark()`; the built-in `config` shape does.
