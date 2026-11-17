### Fixed

- **`Strings::chars` and `Strings::truncate` no longer split an emoji.**
  - Before: `chars("a😀b")` had 4 entries, two of them broken halves; `truncate` could end on half an emoji.
  - Now: `chars` gives one entry per emoji, and `truncate` steps back instead of cutting a pair.
