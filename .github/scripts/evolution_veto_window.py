"""The `evolution/veto-window` check (docs/evolution/GUARDRAILS.md, "Veto").

For each PR (PR_NUMBER, or every open PR):
- not `risk:ultrahigh`: success at once;
- vetoed (a `veto` label or a `/veto` comment, from anyone): pending forever;
- no approving panel verdict for the current head: pending;
- otherwise success once WINDOW_HOURS (default 48) have passed since that verdict.
"""
import os
import sys
from datetime import timedelta

import evolution_lib as ev

WINDOW = timedelta(hours=float(os.environ.get("WINDOW_HOURS") or 48))
CONTEXT = "evolution/veto-window"


def still_unvetoed(number):
    """Re-read the PR right after writing success: a veto that landed meanwhile wins."""
    pr = ev.get(f"/repos/{ev.REPO}/pulls/{number}")
    return not ev.vetoed(pr, ev.comments(number), ev.timeline(number))


def evaluate(pr):
    # Fresh state for this PR: the sweep's list may be minutes old.
    pr = ev.get(f"/repos/{ev.REPO}/pulls/{pr['number']}")
    number, head = pr["number"], pr["head"]["sha"]
    # The diff, not just the label: removing `risk:ultrahigh` must not skip the window.
    files = ev.pr_files(number)
    tier, _, _, _ = ev.effective_tier(pr, files)
    if tier != "ultrahigh":
        ev.set_status(head, CONTEXT, "success", "not risk:ultrahigh: no veto window")
        ev.set_labels(pr, remove=["veto-window:open"])
        print(f"#{number}: not ultrahigh")
        return
    pr_comments = ev.comments(number)
    if ev.vetoed(pr, pr_comments, ev.timeline(number)):
        # Record a comment veto as a label too: the timeline keeps a `labeled` event even if
        # the label or the comment is later removed, so the veto stays permanent.
        if ev.veto_comment(pr_comments):
            ev.set_labels(pr, add=["veto"])
        ev.set_status(head, CONTEXT, "pending", "vetoed: only Kota clears a veto, by closing the PR")
        print(f"#{number}: vetoed")
        return
    approval = ev.panel_approval(pr, pr_comments, ev.diff_fingerprint(files))
    if approval is None:
        ev.set_status(head, CONTEXT, "pending", f"waiting for a panel approval of {head[:7]}")
        print(f"#{number}: no panel approval yet")
        return
    closes = ev.parse_time(approval["created_at"]) + WINDOW
    if ev.now() >= closes:
        ev.set_status(head, CONTEXT, "success", f"veto window closed {closes:%Y-%m-%d %H:%M} UTC with no veto")
        if not ev.DRY_RUN and not (still_unvetoed(number) and ev.still_approved(number)):
            ev.set_status(head, CONTEXT, "pending", "vetoed: only Kota clears a veto, by closing the PR")
            print(f"#{number}: vetoed while closing the window")
            return
        ev.set_labels(pr, remove=["veto-window:open"])
        print(f"#{number}: window closed, no veto")
    else:
        ev.set_status(head, CONTEXT, "pending", f"veto window open until {closes:%Y-%m-%d %H:%M} UTC")
        ev.set_labels(pr, add=["veto-window:open"])
        print(f"#{number}: window open until {closes:%Y-%m-%d %H:%M} UTC")


def main():
    number = os.environ.get("PR_NUMBER", "").strip()
    prs = [ev.get(f"/repos/{ev.REPO}/pulls/{number}")] if number else ev.open_prs()
    failures = 0
    for pr in prs:
        if pr.get("state") != "open":
            continue
        try:
            evaluate(pr)
        except Exception as e:
            failures += 1
            print(f"#{pr['number']}: error {e}", file=sys.stderr)
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
