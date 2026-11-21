### Fixed

- **A config file's leading BOM survives `readLossless` + `render` (#3035).**
  - Before: the BOM was dropped, so an edit rewrote more than one value slot.
  - Now: `render` writes the BOM back; the first key is still read without it.
