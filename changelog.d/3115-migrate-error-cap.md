### Fixed

- **`onion migrate` on a big directory no longer says an old file "compiles as written" (#3115).**
  - Before: with over 1000 compile errors in the joint compile, a file with an old spelling (`ret`, `self`) was left alone, reported "already up to date", and the exit code was 0.
  - Now: that file is counted as "need a manual change" and the exit code is 1. Name fewer files, or the file alone, to migrate it.
