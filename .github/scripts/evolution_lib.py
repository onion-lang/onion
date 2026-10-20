"""Shared logic for the evolution gates (docs/evolution/GUARDRAILS.md).

Standard library only. Talks to the GitHub REST API with the token in GH_TOKEN.
With DRY_RUN=1, every write is printed instead of sent.

Everything here fails safe: when in doubt, a PR gets the higher tier, an approval is not
counted, and a veto is.
"""
import fnmatch
import json
import os
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

API = "https://api.github.com"
REPO = os.environ.get("REPO", "onion-lang/onion")
DRY_RUN = os.environ.get("DRY_RUN", "") not in ("", "0", "false")
TIERS = ["low", "high", "ultrahigh"]

# Who may post a panel verdict or a /veto comment. Every actor uses this account today.
TRUSTED_AUTHORS = set(os.environ.get("PANEL_AUTHORS", "kmizu").split(","))
TRUSTED_ASSOCIATIONS = {"OWNER", "MEMBER", "COLLABORATOR"}

# The files API returns at most this many files; beyond it the diff cannot be judged.
FILES_API_LIMIT = 3000


# ---------------------------------------------------------------- GitHub REST

def _request(method, path, body=None, attempts=3):
    """One REST call, retried on transient network errors (not on HTTP errors)."""
    for attempt in range(attempts):
        try:
            return _request_once(method, path, body)
        except urllib.error.HTTPError:
            raise
        except (urllib.error.URLError, ConnectionError, TimeoutError):
            if attempt == attempts - 1:
                raise
            time.sleep(2 ** attempt)


def _request_once(method, path, body=None):
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


def write(method, path, body=None, ok_statuses=()):
    if DRY_RUN:
        print(f"[dry-run] {method} {path} {json.dumps(body) if body else ''}")
        return None
    try:
        return _request(method, path, body)[0]
    except urllib.error.HTTPError as e:
        if e.code in ok_statuses:
            return None
        raise


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
            # Another run may have removed it already: a 404 is the state we wanted.
            write("DELETE", f"/repos/{REPO}/issues/{pr['number']}/labels/{urllib.parse.quote(name)}",
                  ok_statuses=(404,))


def open_prs():
    return get_all(f"/repos/{REPO}/pulls?state=open")


def comments(number):
    return get_all(f"/repos/{REPO}/issues/{number}/comments")


def timeline(number):
    return get_all(f"/repos/{REPO}/issues/{number}/timeline")


def pr_files(number):
    return get_all(f"/repos/{REPO}/pulls/{number}/files")


# ---------------------------------------------------------------- risk tiers

ULTRAHIGH_PATHS = [
    "docs/evolution/GUARDRAILS.md",
    "docs/evolution/PRINCIPLES.md",
    # Every workflow and action: permissions, triggers and gates all live there.
    ".github/workflows/*",
    ".github/actions/*",
    ".github/scripts/*",
    ".github/CODEOWNERS",
    "CODEOWNERS",
    "docs/CODEOWNERS",
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
SPEC_FILE = re.compile(r"^src/test/.*Spec\.scala$")
SECRETS = re.compile(r"secrets\.")
BUILD_PUBLISHING = re.compile(r"^[+-].*(publish|pgp|sonatype|central|credentials|signing)", re.I)
MAJOR_RELEASE_NOTE = re.compile(r"^docs/releases/([1-9][0-9]*)\.0\.0\.md$")


def _matches(path, patterns):
    return path is not None and any(fnmatch.fnmatch(path, p) for p in patterns)


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

    if len(files) >= FILES_API_LIMIT:
        raise_to("ultrahigh", f"{len(files)} files: too many to judge from the API")

    for f in files:
        status = f["status"]
        # A rename is judged by both names: moving a guarded file out is as risky as editing it.
        paths = [p for p in (f["filename"], f.get("previous_filename")) if p]
        lines = _changed_lines(f.get("patch"))
        for path in paths:
            if _matches(path, ULTRAHIGH_PATHS):
                raise_to("ultrahigh", path)
            if MAJOR_RELEASE_NOTE.match(path):
                raise_to("ultrahigh", f"major release note {path}")
            if _matches(path, HIGH_PATHS):
                raise_to("high", path)
        previous = f.get("previous_filename")
        if status == "removed" and SPEC_FILE.match(f["filename"]):
            raise_to("ultrahigh", f"deletes spec {f['filename']}")
        if status == "renamed" and previous and SPEC_FILE.match(previous) and not SPEC_FILE.match(f["filename"]):
            raise_to("ultrahigh", f"moves spec {previous} out of the suite")
        if status == "removed" and f["filename"].startswith("src/"):
            raise_to("high", f"deletes {f['filename']}")
        if any(SECRETS.search(l) for l in lines):
            raise_to("ultrahigh", f"secrets in {f['filename']}")
        if f["filename"] == "build.sbt":
            if f.get("patch") is None and status != "removed":
                raise_to("ultrahigh", "build.sbt diff not shown by the API")
            elif any(BUILD_PUBLISHING.match(l) for l in lines):
                raise_to("ultrahigh", "publishing or signing settings in build.sbt")
        if f["filename"] == "CLAUDE.md" and any("evolution" in l.lower() for l in lines):
            raise_to("high", "CLAUDE.md evolution section")
    return tier, reasons


def labelled_tier(pr):
    names = {l["name"] for l in pr["labels"]}
    found = [t for t in TIERS if f"risk:{t}" in names]
    return max(found, key=TIERS.index) if found else None


def effective_tier(pr, files):
    """The higher of the label and the diff: removing a label never lowers the tier."""
    computed, reasons = classify(files)
    label = labelled_tier(pr)
    tier = computed if label is None else max(label, computed, key=TIERS.index)
    return tier, computed, label, reasons


# ---------------------------------------------------------------- panel verdicts

PANEL_MARK = re.compile(r"^<!-- panel v1 sha=([0-9a-f]{40}) -->")
VERDICT = re.compile(r"^verdict:\s*(APPROVED|BLOCKED)\s*$", re.M)


def trusted(comment):
    user = (comment.get("user") or {}).get("login")
    return user in TRUSTED_AUTHORS and comment.get("author_association") in TRUSTED_ASSOCIATIONS


def unedited(comment):
    return comment.get("updated_at") in (None, comment.get("created_at"))


def panel_approval(pr, pr_comments):
    """The newest panel verdict for the PR's current head, if it approves; else None.

    It counts only if it starts with `<!-- panel v1 sha=<full 40-char head sha> -->`, has a
    `verdict: APPROVED` line, was written by a trusted author, and was never edited (an edit
    would let an old comment's timestamp skip the veto window). Labels are ignored."""
    head = pr["head"]["sha"]
    newest = None
    for c in pr_comments:
        m = PANEL_MARK.match(c.get("body") or "")
        if m and m.group(1) == head and trusted(c):
            newest = c
    # Pick the newest verdict first, then judge it: an edited or blocking newest verdict
    # means no approval, so editing a BLOCKED verdict cannot revive an older APPROVED one.
    if newest is None or not unedited(newest):
        return None
    v = VERDICT.search(newest["body"])
    return newest if v and v.group(1) == "APPROVED" else None


VETO_COMMENT = re.compile(r"^/veto\b", re.M)


def veto_comment(pr_comments):
    """A trusted `/veto` comment, if any."""
    return next((c for c in pr_comments if trusted(c) and VETO_COMMENT.search(c.get("body") or "")), None)


def vetoed(pr, pr_comments, events=()):
    """A veto is permanent: the label now, the label at any point in the timeline, or a
    trusted `/veto` comment. Removing the label or the comment does not undo it."""
    if any(l["name"] == "veto" for l in pr["labels"]):
        return True
    if any(e.get("event") == "labeled" and (e.get("label") or {}).get("name") == "veto" for e in events):
        return True
    return veto_comment(pr_comments) is not None


def still_approved(number):
    """Re-read the PR right after writing success: a newer BLOCKED verdict or a new push wins."""
    pr = get(f"/repos/{REPO}/pulls/{number}")
    return panel_approval(pr, comments(number)) is not None


def parse_time(text):
    return datetime.fromisoformat(text.replace("Z", "+00:00"))


def now():
    return datetime.now(timezone.utc)
