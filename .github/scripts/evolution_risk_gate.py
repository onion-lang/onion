"""The `evolution/risk-gate` check (docs/evolution/GUARDRAILS.md, "Risk tiers").

For each PR (PR_NUMBER, or every open PR):
1. compute the minimum tier from the diff and raise the `risk:*` label to it (never lower);
2. post `evolution/risk-gate`: success for `risk:low`; for `risk:high`/`risk:ultrahigh`,
   success only when a panel verdict comment for the current head approves.
"""
import os
import sys

import evolution_lib as ev


def evaluate(pr):
    number, head = pr["number"], pr["head"]["sha"]
    tier, computed, current, reasons = ev.effective_tier(pr, ev.pr_files(number))

    ev.set_labels(pr, add=[f"risk:{tier}"], remove=[f"risk:{t}" for t in ev.TIERS if t != tier])
    print(f"#{number}: tier={tier} (computed {computed}, labelled {current}); " + "; ".join(reasons[:5]))

    if tier == "low":
        ev.set_status(head, "evolution/risk-gate", "success", "risk:low: green checks are enough")
        return
    approval = ev.panel_approval(pr, ev.comments(number))
    if approval:
        ev.set_labels(pr, add=["panel:approved"], remove=["panel:changes"])
        ev.set_status(head, "evolution/risk-gate", "success", f"risk:{tier}: panel approved {head[:7]}")
    else:
        ev.set_labels(pr, remove=["panel:approved"])
        ev.set_status(head, "evolution/risk-gate", "pending",
                      f"risk:{tier}: waiting for a panel verdict on {head[:7]} (RUNBOOK §4)")


def main():
    number = os.environ.get("PR_NUMBER", "").strip()
    prs = [ev.get(f"/repos/{ev.REPO}/pulls/{number}")] if number else ev.open_prs()
    failures = 0
    for pr in prs:
        if pr.get("state") != "open":
            continue
        try:
            evaluate(pr)
        except Exception as e:  # one bad PR must not stop the sweep
            failures += 1
            print(f"#{pr['number']}: error {e}", file=sys.stderr)
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
