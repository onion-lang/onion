### Breaking

- **`Strings::split` and `Regex::split` keep trailing empty fields (#2941).**
  - Before: `Strings::split("a,b,,", ",")` was `[a, b]` (Java's `String.split`), so `row[2]` of `alice,30,` threw.
  - Now: `[a, b, , ]`, as in Python and JS; an empty delimiter still gives `[a, b, c]` for `"abc"`. See "Migrating to 1.0" to keep the old result.
