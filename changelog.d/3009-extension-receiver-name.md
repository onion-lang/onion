### Fixed

- **E0077 names an extension call by its declared receiver.**
  - Before: `extension List[Int]` read `List.int.shout`, and `Big_Box` read `Big.Box.yell`.
  - Now: `List[Int].shout` and `Big_Box.yell`.
