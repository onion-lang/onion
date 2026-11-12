### Added

- **A cast the JVM cannot fully check now warns: W0011 (#2665).**
  - Before: `o as List[String]` or `o as T` compiled silently. A wrong element failed later, where it was read.
  - Now: W0011 at the cast. Upcasts, all-`Object` arguments and empty `[]`/`[:]` literals do not warn. Silence it with `--Wno unchecked-cast`.
