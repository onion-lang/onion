### Fixed

- **`Codec::base64Decode(Files::readText("token.b64"))` works when the file was saved as UTF-8 with BOM (#2454).**
  - Before: `invalid Base64 character '?' at index 0`, with the invisible character printed raw.
  - Now: a leading BOM is skipped. Any other invisible or non-ASCII bad character is named as `U+XXXX`, for example `invalid Base64 character U+00A0 at index 4` for a no-break space.
