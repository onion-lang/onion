### Fixed

- **Two scripts with the same file name (`d1/x.on`, `d2/x.on`) give E0008 instead of an internal error.**
  - Before: `[I0000] Internal compiler error ... Duplicate method name "start"`.
  - Now: E0008 names the shared class `xMain` and says to rename one file.
