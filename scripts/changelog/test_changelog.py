#!/usr/bin/env python3

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("changelog.py")

CHANGELOG = """# Changelog

Intro.

## [Unreleased]

### Fixed

- **Direct entry (#100).**
  - Before: old.

## [0.146.0] - 2026-10-25

### Fixed

- **Released entry (#90).**
"""


class ChangelogFragmentsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / "changelog.d").mkdir()
        (self.root / "changelog.d/README.md").write_text("# How to\n\n### Not a fragment\n")
        (self.root / "CHANGELOG.md").write_text(CHANGELOG)

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def fragment(self, name: str, text: str) -> None:
        (self.root / "changelog.d" / name).write_text(text)

    def run_script(self, *args: str) -> subprocess.CompletedProcess:
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--root", str(self.root), *args],
            capture_output=True, text=True,
        )

    def test_cut_gathers_fragments_newest_first_above_direct_entries(self) -> None:
        self.fragment("2401-default-arg.md", "### Fixed\n\n- **Older fix (#2401).**\n  - Now: works.\n")
        self.fragment("2430-empty-literal.md",
                      "### Breaking\n\n- **Break (#2430).**\n\n### Fixed\n\n- **Newer fix (#2430).**\n")
        result = self.run_script("cut", "0.147.0", "--date", "2026-10-27")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.splitlines(), [
            "consumed changelog.d/2430-empty-literal.md",
            "consumed changelog.d/2401-default-arg.md",
        ])
        self.assertEqual((self.root / "CHANGELOG.md").read_text(), """# Changelog

Intro.

## [Unreleased]

## [0.147.0] - 2026-10-27

### Breaking

- **Break (#2430).**

### Fixed

- **Newer fix (#2430).**
- **Older fix (#2401).**
  - Now: works.
- **Direct entry (#100).**
  - Before: old.

## [0.146.0] - 2026-10-25

### Fixed

- **Released entry (#90).**
""")
        self.assertEqual(sorted(p.name for p in (self.root / "changelog.d").iterdir()), ["README.md"])

    def test_preview_changes_nothing(self) -> None:
        self.fragment("2401-a.md", "### Added\n\n- **New thing (#2401).**\n")
        result = self.run_script("preview")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "### Added\n\n- **New thing (#2401).**\n\n### Fixed\n\n"
                                        "- **Direct entry (#100).**\n  - Before: old.\n")
        self.assertEqual((self.root / "CHANGELOG.md").read_text(), CHANGELOG)
        self.assertTrue((self.root / "changelog.d/2401-a.md").exists())

    def test_check_rejects_a_malformed_fragment_and_names_it(self) -> None:
        self.fragment("1-ok.md", "### Fixed\n\n- **Fine.**\n")
        self.fragment("2-unknown.md", "### Fixes\n\n- **Typo in the section.**\n")
        self.fragment("3-preamble.md", "- **No heading.**\n")
        self.fragment("4-empty.md", "### Fixed\n\nJust prose.\n")
        result = self.run_script("check")
        self.assertEqual(result.returncode, 1)
        for expected in ("2-unknown.md:1: unknown section '### Fixes'",
                         "3-preamble.md:1: text before the first",
                         "4-empty.md: section '### Fixed' has no '- ' entry"):
            self.assertIn(expected, result.stderr)
        self.assertNotIn("1-ok.md", result.stderr)

    def test_cut_refuses_a_bad_or_existing_version_and_an_empty_release(self) -> None:
        self.assertIn("not a version", self.run_script("cut", "v0.147.0").stderr)
        self.fragment("1-ok.md", "### Fixed\n\n- **Fine.**\n")
        self.assertIn("already has", self.run_script("cut", "0.146.0").stderr)
        (self.root / "CHANGELOG.md").write_text("# Changelog\n\n## [Unreleased]\n\n## [0.1.0] - 2026-01-01\n")
        (self.root / "changelog.d/1-ok.md").unlink()
        result = self.run_script("cut", "0.2.0")
        self.assertEqual(result.returncode, 1)
        self.assertIn("nothing to release", result.stderr)

    def test_a_failed_cut_leaves_everything_in_place(self) -> None:
        self.fragment("1-ok.md", "### Fixed\n\n- **Fine.**\n")
        self.fragment("2-bad.md", "### Fixes\n\n- **Typo.**\n")
        self.assertEqual(self.run_script("cut", "0.147.0").returncode, 1)
        self.assertEqual((self.root / "CHANGELOG.md").read_text(), CHANGELOG)
        self.assertTrue((self.root / "changelog.d/1-ok.md").exists())


if __name__ == "__main__":
    unittest.main()
