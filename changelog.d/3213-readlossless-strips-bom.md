### Fixed

- **`file(p).readLossless(shape)` hands a user-written shape the text without the file's BOM, as `read(shape)` does.**
  - Before: a file `﻿alice` gave `read` `alice` (5) but `readLossless` `﻿alice` (6).
  - Now: both give `alice`, and `render()` writes the BOM back, so the file still round-trips byte for byte.
