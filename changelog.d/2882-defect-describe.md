### Fixed

- **A defect with no path no longer prints `: :`, and a missing file says why (#2882).**
  - Before: `missing.txt:1: : expected a readable file, found /tmp/missing.txt`.
  - Now: `missing.txt:1: expected a readable file, found no such file`; also `permission denied`.
