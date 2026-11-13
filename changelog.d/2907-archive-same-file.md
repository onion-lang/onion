### Fixed

- **`Archive::gzipFile(p, p)` and `gunzipFile(p, p)` refuse to run instead of destroying the file.**
  - Before: returned normally, and the file was left holding a bare gzip header.
  - Now: `Archive: could not gzip p to p (source and target are the same file)`, and the file is untouched.
