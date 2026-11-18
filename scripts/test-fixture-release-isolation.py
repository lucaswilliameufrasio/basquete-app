#!/usr/bin/env python3
"""Check that the experimental fixture cannot be packaged as release."""

import pathlib
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parent.parent


def check_rejected(command, directory, expected):
    result = subprocess.run(command, cwd=directory, text=True, capture_output=True)
    output = result.stdout + result.stderr
    if result.returncode == 0 or expected not in output:
        print(output, file=sys.stderr)
        raise SystemExit(f"Expected release rejection containing: {expected}")
    print(f"PASS: {expected}")


check_rejected(
    ["cargo", "check", "-p", "basquete-app-ffi", "--release", "--features",
     "experimental-brazil-fixtures", "--offline"],
    root,
    "experimental-brazil-fixtures is restricted to debug builds",
)
check_rejected(
    ["./gradlew", ":app:assembleRelease", "-PbrazilFixtureTest=true", "--dry-run"],
    root / "android",
    "task 'assembleRelease' not found",
)
subprocess.run(["xcodegen", "generate", "--spec", "project-fixture.yml"],
               cwd=root / "ios", check=True)
check_rejected(
    ["xcodebuild", "build", "-project", "BasqueteAppFixture.xcodeproj",
     "-scheme", "BasqueteApp", "-configuration", "Release", "-sdk",
     "iphonesimulator", "-derivedDataPath", "../target/xcode-fixture-release-test",
     "ARCHS=arm64", "CODE_SIGNING_ALLOWED=NO"],
    root / "ios",
    "fixture project supports Debug only",
)
