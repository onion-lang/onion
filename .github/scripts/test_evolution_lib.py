"""Unit tests for the gate logic: `python3 .github/scripts/test_evolution_lib.py`."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
os.environ.setdefault("GH_TOKEN", "unused")
import evolution_lib as ev  # noqa: E402


def f(name, status="modified", patch=""):
    return {"filename": name, "status": status, "patch": patch}


class ClassifyTest(unittest.TestCase):
    def tier(self, *files):
        return ev.classify(list(files))[0]

    def test_ordinary_source_change_is_low(self):
        self.assertEqual(self.tier(f("src/main/java/onion/Colls.java"), f("CHANGELOG.md")), "low")

    def test_grammar_and_meta_are_high(self):
        self.assertEqual(self.tier(f("grammar/JJOnionParser.jj")), "high")
        self.assertEqual(self.tier(f("docs/evolution/RUNBOOK.md")), "high")
        self.assertEqual(self.tier(f(".github/workflows/docs.yml", patch="+      - run: echo hi")), "high")

    def test_guardrails_principles_scripts_license_are_ultrahigh(self):
        for path in ["docs/evolution/GUARDRAILS.md", "docs/evolution/PRINCIPLES.md",
                     ".github/scripts/evolution_lib.py", ".github/workflows/evolution-risk-gate.yml",
                     ".github/workflows/release.yml", "LICENSE"]:
            self.assertEqual(self.tier(f(path)), "ultrahigh", path)

    def test_major_release_note_is_ultrahigh_but_minor_is_not(self):
        self.assertEqual(self.tier(f("docs/releases/1.0.0.md", "added")), "ultrahigh")
        self.assertEqual(self.tier(f("docs/releases/0.9.0.md", "added")), "low")

    def test_workflow_permissions_triggers_secrets_are_ultrahigh(self):
        for line in ["+permissions:", "-on:", "+  pull_request_target:", "+  TOKEN: ${{ secrets.X }}"]:
            self.assertEqual(self.tier(f(".github/workflows/docs.yml", patch=line)), "ultrahigh", line)

    def test_removing_a_test_step_is_ultrahigh(self):
        self.assertEqual(self.tier(f(".github/workflows/scala.yml", patch="-      run: sbt -v +test")), "ultrahigh")

    def test_build_publishing_lines_are_ultrahigh(self):
        self.assertEqual(self.tier(f("build.sbt", patch="+publishTo := Some(x)")), "ultrahigh")
        self.assertEqual(self.tier(f("build.sbt", patch="+libraryDependencies += y")), "high")

    def test_deleting_a_spec_is_ultrahigh(self):
        self.assertEqual(self.tier(f("src/test/scala/onion/FooSpec.scala", "removed")), "ultrahigh")


class PanelTest(unittest.TestCase):
    pr = {"head": {"sha": "abcdef1234567890"}, "labels": []}

    def c(self, body, at="2026-10-21T00:00:00Z"):
        return {"body": body, "created_at": at}

    def test_only_a_head_sha_approval_counts(self):
        ok = self.c("<!-- panel v1 sha=abcdef1 -->\n| lens | verdict |\nverdict: APPROVED")
        stale = self.c("<!-- panel v1 sha=0000000 -->\nverdict: APPROVED")
        self.assertIs(ev.panel_approval(self.pr, [ok]), ok)
        self.assertIsNone(ev.panel_approval(self.pr, [stale]))

    def test_the_newest_verdict_wins(self):
        approve = self.c("<!-- panel v1 sha=abcdef1 -->\nverdict: APPROVED")
        block = self.c("<!-- panel v1 sha=abcdef1 -->\nverdict: BLOCKED")
        self.assertIsNone(ev.panel_approval(self.pr, [approve, block]))
        self.assertIs(ev.panel_approval(self.pr, [block, approve]), approve)

    def test_label_alone_is_ignored(self):
        pr = dict(self.pr, labels=[{"name": "panel:approved"}])
        self.assertIsNone(ev.panel_approval(pr, []))

    def test_veto_by_label_or_comment(self):
        self.assertTrue(ev.vetoed(dict(self.pr, labels=[{"name": "veto"}]), []))
        self.assertTrue(ev.vetoed(self.pr, [self.c("I object.\n/veto")]))
        self.assertFalse(ev.vetoed(self.pr, [self.c("no veto here, see /vetoes")]))


if __name__ == "__main__":
    unittest.main()
