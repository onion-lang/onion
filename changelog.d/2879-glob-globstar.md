### Fixed

- **`Files::glob(dir, "**/*.on")` now finds files directly in `dir`.**
  - Before: only files in a subdirectory matched, so top-level files were silently left out.
  - Now: `**/` also matches zero directories, as in bash `globstar` and Python `glob`.
