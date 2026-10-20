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
