### Fixed

- **`Net` `readAll()`/`readBytes()` no longer lose bytes when a read times out.**
  - Before: a timeout dropped what had arrived, so a retry returned `" world"` for `"hello world"`.
  - Now: the bytes are kept, and the retry returns all of it.
