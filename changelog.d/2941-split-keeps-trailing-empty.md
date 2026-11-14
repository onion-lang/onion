### Breaking

- **`Strings::split` and `Regex::split` keep trailing empty fields (#2941).**
  - Before: `Strings::split("a,b,,", ",")` was `[a, b]` (Java's `String.split`), so `row[2]` of `alice,30,` threw.
  - Now: `[a, b, , ]`, as in Python and JS. `Strings::splitRegex` and the `Pattern` overload of `Regex::split` match.
  - To drop them as before, remove the trailing `""` entries yourself.
