### Fixed

- **An error inside a lambda passed to an extension name with an E0084 twin is reported (#2550).**
  - Before: `"a".p { x -> undefinedName }` showed only E0084; the E0002 appeared after you fixed it.
  - Now: both are reported in one compile. Only the call's own E0000/E0005/E0006 stay hidden.
