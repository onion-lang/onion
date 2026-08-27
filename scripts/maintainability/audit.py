#!/usr/bin/env python3
"""Produce a deterministic maintainability snapshot for hand-written sources."""

from __future__ import annotations

import argparse
import itertools
import json
import re
import subprocess
from collections import Counter, defaultdict
from pathlib import Path


SOURCE_ROOTS = ("src/main/scala", "src/main/java")
SOURCE_SUFFIXES = (".scala", ".java")
PACKAGE_RE = re.compile(r"^\s*package\s+([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)", re.MULTILINE)
IMPORT_RE = re.compile(
    r"^\s*import\s+(?:_root_\.)?([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)+)",
    re.MULTILINE,
)
DEFINITION_RE = re.compile(
    r"^\s*(?:(?:private|protected|public|final|sealed|abstract|static|case)\s+)*"
    r"(?:class|trait|object|enum|interface|record|def|val|var)\b",
    re.MULTILINE,
)
BRANCH_RE = re.compile(r"\b(?:if|for|while|case|catch)\b")


def git(repo: Path, *args: str) -> str:
    completed = subprocess.run(
        ["git", *args],
        cwd=repo,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return completed.stdout


def tracked_sources(repo: Path) -> list[str]:
    output = git(repo, "ls-files", "-z", "--", *SOURCE_ROOTS)
    return sorted(
        path
        for path in output.split("\0")
        if path and path.endswith(SOURCE_SUFFIXES) and not path.startswith("target/")
    )


def resolve_import(import_name: str, packages: set[str]) -> str | None:
    matches = [
        package
        for package in packages
        if import_name == package or import_name.startswith(package + ".")
    ]
    return max(matches, key=len, default=None)


def history_metrics(
    repo: Path, paths: list[str], max_commits: int
) -> tuple[Counter[str], Counter[str], Counter[tuple[str, str]], int]:
    if not paths:
        return Counter(), Counter(), Counter(), 0

    output = git(
        repo,
        "log",
        f"-n{max_commits}",
        "--format=COMMIT %H",
        "--numstat",
        "--",
        *SOURCE_ROOTS,
    )
    tracked = set(paths)
    commit_count: Counter[str] = Counter()
    churn: Counter[str] = Counter()
    cochanges: Counter[tuple[str, str]] = Counter()
    current_paths: set[str] = set()
    commits_scanned = 0

    def flush() -> None:
        nonlocal commits_scanned
        if not current_paths:
            return
        commits_scanned += 1
        for path in current_paths:
            commit_count[path] += 1
        for pair in itertools.combinations(sorted(current_paths), 2):
            cochanges[pair] += 1
        current_paths.clear()

    for line in output.splitlines():
        if line.startswith("COMMIT "):
            flush()
            continue
        parts = line.split("\t", 2)
        if len(parts) != 3:
            continue
        added, deleted, path = parts
        if path not in tracked or not added.isdigit() or not deleted.isdigit():
            continue
        current_paths.add(path)
        churn[path] += int(added) + int(deleted)
    flush()
    return commit_count, churn, cochanges, commits_scanned


def build_report(repo: Path, max_commits: int) -> dict[str, object]:
    paths = tracked_sources(repo)
    texts = {
        path: (repo / path).read_text(encoding="utf-8", errors="replace") for path in paths
    }
    packages = {
        path: (match.group(1) if (match := PACKAGE_RE.search(text)) else "")
        for path, text in texts.items()
    }
    known_packages = {package for package in packages.values() if package}
    package_dependencies: dict[str, set[str]] = defaultdict(set)
    package_dependents: dict[str, set[str]] = defaultdict(set)

    for path, text in texts.items():
        source_package = packages[path]
        for import_name in IMPORT_RE.findall(text):
            target_package = resolve_import(import_name, known_packages)
            if target_package and target_package != source_package:
                package_dependencies[source_package].add(target_package)
                package_dependents[target_package].add(source_package)

    commit_count, churn, cochanges, commits_scanned = history_metrics(
        repo, paths, max_commits
    )
    files: list[dict[str, object]] = []
    for path in paths:
        text = texts[path]
        package = packages[path]
        loc = len(text.splitlines())
        definitions = len(DEFINITION_RE.findall(text))
        branches = len(BRANCH_RE.findall(text))
        fan_in = len(package_dependents[package])
        fan_out = len(package_dependencies[package])
        score = (
            loc
            + branches * 5
            + fan_in * 10
            + fan_out * 10
            + commit_count[path] * 10
            + churn[path]
        )
        files.append(
            {
                "path": path,
                "package": package,
                "bytes": len(text.encode("utf-8")),
                "loc": loc,
                "definitions": definitions,
                "branches": branches,
                "fan_in": fan_in,
                "fan_out": fan_out,
                "commit_count": commit_count[path],
                "churn": churn[path],
                "hotspot_score": score,
            }
        )

    top_cochanges = [
        {"left": left, "right": right, "commits": count}
        for (left, right), count in sorted(
            cochanges.items(), key=lambda item: (-item[1], item[0])
        )[:50]
    ]
    return {
        "schema_version": 1,
        "head": git(repo, "rev-parse", "HEAD").strip(),
        "history": {
            "max_commits": max_commits,
            "commits_scanned": commits_scanned,
        },
        "summary": {
            "source_files": len(files),
            "total_loc": sum(int(entry["loc"]) for entry in files),
        },
        "files": files,
        "top_cochanges": top_cochanges,
    }


def render_markdown(report: dict[str, object]) -> str:
    files = sorted(
        report["files"],
        key=lambda entry: (-entry["hotspot_score"], entry["path"]),
    )
    lines = [
        "# Maintainability audit",
        "",
        f"- HEAD: `{report['head']}`",
        f"- Hand-written source files: {report['summary']['source_files']}",
        f"- Total LOC: {report['summary']['total_loc']}",
        f"- History window: {report['history']['commits_scanned']} of at most {report['history']['max_commits']} commits",
        "",
        "## File hotspots",
        "",
        "| Path | LOC | Definitions | Branches | Fan-in | Fan-out | Commits | Churn |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for entry in files:
        lines.append(
            "| `{path}` | {loc} | {definitions} | {branches} | {fan_in} | "
            "{fan_out} | {commit_count} | {churn} |".format(**entry)
        )

    lines.extend(
        [
            "",
            "## Top co-change pairs",
            "",
            "| Left | Right | Commits |",
            "|---|---|---:|",
        ]
    )
    for pair in report["top_cochanges"]:
        lines.append(
            f"| `{pair['left']}` | `{pair['right']}` | {pair['commits']} |"
        )
    return "\n".join(lines) + "\n"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument(
        "--output-dir", type=Path, default=Path("target/maintainability")
    )
    parser.add_argument("--max-commits", type=int, default=200)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    repo = args.repo.resolve()
    output_dir = args.output_dir
    if not output_dir.is_absolute():
        output_dir = repo / output_dir
    output_dir.mkdir(parents=True, exist_ok=True)
    report = build_report(repo, args.max_commits)
    (output_dir / "audit.json").write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    (output_dir / "audit.md").write_text(render_markdown(report), encoding="utf-8")


if __name__ == "__main__":
    main()
