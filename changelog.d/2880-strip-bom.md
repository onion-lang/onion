### Fixed

- **`Files::readText`, `Files::readLines` and `file"…".text()` no longer return a UTF-8 BOM as text (#2880).**
  - Before: a file saved by Windows Notepad kept U+FEFF, so its first key or line looked wrong (`host: found absent`).
  - Now: a leading BOM is dropped, as `Csv` already did.
