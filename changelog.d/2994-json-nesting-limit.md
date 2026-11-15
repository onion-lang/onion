### Fixed

- **`Json::parse` no longer overflows the stack on deeply nested input (#2994).**
  - Before: 5,000 nested `[` killed the script with a stack overflow, even through `parseOrNull`.
  - Now: nesting past 512 levels is a `Json.JsonParseException`; `parseOrNull` returns `null`.
