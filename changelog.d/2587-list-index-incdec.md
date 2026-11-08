### Fixed

- **`ns[0]++` and `counts[k]--` work on a `List` or `Map` element (#2587).**
  - Before: E0001 "operator ++ is not applicable for type Int", though `ns[0] += 1` worked.
  - Now: `ns[0]++` means `ns[0] += 1`, and its value is the old element, as for an array.
