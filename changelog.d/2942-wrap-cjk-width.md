### Fixed

- **`Text::wrap` wraps Japanese and other full-width text by terminal columns.**
  - Before: text with no spaces stayed one line, and width was counted in characters.
  - Now: lines break between full-width characters (2 columns each), as `Text::table` counts them; `。` and `」` never start a line.
