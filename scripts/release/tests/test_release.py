"""Focused checks for release identity, packaging, and retry behavior."""

import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import release  # noqa: E402


class ReleaseFixture(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.git("init", "-q", "--initial-branch=main")
        self.git("config", "user.name", "Release Test")
        self.git("config", "user.email", "release-test@example.invalid")
        (self.root / "README.md").write_text("fixture\n", encoding="utf-8")
        (self.root / "LICENSE").write_text("Apache-2.0 fixture\n", encoding="utf-8")
        wrapper = self.root / "gradle/wrapper"
        wrapper.mkdir(parents=True)
        (wrapper / "gradle-wrapper.properties").write_text(
            "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.5.1-bin.zip\n"
            "distributionSha256Sum=" + "a" * 64 + "\n",
            encoding="utf-8",
        )
        (wrapper / "gradle-wrapper.jar").write_bytes(b"wrapper fixture")
        self.git("add", ".")
        self.git("commit", "-qm", "Initial source")
        self.commit = self.git("rev-parse", "HEAD")
        self.git("update-ref", "refs/remotes/origin/main", self.commit)
        self.git("tag", "-a", "v0.1.0-alpha.1", "-m", "test tag")
        self.version = "0.1.0-alpha.1"
        self.create_build_inputs()

    def git(self, *args):
        return subprocess.run(
            ["git", *args], cwd=self.root, text=True, capture_output=True, check=True
        ).stdout.strip()

    def create_build_inputs(self):
        jar = self.root / "stackframe-fabric/build/libs" / f"stackframe-fabric-{self.version}.jar"
        jar.parent.mkdir(parents=True)
        report = self.root / "stackframe-fabric/build/generated/supply-chain/dependencies.tsv"
        report.parent.mkdir(parents=True)
        report.write_text(
            "component\tbundled_path\tspdx_license\tlicense_path\n"
            "fixture\tMETA-INF/jars/fixture.jar\tApache-2.0\tLICENSE\n",
            encoding="utf-8",
        )
        runtime = self.root / "build/release/runtime-dependencies.txt"
        runtime.parent.mkdir(parents=True)
        runtime.write_text("runtimeClasspath - fixture\n", encoding="utf-8")
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr(
                "META-INF/MANIFEST.MF",
                "Manifest-Version: 1.0\r\n"
                f"Implementation-Version: {self.version}\r\n"
                f"Build-Revision: {self.commit}\r\n\r\n",
            )
            archive.writestr("fabric.mod.json", json.dumps({"version": self.version}))
            archive.writestr("META-INF/stackframe/dependencies.tsv", report.read_bytes())


class ValidateTagTest(ReleaseFixture):
    def test_annotated_tag_on_main_is_accepted(self):
        result = release.require_tag(self.root, "v0.1.0-alpha.1")
        self.assertEqual((result.version, result.commit), (self.version, self.commit))

    def test_lightweight_tag_is_rejected(self):
        self.git("tag", "v0.1.0-alpha.2")
        with self.assertRaisesRegex(ValueError, "annotated"):
            release.require_tag(self.root, "v0.1.0-alpha.2")

    def test_dirty_tagged_source_is_rejected(self):
        (self.root / "README.md").write_text("modified after checkout\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "tracked source files differ"):
            release.require_tag(self.root, "v0.1.0-alpha.1")

    def test_bad_versions_are_rejected(self):
        for name in ("v01.0.0", "v0.1.0-dev", "v0.1.0-alpha.01", "v0.1.0;echo hacked"):
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "release tag"):
                release.require_tag(self.root, name)

    def test_unmerged_commit_is_rejected(self):
        (self.root / "README.md").write_text("new\n", encoding="utf-8")
        self.git("add", "README.md")
        self.git("commit", "-qm", "Later source")
        self.git("tag", "-a", "v0.1.0-alpha.2", "-m", "test tag")
        with self.assertRaisesRegex(ValueError, "origin/main"):
            release.require_tag(self.root, "v0.1.0-alpha.2")

    def test_changelog_uses_nearest_previous_annotated_tag(self):
        (self.root / "README.md").write_text("next release\n", encoding="utf-8")
        self.git("add", "README.md")
        self.git("commit", "-qm", "Add next release behavior")
        next_commit = self.git("rev-parse", "HEAD")
        self.git("update-ref", "refs/remotes/origin/main", next_commit)
        self.git("tag", "-a", "v0.1.0-alpha.2", "-m", "next tag")
        changelog = release.draft_changelog(
            self.root, release.require_tag(self.root, "v0.1.0-alpha.2")
        )
        self.assertIn("Previous tag: `v0.1.0-alpha.1`", changelog)
        self.assertIn("Add next release behavior", changelog)
        self.assertNotIn("Initial source (", changelog)


class AssembleTest(ReleaseFixture):
    def test_packages_checked_binary_and_provenance(self):
        output = self.root / "staged"
        with patch.object(release, "java_version", return_value="openjdk version 25-test"):
            release.assemble(
                self.root, "v0.1.0-alpha.1", self.version, self.commit, output
            )
        provenance = json.loads((output / "provenance.json").read_text(encoding="utf-8"))
        self.assertEqual(provenance["source_commit"], self.commit)
        self.assertEqual(provenance["version"], self.version)
        self.assertEqual(
            provenance["artifact_sha256"],
            release.sha256(output / f"stackframe-fabric-{self.version}.jar"),
        )
        for line in (output / "SHA256SUMS").read_text(encoding="ascii").splitlines():
            digest, filename = line.split("  ", 1)
            self.assertEqual(digest, release.sha256(output / filename))
        self.assertIn("Draft changelog", (output / "CHANGELOG.md").read_text())
        with self.assertRaisesRegex(ValueError, "refusing to overwrite"):
            release.assemble(self.root, "v0.1.0-alpha.1", self.version, self.commit, output)

    def test_rejects_version_and_binary_revision_mismatch(self):
        with self.assertRaisesRegex(ValueError, "differs from the annotated tag"):
            release.assemble(
                self.root, "v0.1.0-alpha.1", "0.1.0-alpha.2", self.commit, self.root / "staged"
            )
        jar = self.root / "stackframe-fabric/build/libs" / f"stackframe-fabric-{self.version}.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", "Build-Revision: wrong\n")
            archive.writestr("fabric.mod.json", json.dumps({"version": self.version}))
            archive.writestr("META-INF/stackframe/dependencies.tsv", b"wrong")
        with self.assertRaisesRegex(ValueError, "wrong release version"):
            release.assemble(
                self.root, "v0.1.0-alpha.1", self.version, self.commit, self.root / "staged"
            )

    def test_rejects_invalid_dependency_report_without_partial_output(self):
        runtime = self.root / "build/release/runtime-dependencies.txt"
        runtime.write_text("BUILD SUCCESSFUL\n", encoding="utf-8")
        output = self.root / "staged"
        with self.assertRaisesRegex(ValueError, "runtimeClasspath inventory"):
            release.assemble(self.root, "v0.1.0-alpha.1", self.version, self.commit, output)
        self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
