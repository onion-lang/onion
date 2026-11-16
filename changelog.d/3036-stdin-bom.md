### Fixed

- **`IO::readLine()` no longer returns a leading BOM from piped stdin (#3036).**
  - Before: `printf '\xef\xbb\xbfline1\n' | onion s.on` read `\uFEFFline1`, so the first key looked wrong.
  - Now: the BOM is dropped, as the file readers already do.
