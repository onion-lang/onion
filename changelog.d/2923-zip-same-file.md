### Fixed

- **`Archive::zip(p, [p])` refuses to overwrite its own source (#2923).**
  - Before: it returned normally and `p` became the zip; the file's text was lost.
  - Now: it throws "source and target are the same file" and `p` stays intact, as `gzipFile` and `zipDir` already do.
  - Every file is checked before the zip is created, so a missing file no longer leaves a half-written zip.
