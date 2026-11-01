"""Unit tests for the gate logic: `python3 .github/scripts/test_evolution_lib.py`."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
os.environ.setdefault("GH_TOKEN", "unused")
import evolution_lib as ev  # noqa: E402

HEAD = "abcdef1234567890abcdef1234567890abcdef12"


def f(name, status="modified", patch="", previous=None):
    d = {"filename": name, "status": status, "patch": patch}
    if previous:
        d["previous_filename"] = previous
    return d


class ClassifyTest(unittest.TestCase):
    def tier(self, *files):
        return ev.classify(list(files))[0]

    def test_ordinary_source_change_is_low(self):
        self.assertEqual(self.tier(f("src/main/java/onion/Colls.java"), f("CHANGELOG.md")), "low")

    def test_grammar_and_meta_are_high(self):
        self.assertEqual(self.tier(f("grammar/JJOnionParser.jj")), "high")
        self.assertEqual(self.tier(f("docs/evolution/RUNBOOK.md")), "high")

    def test_guarded_files_are_ultrahigh(self):
        for path in ["docs/evolution/GUARDRAILS.md", "docs/evolution/PRINCIPLES.md",
                     ".github/scripts/evolution_lib.py", ".github/workflows/evolution-risk-gate.yml",
                     ".github/workflows/release.yml", ".github/workflows/docs.yml",
                     ".github/workflows/new.yaml", ".github/actions/setup/action.yml",
                     ".github/CODEOWNERS", "LICENSE"]:
            self.assertEqual(self.tier(f(path)), "ultrahigh", path)

    def test_renaming_a_guarded_file_away_is_ultrahigh(self):
        self.assertEqual(self.tier(f("tools/x.py", "renamed", previous=".github/scripts/evolution_lib.py")),
                         "ultrahigh")

    def test_major_release_note_is_ultrahigh_but_minor_is_not(self):
        self.assertEqual(self.tier(f("docs/releases/1.0.0.md", "added")), "ultrahigh")
        self.assertEqual(self.tier(f("docs/releases/0.9.0.md", "added")), "low")

    def test_secrets_anywhere_are_ultrahigh(self):
        self.assertEqual(self.tier(f("scripts/deploy.sh", patch="+TOKEN=${{ secrets.X }}")), "ultrahigh")

    def test_build_publishing_lines_are_ultrahigh(self):
        self.assertEqual(self.tier(f("build.sbt", patch="+publishTo := Some(x)")), "ultrahigh")
        self.assertEqual(self.tier(f("build.sbt", patch="+libraryDependencies += y")), "high")
        self.assertEqual(self.tier(f("build.sbt", patch=None)), "ultrahigh")

    def test_deleting_or_moving_a_spec_is_ultrahigh_but_a_resource_is_high(self):
        self.assertEqual(self.tier(f("src/test/scala/onion/FooSpec.scala", "removed")), "ultrahigh")
        self.assertEqual(self.tier(f("attic/FooSpec.txt", "renamed",
                                     previous="src/test/scala/onion/FooSpec.scala")), "ultrahigh")
        self.assertEqual(self.tier(f("src/test/run/old.on", "removed")), "high")

    def test_too_many_files_is_ultrahigh(self):
        self.assertEqual(self.tier(*[f(f"src/a{i}.txt") for i in range(ev.FILES_API_LIMIT)]), "ultrahigh")


class TierTest(unittest.TestCase):
    def test_removing_the_label_never_lowers_the_tier(self):
        pr = {"labels": []}
        tier, *_ = ev.effective_tier(pr, [f("docs/evolution/GUARDRAILS.md")])
        self.assertEqual(tier, "ultrahigh")

    def test_a_higher_label_is_kept(self):
        pr = {"labels": [{"name": "risk:high"}]}
        self.assertEqual(ev.effective_tier(pr, [f("CHANGELOG.md")])[0], "high")


def comment(body, login="kmizu", assoc="OWNER", created="2026-10-21T00:00:00Z", updated=None):
    return {"body": body, "user": {"login": login}, "author_association": assoc,
            "created_at": created, "updated_at": updated or created}


class FingerprintTest(unittest.TestCase):
    """A verdict follows the PR's own change, not the head SHA: merging main must not void it."""
    base = [f("src/A.scala", patch="@@ -1,3 +1,3 @@\n ctx\n-old\n+new\n ctx2")]

    def test_context_lines_and_hunk_positions_do_not_matter(self):
        moved = [f("src/A.scala", patch="@@ -40,3 +52,3 @@ class Other\n different ctx\n-old\n+new\n more ctx")]
        self.assertEqual(ev.diff_fingerprint(self.base), ev.diff_fingerprint(moved))

    def test_a_changed_line_changes_it(self):
        other = [f("src/A.scala", patch="@@ -1,3 +1,3 @@\n ctx\n-old\n+newer\n ctx2")]
        self.assertNotEqual(ev.diff_fingerprint(self.base), ev.diff_fingerprint(other))

    def test_an_extra_file_or_a_rename_changes_it(self):
        self.assertNotEqual(ev.diff_fingerprint(self.base), ev.diff_fingerprint(self.base + [f("B.md", patch="+x")]))
        renamed = [f("src/C.scala", status="renamed", previous="src/A.scala", patch=self.base[0]["patch"])]
        self.assertNotEqual(ev.diff_fingerprint(self.base), ev.diff_fingerprint(renamed))

    def test_file_order_does_not_matter(self):
        a, b = f("a.md", patch="+1"), f("b.md", patch="+2")
        self.assertEqual(ev.diff_fingerprint([a, b]), ev.diff_fingerprint([b, a]))

    def test_a_file_without_a_patch_is_told_apart_by_its_blob(self):
        x = {"filename": "img.png", "status": "added", "sha": "1" * 40}
        y = {"filename": "img.png", "status": "added", "sha": "2" * 40}
        self.assertNotEqual(ev.diff_fingerprint([x]), ev.diff_fingerprint([y]))

    def test_too_many_files_cannot_be_judged(self):
        self.assertIsNone(ev.diff_fingerprint([f(f"f{i}", patch="+x") for i in range(ev.FILES_API_LIMIT)]))


class PanelDiffTest(unittest.TestCase):
    pr = {"head": {"sha": HEAD}, "labels": []}
    OLD = "1" * 40
    fp = ev.diff_fingerprint([f("a.md", patch="+1")])
    other_fp = ev.diff_fingerprint([f("a.md", patch="+2")])

    def mark(self, sha=None, fp=None, verdict="APPROVED"):
        d = f" diff={fp}" if fp else ""
        return comment(f"<!-- panel v1 sha={sha or self.OLD}{d} -->\nverdict: {verdict}")

    def test_an_older_head_with_the_same_diff_still_approves(self):
        c = self.mark(fp=self.fp)
        self.assertIs(ev.panel_approval(self.pr, [c], fingerprint=self.fp), c)

    def test_a_different_diff_does_not_approve(self):
        self.assertIsNone(ev.panel_approval(self.pr, [self.mark(fp=self.fp)], fingerprint=self.other_fp))

    def test_a_mark_without_a_diff_still_needs_the_exact_head(self):
        self.assertIsNone(ev.panel_approval(self.pr, [self.mark()], fingerprint=self.fp))

    def test_no_current_fingerprint_means_the_head_must_match(self):
        self.assertIsNone(ev.panel_approval(self.pr, [self.mark(fp=self.fp)], fingerprint=None))

    def test_a_block_on_the_same_diff_carries_over(self):
        approve = self.mark(sha=HEAD, fp=self.fp)
        block = self.mark(fp=self.fp, verdict="BLOCKED")
        self.assertIsNone(ev.panel_approval(self.pr, [approve, block], fingerprint=self.fp))


class PanelTest(unittest.TestCase):
    pr = {"head": {"sha": HEAD}, "labels": []}
    ok_body = f"<!-- panel v1 sha={HEAD} -->\n| lens | verdict |\nverdict: APPROVED"

    def test_a_trusted_unedited_full_sha_approval_counts(self):
        c = comment(self.ok_body)
        self.assertIs(ev.panel_approval(self.pr, [c]), c)

    def test_a_short_or_stale_sha_does_not_count(self):
        self.assertIsNone(ev.panel_approval(self.pr, [comment("<!-- panel v1 sha=abcdef1 -->\nverdict: APPROVED")]))
        self.assertIsNone(ev.panel_approval(self.pr, [comment(f"<!-- panel v1 sha={'0' * 40} -->\nverdict: APPROVED")]))

    def test_an_outsider_cannot_approve(self):
        self.assertIsNone(ev.panel_approval(self.pr, [comment(self.ok_body, login="stranger", assoc="NONE")]))
        self.assertIsNone(ev.panel_approval(self.pr, [comment(self.ok_body, assoc="CONTRIBUTOR")]))

    def test_an_edited_comment_does_not_count(self):
        edited = comment(self.ok_body, created="2026-10-01T00:00:00Z", updated="2026-10-21T00:00:00Z")
        self.assertIsNone(ev.panel_approval(self.pr, [edited]))

    def test_the_newest_verdict_wins(self):
        approve = comment(self.ok_body)
        block = comment(f"<!-- panel v1 sha={HEAD} -->\nverdict: BLOCKED")
        self.assertIsNone(ev.panel_approval(self.pr, [approve, block]))
        self.assertIs(ev.panel_approval(self.pr, [block, approve]), approve)

    def test_editing_a_newer_block_does_not_revive_an_older_approval(self):
        approve = comment(self.ok_body, created="2026-10-20T00:00:00Z")
        block = comment(f"<!-- panel v1 sha={HEAD} -->\nverdict: BLOCKED",
                        created="2026-10-21T00:00:00Z", updated="2026-10-21T01:00:00Z")
        self.assertIsNone(ev.panel_approval(self.pr, [approve, block]))

    def test_label_alone_is_ignored(self):
        self.assertIsNone(ev.panel_approval(dict(self.pr, labels=[{"name": "panel:approved"}]), []))


class VetoTest(unittest.TestCase):
    pr = {"head": {"sha": HEAD}, "labels": []}

    def test_veto_by_label_or_trusted_comment(self):
        self.assertTrue(ev.vetoed(dict(self.pr, labels=[{"name": "veto"}]), []))
        self.assertTrue(ev.vetoed(self.pr, [comment("I object.\n/veto")]))
        self.assertFalse(ev.vetoed(self.pr, [comment("no veto here, see /vetoes")]))

    def test_an_outsider_cannot_veto_by_comment(self):
        self.assertFalse(ev.vetoed(self.pr, [comment("/veto", login="stranger", assoc="NONE")]))

    def test_a_removed_veto_label_still_counts(self):
        events = [{"event": "labeled", "label": {"name": "veto"}}, {"event": "unlabeled", "label": {"name": "veto"}}]
        self.assertTrue(ev.vetoed(self.pr, [], events))


if __name__ == "__main__":
    unittest.main()
