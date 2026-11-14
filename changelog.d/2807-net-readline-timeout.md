### Fixed

- **`Net.Conn.readLine()` after a read timeout returns the whole line.**
  - Before: `"hel"` arrives, `readLine()` times out, the next call returned `"lo"`.
  - Now: it returns `"hello"`. A `\r\n` split by a timeout is still one line break.
