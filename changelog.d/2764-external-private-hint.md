### Fixed

- **E0013/E0014 on a private member of a Java or stdlib class no longer says "move it under a `public:` section".**
  - Before: `r.status = 1` on `Server.Response` told you to edit a class you cannot edit.
  - Now: the hint says the class is not Onion source and to use its public methods or fields.
