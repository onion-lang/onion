### Added

- **`@Trusted` on a method marks its contract as relied on, not verified.**
  - Each use is listed with the warning W0027, so what nothing checked is visible.
  - The body is not skipped by any check yet; that comes with the change that makes an unproved contract an error.
