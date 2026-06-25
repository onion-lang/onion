# Changelog

All notable changes to Onion are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Automated release versioning via [sbt-dynver](https://github.com/sbt/sbt-dynver).
- Versioned artifact names for releases (`onion-<version>.jar` and
  `onion-dist-<version>.zip`).
- SHA-256 checksums attached to GitHub Releases.
- `RELEASING.md` documenting the release process.

### Changed
- Release workflow now verifies that the pushed tag matches the sbt-derived
  version before publishing artifacts.

## [0.2.0-M14] - 2024-??-??

### Added
- (Previous milestone changes should be recorded here from release notes.)

## [0.1.0] - 2024-??-??

### Added
- Initial release.

[Unreleased]: https://github.com/onion-lang/onion/compare/v0.2.0-M14...develop
[0.2.0-M14]: https://github.com/onion-lang/onion/releases/tag/v0.2.0-M14
[0.1.0]: https://github.com/onion-lang/onion/releases/tag/releases/0.1
