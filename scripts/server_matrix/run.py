"""Run the pinned Fabric 26.2 dedicated-server failure matrix.

Build the Stackframe and fixture JARs first. The actual server invocations use
Gradle --offline, so no scenario resolves or downloads a mutable dependency.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import queue
import re
import shutil
import signal
import subprocess
import sys
import threading
import time
import tomllib
import zipfile


ROOT = Path(__file__).resolve().parents[2]
FABRIC = ROOT / "stackframe-fabric"
OUTPUT = FABRIC / "build" / "server-matrix"
SCENARIOS = (
    "clean-start",
    "prelaunch-after-hook",
    "startup",
    "world",
    "registry",
    "datapack",
    "runtime",
    "mixin",
    "shutdown",
)
BOOTSTRAP = "[Stackframe] Loaded Stackframe dedicated-server bootstrap."
READY = re.compile(r"\bDone \([\d.]+s\)! For help, type")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def pins_and_artifacts() -> tuple[dict[str, str], Path, Path]:
    with (ROOT / "gradle" / "libs.versions.toml").open("rb") as source:
        pins = tomllib.load(source)["versions"]
    fixture = FABRIC / "build" / "libs" / "stackframe-server-matrix-fixture-0.1.0.jar"
    stackframe = FABRIC / "build" / "libs" / "stackframe-fabric-0.1.0-SNAPSHOT.jar"
    for artifact in (fixture, stackframe):
        if not artifact.is_file():
            raise RuntimeError(f"Build the server matrix artifacts first: {artifact}")
    with zipfile.ZipFile(fixture) as archive:
        metadata = json.loads(archive.read("fabric.mod.json"))
    if metadata["depends"]["minecraft"] != pins["minecraft"]:
        raise RuntimeError("Fixture Minecraft pin differs from the version catalog")
    if metadata["depends"]["fabricloader"] != pins["fabric-loader"]:
        raise RuntimeError("Fixture Loader pin differs from the version catalog")
    if metadata["depends"]["stackframe"] != "*":
        raise RuntimeError("Fixture must depend on the Stackframe mod")
    return pins, fixture, stackframe


def terminate_tree(process: subprocess.Popen[str]) -> None:
    if process.poll() is not None:
        return
    if os.name == "nt":
        subprocess.run(
            ["taskkill", "/PID", str(process.pid), "/T", "/F"],
            capture_output=True, check=False, timeout=10,
        )
    else:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=5)
            return
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
    process.wait(timeout=10)


def validate(
    scenario: str, console: str, run_dir: Path, returncode: int,
    pins: dict[str, str],
) -> tuple[list[str], str | None]:
    errors: list[str] = []
    if f"Loading Minecraft {pins['minecraft']} with Fabric Loader {pins['fabric-loader']}" not in console:
        errors.append("exact Minecraft/Loader baseline was not observed")
    if "stackframe_server_matrix_fixture 0.1.0" not in console:
        errors.append("pinned failure fixture was not loaded")
    if BOOTSTRAP not in console:
        errors.append("Stackframe bootstrap was not observed")
    if scenario != "mixin" and not READY.search(console):
        errors.append("dedicated server did not reach ready state")
    if scenario != "mixin" and returncode != 0:
        errors.append(f"server exited with code {returncode}, expected a clean stop")

    traces = list((run_dir / "logs" / "stackframe-traces").glob("*.trace"))
    if scenario == "clean-start":
        if "SF_MATRIX_DETAIL_" in console:
            errors.append("clean control unexpectedly emitted a fixture failure")
        return errors, None

    detail = f"SF_MATRIX_DETAIL_{scenario}"
    matching = [path for path in traces if detail in path.read_text(
        encoding="utf-8", errors="replace")]
    if len(matching) != 1:
        errors.append(f"expected one complete fixture trace, found {len(matching)}")
        return errors, None
    trace_id = matching[0].stem
    trace = matching[0].read_text(encoding="utf-8", errors="replace")
    if "IllegalStateException: " + detail not in trace:
        errors.append("raw trace lost the exception type or message")
    if "FailureFixture" not in trace and "ServerPhaseMixin" not in trace:
        errors.append("raw trace lost the fixture stack frame")
    phase_frame = {
        "prelaunch-after-hook": "FailureFixture.onPreLaunch",
        "startup": "FailureFixture.onInitialize",
        "world": "MinecraftServer.loadLevel",
        "registry": "MinecraftServer.registryAccess",
        "datapack": "MinecraftServer.reloadResources",
        "runtime": "MinecraftServer.tickServer",
        "mixin": "MinecraftServer.tickServer",
        "shutdown": "MinecraftServer.stopServer",
    }[scenario]
    if phase_frame not in trace:
        errors.append(f"raw trace did not pass through {phase_frame}")
    diagnostics = re.findall(
        r"error\[SF0001\]:[^\n]*\ntrace:[^\n]*\n[^\n]*correlation ([A-Z0-9]+)",
        console,
    )
    if diagnostics.count(trace_id) != 1 or console.count(f"correlation {trace_id}") != 1:
        errors.append("fixture produced zero or repeated SF0001 diagnostics")
    if scenario == "mixin":
        if detail not in console or "Encountered an unexpected exception" not in console:
            errors.append("original Mixin crash was not visible in the server log")
        crash_reports = list((run_dir / "crash-reports").glob("*.txt"))
        if not any(detail in report.read_text(
                encoding="utf-8", errors="replace") for report in crash_reports):
            errors.append("vanilla crash report did not preserve the Mixin failure")
    else:
        if scenario == "datapack" and "Reloading!" not in console:
            errors.append("datapack reload did not reach the server")
        if console.count(f"SF_MATRIX_ORIGINAL_{scenario}") != 1:
            errors.append("first original Log4j error is missing or repeated")
        if console.count(f"SF_MATRIX_REPEAT_{scenario}") != 1:
            errors.append("second original Log4j error is missing or repeated")
        if console.count(f"Failure {trace_id} was observed 1 more time") != 1:
            errors.append("correlation repeat summary is missing or inaccurate")
    return errors, trace_id


def run_one(
    scenario: str, timeout: int, pins: dict[str, str], fixture: Path,
    stackframe: Path,
) -> bool:
    scenario_dir = OUTPUT / scenario
    if scenario_dir.exists():
        raise RuntimeError(f"Refusing to mix evidence with an existing run: {scenario_dir}")
    run_dir = scenario_dir / "run"
    mods = run_dir / "mods"
    mods.mkdir(parents=True)
    shutil.copyfile(fixture, mods / fixture.name)
    (run_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (run_dir / "server.properties").write_text(
        "online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n"
        "max-players=1\nenable-rcon=false\n", encoding="utf-8",
    )
    gradle = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    args = [str(gradle), "--no-daemon", "--offline",
            "--dependency-verification=strict", ":stackframe-fabric:runServer",
            "--console=plain", f"-PserverMatrixRunDir={run_dir}"]
    environment = os.environ.copy()
    environment["STACKFRAME_MATRIX_SCENARIO"] = scenario
    creationflags = subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0
    lines: queue.Queue[str] = queue.Queue()
    reader_done = threading.Event()
    started = time.monotonic()
    timed_out = False
    ready = False
    emitted = False
    stop_sent = False
    reload_sent = False
    console_path = scenario_dir / "console.log"
    print(f"[{scenario}] starting pinned dedicated server", flush=True)
    with console_path.open("w", encoding="utf-8") as console_file:
        process = subprocess.Popen(
            args, cwd=ROOT, env=environment, stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
            encoding="utf-8", errors="replace", bufsize=1,
            start_new_session=os.name != "nt", creationflags=creationflags,
        )

        def collect() -> None:
            assert process.stdout is not None
            for line in process.stdout:
                console_file.write(line)
                console_file.flush()
                lines.put(line)
            reader_done.set()

        reader = threading.Thread(target=collect, daemon=True)
        reader.start()
        while True:
            if time.monotonic() - started > timeout:
                timed_out = True
                terminate_tree(process)
                break
            if process.poll() is not None and reader_done.is_set() and lines.empty():
                break
            try:
                line = lines.get(timeout=0.2)
            except queue.Empty:
                continue
            ready |= bool(READY.search(line))
            emitted |= f"SF_MATRIX_EMITTED_{scenario}" in line
            if scenario == "datapack" and ready and not reload_sent:
                assert process.stdin is not None
                process.stdin.write("reload\n")
                process.stdin.flush()
                reload_sent = True
            elif scenario != "mixin" and ready and not stop_sent and (
                    scenario in ("clean-start", "shutdown") or emitted):
                assert process.stdin is not None
                process.stdin.write("stop\n")
                process.stdin.flush()
                stop_sent = True
        reader.join(timeout=5)
        if process.poll() is None:
            terminate_tree(process)
    elapsed = round(time.monotonic() - started, 2)
    console = console_path.read_text(encoding="utf-8", errors="replace")
    errors, trace_id = validate(scenario, console, run_dir, process.returncode, pins)
    if timed_out:
        errors.insert(0, f"server exceeded the {timeout}-second process deadline")
    if scenario != "mixin" and not stop_sent:
        errors.append("runner never sent a graceful stop command")
    if scenario == "datapack" and not reload_sent:
        errors.append("runner never sent a datapack reload command")
    result = {
        "scenario": scenario,
        "passed": not errors,
        "errors": errors,
        "elapsed_seconds": elapsed,
        "exit_code": process.returncode,
        "ready": ready,
        "trace_id": trace_id,
        "pins": {key: pins[key] for key in ("minecraft", "fabric-loader", "java")},
        "fixture_sha256": sha256(fixture),
        "built_stackframe_artifact_sha256": sha256(stackframe),
        "execution_mode": "Fabric Loom runServer development classpath",
        "source_revision": subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True,
            check=True, text=True, timeout=5,
        ).stdout.strip(),
        "java_runtime": subprocess.run(
            [str(Path(os.environ["JAVA_HOME"]) / "bin" /
                 ("java.exe" if os.name == "nt" else "java")), "-version"],
            capture_output=True, check=True, text=True, timeout=5,
        ).stderr.strip(),
        "host": {"system": platform.system(), "release": platform.release(),
                 "machine": platform.machine()},
    }
    (scenario_dir / "result.json").write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8",
    )
    print(f"[{scenario}] {'PASS' if not errors else 'FAIL'} in {elapsed}s"
          + (f": {'; '.join(errors)}" if errors else ""), flush=True)
    return not errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scenario", action="append", choices=SCENARIOS,
                        help="Run one or more phases; default: all")
    parser.add_argument("--timeout-seconds", type=int, default=150,
                        help="Per-server hard deadline (default: 150)")
    args = parser.parse_args()
    if not 30 <= args.timeout_seconds <= 600:
        parser.error("timeout-seconds must be between 30 and 600")
    pins, fixture, stackframe = pins_and_artifacts()
    if sys.version_info < (3, 11):
        raise RuntimeError("Python 3.11 or newer is required for tomllib")
    success = True
    for scenario in args.scenario or SCENARIOS:
        try:
            success = run_one(scenario, args.timeout_seconds, pins,
                              fixture, stackframe) and success
        except (OSError, RuntimeError, subprocess.SubprocessError) as failure:
            print(f"[{scenario}] harness failure: {failure}", file=sys.stderr,
                  flush=True)
            success = False
    return 0 if success else 1


if __name__ == "__main__":
    sys.exit(main())
