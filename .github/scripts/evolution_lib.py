"""Shared logic for the evolution gates (docs/evolution/GUARDRAILS.md).

Standard library only. Talks to the GitHub REST API with the token in GH_TOKEN.
With DRY_RUN=1, every write is printed instead of sent.
"""
import fnmatch
import json
import os
import re
import urllib.request
from datetime import datetime, timezone

API = "https://api.github.com"
REPO = os.environ.get("REPO", "onion-lang/onion")
DRY_RUN = os.environ.get("DRY_RUN", "") not in ("", "0", "false")
TIERS = ["low", "high", "ultrahigh"]


# ---------------------------------------------------------------- GitHub REST

def _request(method, path, body=None):
    url = path if path.startswith("http") else f"{API}{path}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"Bearer {os.environ['GH_TOKEN']}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    with urllib.request.urlopen(req) as resp:
        raw = resp.read()
        link = resp.headers.get("Link", "")
    return (json.loads(raw) if raw else None), link


def get(path):
    return _request("GET", path)[0]


def get_all(path):
    """Every page of a list endpoint."""
    sep = "&" if "?" in path else "?"
    url, items = f"{path}{sep}per_page=100", []
    while url:
        page, link = _request("GET", url)
        items.extend(page)
        nxt = re.search(r'<([^>]+)>;\s*rel="next"', link)
        url = nxt.group(1) if nxt else None
    return items


def write(method, path, body=None):
    if DRY_RUN:
        print(f"[dry-run] {method} {path} {json.dumps(body) if body else ''}")
        return None
    return _request(method, path, body)[0]


def set_status(sha, context, state, description):
    write("POST", f"/repos/{REPO}/statuses/{sha}",
          {"state": state, "context": context, "description": description[:140]})


def set_labels(pr, add=(), remove=()):
    current = {l["name"] for l in pr["labels"]}
    for name in add:
        if name not in current:
            write("POST", f"/repos/{REPO}/issues/{pr['number']}/labels", {"labels": [name]})
    for name in remove:
        if name in current:
            write("DELETE", f"/repos/{REPO}/issues/{pr['number']}/labels/{urllib.request.quote(name)}")


def open_prs():
    return get_all(f"/repos/{REPO}/pulls?state=open")


def comments(number):
    return get_all(f"/repos/{REPO}/issues/{number}/comments")


# ---------------------------------------------------------------- risk tiers

ULTRAHIGH_PATHS = [
    "docs/evolution/GUARDRAILS.md",
    "docs/evolution/PRINCIPLES.md",
    ".github/workflows/evolution-*.yml",
    ".github/scripts/*",
    ".github/workflows/release.yml",
    ".github/CODEOWNERS",
    "CODEOWNERS",
    "LICENSE*",
]
HIGH_PATHS = [
    "grammar/*",
    "src/main/scala/onion/compiler/parser/*",
    "docs/evolution/*",
    ".github/*",
    "build.sbt",
    "project/*",
]
WORKFLOW_POWER = re.compile(r"^[+-]\s*(permissions:|on:|pull_request_target)|secrets\.")
BUILD_PUBLISHING = re.compile(r"^[+-].*(publish|pgp|sonatype|central|credentials|signing)", re.I)
MAJOR_RELEASE_NOTE = re.compile(r"^docs/releases/([1-9][0-9]*)\.0\.0\.md$")


def _matches(path, patterns):
    return any(fnmatch.fnmatch(path, p) for p in patterns)


def _changed_lines(patch):
    return [l for l in (patch or "").splitlines() if l[:1] in "+-" and not l.startswith(("+++", "---"))]


def classify(files):
    """The minimum tier a PR's diff requires, and why. `files` is the pulls/<n>/files list."""
    tier, reasons = "low", []

    def raise_to(level, why):
        nonlocal tier
        if TIERS.index(level) > TIERS.index(tier):
            tier = level
        reasons.append(f"{level}: {why}")

    for f in files:
        path, status, lines = f["filename"], f["status"], _changed_lines(f.get("patch"))
        if _matches(path, ULTRAHIGH_PATHS):
            raise_to("ultrahigh", path)
        if MAJOR_RELEASE_NOTE.match(path):
            raise_to("ultrahigh", f"major release note {path}")
        if status == "removed" and path.startswith("src/test/"):
            raise_to("ultrahigh", f"deletes spec {path}")
        if path.startswith(".github/workflows/") and any(WORKFLOW_POWER.search(l) for l in lines):
            raise_to("ultrahigh", f"workflow permissions, triggers or secrets in {path}")
        if path == "build.sbt" and any(BUILD_PUBLISHING.match(l) for l in lines):
            raise_to("ultrahigh", "publishing or signing settings in build.sbt")
        if path.startswith(".github/workflows/") and any(
                l.startswith("-") and "test" in l for l in lines):
            raise_to("ultrahigh", f"removes a test step in {path}")
        if _matches(path, HIGH_PATHS):
            raise_to("high", path)
        if status == "removed" and path.startswith("src/"):
            raise_to("high", f"deletes {path}")
        if path == "CLAUDE.md" and any("evolution" in l.lower() for l in lines):
            raise_to("high", "CLAUDE.md evolution section")
    return tier, reasons


def labelled_tier(pr):
    names = {l["name"] for l in pr["labels"]}
    found = [t for t in TIERS if f"risk:{t}" in names]
    return max(found, key=TIERS.index) if found else None


# ---------------------------------------------------------------- panel verdicts

PANEL_MARK = re.compile(r"^<!-- panel v1 sha=([0-9a-f]{7,40}) -->")
VERDICT = re.compile(r"^verdict:\s*(APPROVED|BLOCKED)\s*$", re.M)


def panel_approval(pr, pr_comments):
    """The newest panel verdict comment for the PR's current head, if it approves; else None.

    Only a comment starting `<!-- panel v1 sha=<head> -->` with a `verdict: APPROVED` line
    counts (GUARDRAILS.md, "Only the verdict comment counts"). Labels are ignored."""
    head = pr["head"]["sha"]
    newest = None
    for c in pr_comments:
        m = PANEL_MARK.match(c.get("body") or "")
        if m and head.startswith(m.group(1)):
            newest = c
    if newest is None:
        return None
    v = VERDICT.search(newest["body"])
    return newest if v and v.group(1) == "APPROVED" else None


VETO_COMMENT = re.compile(r"^/veto\b", re.M)


def vetoed(pr, pr_comments):
    if any(l["name"] == "veto" for l in pr["labels"]):
        return True
    return any(VETO_COMMENT.search(c.get("body") or "") for c in pr_comments)


def parse_time(text):
    return datetime.fromisoformat(text.replace("Z", "+00:00"))


def now():
    return datetime.now(timezone.utc)
