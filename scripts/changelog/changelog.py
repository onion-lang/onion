#!/usr/bin/env python3
"""Gather changelog fragments from changelog.d/ into CHANGELOG.md.

Each PR adds its own file, changelog.d/<issue>-<slug>.md, instead of editing
CHANGELOG.md, so two open PRs never conflict over the same lines. The PR that
cuts a release runs `cut` to move every fragment, together with anything still
written directly under [Unreleased], under the new version heading.

  changelog.py check              validate every fragment
  changelog.py preview            print what the next release will say
  changelog.py cut 0.147.0        write the release section and delete the fragments
"""

from __future__ import annotations

import argparse
import datetime
import re
import sys
from pathlib import Path


# Keep a Changelog's sections, with Onion's Breaking first and Documentation last.
SECTIONS = ("Breaking", "Added", "Changed", "Deprecated", "Removed", "Fixed", "Security", "Documentation")
FRAGMENT_DIR = "changelog.d"
SKIPPED = {"README.md"}
SECTION_RE = re.compile(r"^### (.+?)\s*$")
VERSION_RE = re.compile(r"^\d+\.\d+\.\d+(?:-[0-9A-Za-z.]+)?$")
UNRELEASED = "## [Unreleased]"


class ChangelogError(Exception):
    pass


def parse_sections(text: str, where: str, strict: bool) -> tuple[list[str], dict[str, list[str]]]:
    """Split text into its `### Section` blocks.

    Returns the lines before the first heading and, per section, its entry lines with
    surrounding blank lines removed. `strict` is for fragments: only known sections,
    nothing before the first heading, and at least one `- ` entry in each section.
    """
    preamble: list[str] = []
    sections: dict[str, list[str]] = {}
    current: list[str] | None = None
    for number, line in enumerate(text.splitlines(), start=1):
        heading = SECTION_RE.match(line)
        if heading:
            name = heading.group(1)
            if strict and name not in SECTIONS:
                raise ChangelogError(
                    f"{where}:{number}: unknown section '### {name}'; use one of: "
                    + ", ".join(SECTIONS)
                )
            current = sections.setdefault(name, [])
            if current:
                current.append("")
            continue
        if strict and line.startswith("#"):
            raise ChangelogError(f"{where}:{number}: only '### Section' headings belong in a fragment")
        if current is None:
            if strict and line.strip():
                raise ChangelogError(f"{where}:{number}: text before the first '### Section' heading")
            preamble.append(line)
        else:
            current.append(line)
    for name, lines in sections.items():
        sections[name] = trim_blank(lines)
        if strict and not any(l.startswith("- ") for l in sections[name]):
            raise ChangelogError(f"{where}: section '### {name}' has no '- ' entry")
    if strict and not sections:
        raise ChangelogError(f"{where}: no '### Section' heading")
    return trim_blank(preamble), sections


def trim_blank(lines: list[str]) -> list[str]:
    start, end = 0, len(lines)
    while start < end and not lines[start].strip():
        start += 1
    while end > start and not lines[end - 1].strip():
        end -= 1
    return lines[start:end]


def fragment_key(path: Path) -> tuple[int, str]:
    # Newest issue first, as in CHANGELOG.md; unnumbered fragments go last.
    number = re.match(r"^(\d+)", path.name)
    return (-int(number.group(1)) if number else 0, path.name)


def fragments(root: Path) -> list[Path]:
    directory = root / FRAGMENT_DIR
    if not directory.is_dir():
        return []
    found = [p for p in directory.iterdir() if p.is_file() and p.name not in SKIPPED]
    for p in found:
        if p.suffix != ".md":
            raise ChangelogError(f"{FRAGMENT_DIR}/{p.name}: a fragment is a .md file")
    return sorted(found, key=fragment_key)


def read_fragments(root: Path) -> list[tuple[Path, dict[str, list[str]]]]:
    errors, parsed = [], []
    for path in fragments(root):
        try:
            _, sections = parse_sections(path.read_text(encoding="utf-8"), f"{FRAGMENT_DIR}/{path.name}", True)
            parsed.append((path, sections))
        except ChangelogError as e:
            errors.append(str(e))
    if errors:
        raise ChangelogError("\n".join(errors))
    return parsed


def split_changelog(text: str) -> tuple[list[str], list[str], list[str]]:
    """Return (lines up to and including the [Unreleased] heading, its body, the rest)."""
    lines = text.splitlines()
    try:
        start = next(i for i, l in enumerate(lines) if l.strip() == UNRELEASED)
    except StopIteration:
        raise ChangelogError(f"CHANGELOG.md has no '{UNRELEASED}' heading")
    end = next((i for i in range(start + 1, len(lines)) if lines[i].startswith("## ")), len(lines))
    return lines[: start + 1], lines[start + 1 : end], lines[end:]


def merged_sections(root: Path) -> tuple[list[str], list[tuple[str, list[str]]], list[Path]]:
    """The next release's text: fragments first (newest issue first), then [Unreleased]."""
    _, body, _ = split_changelog((root / "CHANGELOG.md").read_text(encoding="utf-8"))
    preamble, unreleased = parse_sections("\n".join(body), "CHANGELOG.md [Unreleased]", False)
    parsed = read_fragments(root)
    names = list(SECTIONS) + [n for n in unreleased if n not in SECTIONS]
    out = []
    for name in names:
        entries: list[str] = []
        for _, sections in parsed:
            entries += sections.get(name, [])
        entries += unreleased.get(name, [])
        if entries:
            out.append((name, entries))
    return preamble, out, [p for p, _ in parsed]


def render(preamble: list[str], sections: list[tuple[str, list[str]]]) -> list[str]:
    out: list[str] = []
    if preamble:
        out += preamble + [""]
    for name, entries in sections:
        out += [f"### {name}", ""] + entries + [""]
    return out


def cut(root: Path, version: str, date: str) -> list[Path]:
    if not VERSION_RE.match(version):
        raise ChangelogError(f"'{version}' is not a version like 0.147.0 (no leading v)")
    changelog = root / "CHANGELOG.md"
    text = changelog.read_text(encoding="utf-8")
    if re.search(rf"^## \[{re.escape(version)}\]", text, re.M):
        raise ChangelogError(f"CHANGELOG.md already has a '## [{version}]' heading")
    preamble, sections, consumed = merged_sections(root)
    if not sections:
        raise ChangelogError("nothing to release: no fragment and an empty [Unreleased]")
    head, _, rest = split_changelog(text)
    lines = head + ["", f"## [{version}] - {date}", ""] + render(preamble, sections) + rest
    changelog.write_text("\n".join(lines).rstrip("\n") + "\n", encoding="utf-8")
    for path in consumed:
        path.unlink()
    return consumed


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("check", help="validate every fragment")
    sub.add_parser("preview", help="print the next release's sections")
    cut_parser = sub.add_parser("cut", help="move fragments and [Unreleased] under a version heading")
    cut_parser.add_argument("version", help="the released version, e.g. 0.147.0")
    cut_parser.add_argument("--date", default=datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d"))
    args = parser.parse_args(argv)
    try:
        if args.command == "check":
            print(f"{len(read_fragments(args.root))} fragment(s) OK")
        elif args.command == "preview":
            preamble, sections, _ = merged_sections(args.root)
            print("\n".join(render(preamble, sections)).rstrip("\n") or "(nothing unreleased)")
        else:
            for path in cut(args.root, args.version, args.date):
                print(f"consumed {FRAGMENT_DIR}/{path.name}")
    except ChangelogError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
