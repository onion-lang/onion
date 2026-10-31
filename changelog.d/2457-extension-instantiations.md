### Fixed

- **`extension List[Int]` and `extension List[String]` can both declare `describe()`.**
  - Before: every call was E0006 (ambiguous) plus E0005.
  - Now: the block whose type argument matches the receiver is chosen.
