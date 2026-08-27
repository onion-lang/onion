#!/usr/bin/env python3

import json
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("audit.py")


class MaintainabilityAuditTest(unittest.TestCase):
    def test_reports_tracked_sources_deterministically_without_machine_local_data(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp) / "fixture"
            output = repo / "out"
            (repo / "src/main/scala/example/b").mkdir(parents=True)
            (repo / "target/generated").mkdir(parents=True)
            self.run_git(repo, "init", "-q")
            self.run_git(repo, "config", "user.email", "audit@example.invalid")
            self.run_git(repo, "config", "user.name", "Audit Test")

            (repo / "src/main/scala/example/A.scala").write_text(
                """package example
                  |import example.b.B
                  |object A:
                  |  def choose(flag: Boolean): Int =
                  |    if flag then 1 else 2
                  |""".replace("                  |", ""),
                encoding="utf-8",
            )
            (repo / "src/main/scala/example/b/B.scala").write_text(
                "package example.b\nclass B\n", encoding="utf-8"
            )
            (repo / "target/generated/Generated.scala").write_text(
                "package generated\nclass Generated\n", encoding="utf-8"
            )
            self.run_git(repo, "add", "-f", ".")
            self.run_git(repo, "commit", "-qm", "initial")

            with (repo / "src/main/scala/example/A.scala").open("a", encoding="utf-8") as handle:
                handle.write("\nobject Added\n")
            self.run_git(repo, "add", ".")
            self.run_git(repo, "commit", "-qm", "change A")

            self.run_audit(repo, output)
            first_json = (output / "audit.json").read_text(encoding="utf-8")
            first_markdown = (output / "audit.md").read_text(encoding="utf-8")
            report = json.loads(first_json)

            self.assertEqual(report["schema_version"], 1)
            self.assertEqual(report["history"]["max_commits"], 20)
            self.assertEqual(
                [entry["path"] for entry in report["files"]],
                [
                    "src/main/scala/example/A.scala",
                    "src/main/scala/example/b/B.scala",
                ],
            )
            self.assertEqual(report["files"][0]["commit_count"], 2)
            self.assertEqual(report["files"][0]["fan_out"], 1)
            self.assertNotIn(str(repo), first_json)
            self.assertNotIn("generated/Generated.scala", first_json)
            self.assertNotIn("generated/Generated.scala", first_markdown)
            self.assertIn("| Path | LOC | Definitions | Branches | Fan-in | Fan-out | Commits | Churn |", first_markdown)

            self.run_audit(repo, output)
            self.assertEqual(first_json, (output / "audit.json").read_text(encoding="utf-8"))
            self.assertEqual(first_markdown, (output / "audit.md").read_text(encoding="utf-8"))

    def run_audit(self, repo: Path, output: Path) -> None:
        subprocess.run(
            [
                "python3",
                str(SCRIPT),
                "--repo",
                str(repo),
                "--output-dir",
                str(output),
                "--max-commits",
                "20",
            ],
            check=True,
            text=True,
            capture_output=True,
        )

    @staticmethod
    def run_git(repo: Path, *args: str) -> None:
        subprocess.run(
            ["git", *args], cwd=repo, check=True, text=True, capture_output=True
        )


if __name__ == "__main__":
    unittest.main()
