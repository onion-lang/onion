### Fixed

- **E0110 points at its own `invariant`/`history` clause (#2797).**
  - Before: when an earlier clause failed to type, E0110 for a later clause was reported on the earlier clause's line.
  - Now: it points at the field the later clause names.
