#!/usr/bin/env python3
"""Validate a release tag and assemble reviewable artifacts from its build."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path


RELEASE_TAG = re.compile(
    r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"
    r"(?:-(?:alpha|beta|rc)\.(0|[1-9][0-9]*))?\Z"
)
COMMIT = re.compile(r"[0-9a-f]{40}\Z")
REPOSITORY_ROOT = Path(__file__).resolve().parents[2]


@dataclass(frozen=True)
class ValidatedTag:
    name: str
    version: str
    commit: str


def git(root: Path, *args: str, check: bool = True) -> str:
    result = subprocess.run(
        ["git", *args], cwd=root, text=True, capture_output=True, check=False
    )
    if check and result.returncode:
        raise ValueError(f"git {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout.strip()


def require_tag(root: Path, name: str) -> ValidatedTag:
    match = RELEASE_TAG.fullmatch(name)
    if match is None:
        raise ValueError("release tag must be vMAJOR.MINOR.PATCH[-alpha|beta|rc.N]")
    ref = f"refs/tags/{name}"
    if git(root, "cat-file", "-t", ref) != "tag":
        raise ValueError("release tag must be annotated (lightweight tags are rejected)")
    commit = git(root, "rev-parse", f"{ref}^{{commit}}")
    if not COMMIT.fullmatch(commit):
        raise ValueError("release tag did not resolve to a full Git commit SHA")
    if git(root, "rev-parse", "HEAD") != commit:
        raise ValueError("checked-out source does not match the release tag")
    if git(root, "status", "--porcelain", "--untracked-files=no"):
        raise ValueError("tracked source files differ from the release tag")
    main = git(root, "rev-parse", "--verify", "refs/remotes/origin/main")
    if subprocess.run(
        ["git", "merge-base", "--is-ancestor", commit, main],
        cwd=root,
        check=False,
    ).returncode:
        raise ValueError("release commit is not reachable from origin/main")
    return ValidatedTag(name, name[1:], commit)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def manifest_attributes(data: bytes) -> dict[str, str]:
    attributes: dict[str, str] = {}
    last_name = ""
    for line in data.decode("utf-8").splitlines():
        if line.startswith(" ") and last_name:
            attributes[last_name] += line[1:]
        elif ": " in line:
            last_name, value = line.split(": ", 1)
            attributes[last_name] = value
    return attributes


def verify_fabric_jar(path: Path, version: str, commit: str, report: Path) -> None:
    with zipfile.ZipFile(path) as jar:
        manifest = manifest_attributes(jar.read("META-INF/MANIFEST.MF"))
        if manifest.get("Implementation-Version") != version:
            raise ValueError("Fabric JAR manifest has the wrong release version")
        if manifest.get("Build-Revision") != commit:
            raise ValueError("Fabric JAR manifest has the wrong source revision")
        mod_version = json.loads(jar.read("fabric.mod.json"))["version"]
        if mod_version != version:
            raise ValueError("Fabric mod metadata has the wrong release version")
        embedded = jar.read("META-INF/stackframe/dependencies.tsv")
        if embedded != report.read_bytes():
            raise ValueError("Fabric JAR and separate bundled-dependency inventory differ")


def previous_release_tag(root: Path, current: ValidatedTag) -> str | None:
    result = subprocess.run(
        [
            "git", "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*",
            f"{current.commit}^",
        ],
        cwd=root,
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode:
        return None
    name = result.stdout.strip()
    if not RELEASE_TAG.fullmatch(name) or git(root, "cat-file", "-t", f"refs/tags/{name}") != "tag":
        raise ValueError(f"previous release tag {name!r} is not a valid annotated version tag")
    return name


def draft_changelog(root: Path, current: ValidatedTag) -> str:
    previous = previous_release_tag(root, current)
    revision_range = f"{previous}..{current.commit}" if previous else current.commit
    subjects = git(root, "log", "--no-merges", "--format=%h%x09%s", revision_range)
    entries = []
    for row in subjects.splitlines():
        short_sha, _, subject = row.partition("\t")
        subject = subject.replace("`", "'").replace("\r", " ").replace("\n", " ")
        entries.append(f"- {subject} (`{short_sha}`)")
    title = f"# Draft changelog for {current.name}\n\n"
    title += f"Source: `{current.commit}`. Previous tag: `{previous or 'none'}`.\n\n"
    title += "Generated from commit subjects. Maintainers must review issues, compatibility evidence, migrations, privacy changes, and known limitations before publication.\n\n"
    return title + "## Changes\n\n" + ("\n".join(entries) or "- No commits recorded.") + "\n"


def wrapper_identity(root: Path) -> dict[str, str]:
    props = (root / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
    values = dict(
        line.split("=", 1) for line in props.splitlines() if "=" in line and not line.startswith("#")
    )
    return {
        "distribution_url": values["distributionUrl"],
        "distribution_sha256": values["distributionSha256Sum"],
        "wrapper_jar_sha256": sha256(root / "gradle/wrapper/gradle-wrapper.jar"),
    }


def java_version() -> str:
    result = subprocess.run(["java", "-version"], text=True, capture_output=True, check=True)
    return result.stderr.splitlines()[0]


def assemble(root: Path, name: str, version: str, commit: str, output: Path) -> None:
    tag = require_tag(root, name)
    if version != tag.version or commit != tag.commit:
        raise ValueError("release version or source revision differs from the annotated tag")
    jar_path = root / "stackframe-fabric/build/libs" / f"stackframe-fabric-{version}.jar"
    bundled_report = root / "stackframe-fabric/build/generated/supply-chain/dependencies.tsv"
    runtime_report = root / "build/release/runtime-dependencies.txt"
    license_file = root / "LICENSE"
    for path in (jar_path, bundled_report, runtime_report, license_file):
        if not path.is_file() or not path.stat().st_size:
            raise ValueError(f"required release input missing or empty: {path}")
    if "runtimeClasspath -" not in runtime_report.read_text(encoding="utf-8"):
        raise ValueError("runtime dependency report is not a Gradle runtimeClasspath inventory")
    verify_fabric_jar(jar_path, version, commit, bundled_report)
    if output.exists():
        raise ValueError(f"release output already exists; refusing to overwrite: {output}")
    output.parent.mkdir(parents=True, exist_ok=True)
    # Move into place only after every evidence file is complete. A failed
    # assembly leaves no half-populated bundle for a later upload step.
    with tempfile.TemporaryDirectory(prefix=".stackframe-release-", dir=output.parent) as temp:
        staging = Path(temp)
        files = {
            jar_path.name: jar_path,
            "LICENSE": license_file,
            "embedded-dependencies.tsv": bundled_report,
            "runtime-dependencies.txt": runtime_report,
        }
        for dest_name, source in files.items():
            shutil.copyfile(source, staging / dest_name)
        (staging / "CHANGELOG.md").write_text(draft_changelog(root, tag), encoding="utf-8")
        provenance = {
            "schema": "stackframe-release-provenance-v1",
            "tag": name,
            "version": version,
            "source_commit": commit,
            "source_repository": os.environ.get("GITHUB_REPOSITORY", "MinecraftProt/Stackframe"),
            "build_run": (
                f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/"
                f"{os.environ['GITHUB_RUN_ID']}/attempts/{os.environ['GITHUB_RUN_ATTEMPT']}"
                if all(k in os.environ for k in ("GITHUB_REPOSITORY", "GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT"))
                else "local validation"
            ),
            "java": java_version(),
            "wrapper": wrapper_identity(root),
            "build_command": "./gradlew --no-daemon --stacktrace --dependency-verification=strict -PreleaseVersion=VERSION -PreleaseSourceRevision=COMMIT clean build verifyModuleBoundaries",
            "artifact_sha256": sha256(staging / jar_path.name),
            "dependency_inventory": {
                "embedded": "embedded-dependencies.tsv (reviewed SPDX licenses and bundled paths)",
                "runtime": "runtime-dependencies.txt (resolved Gradle tree; platform-provided licenses require separate review)",
            },
            "compatibility_matrix": f"https://github.com/MinecraftProt/Stackframe/blob/{commit}/docs/COMPATIBILITY.md",
        }
        (staging / "provenance.json").write_text(
            json.dumps(provenance, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        checksums = [
            f"{sha256(path)}  {path.name}"
            for path in sorted(staging.iterdir())
            if path.is_file() and path.name != "CHANGELOG.md"
        ]
        (staging / "SHA256SUMS").write_text("\n".join(checksums) + "\n", encoding="ascii")
        if output.exists():
            raise ValueError(f"release output already exists; refusing to overwrite: {output}")
        staging.rename(output)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=REPOSITORY_ROOT)
    subcommands = parser.add_subparsers(dest="command", required=True)
    validate = subcommands.add_parser("validate")
    validate.add_argument("--tag", required=True)
    validate.add_argument("--github-output", type=Path)
    package = subcommands.add_parser("assemble")
    package.add_argument("--tag", required=True)
    package.add_argument("--version", required=True)
    package.add_argument("--commit", required=True)
    package.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        if args.command == "validate":
            tag = require_tag(args.root, args.tag)
            values = {
                "version": tag.version,
                "commit": tag.commit,
                "bundle_name": (
                    f"stackframe-release-{tag.name}-{tag.commit[:12]}-"
                    f"{os.environ.get('GITHUB_RUN_ID', 'local')}-"
                    f"{os.environ.get('GITHUB_RUN_ATTEMPT', '1')}"
                ),
            }
            if args.github_output:
                with args.github_output.open("a", encoding="utf-8") as file:
                    for key, value in values.items():
                        file.write(f"{key}={value}\n")
            print(json.dumps(values, sort_keys=True))
        else:
            output = args.output or args.root / "build/release/staging"
            assemble(args.root, args.tag, args.version, args.commit, output)
            print(f"Release evidence staged in {output}")
        return 0
    except (ValueError, OSError, KeyError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        print(f"Release preparation failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
