"""Compile the optional Maven benchmark profile and launch its Java entry points."""

from __future__ import annotations

import os
import shutil
import subprocess
from functools import lru_cache
from pathlib import Path


@lru_cache(maxsize=None)
def benchmark_classpath(repository_root: Path) -> str:
    root = repository_root.resolve()
    wrapper = root / "mvnw"
    if not wrapper.is_file():
        raise RuntimeError(f"AIlex Maven wrapper is missing: {wrapper}")
    subprocess.run(
        [str(wrapper), "-B", "-ntp", "-q", "-Pbenchmark", "-DskipTests", "test-compile"],
        cwd=root,
        check=True,
    )
    dependencies = (root / "target/benchmark-classpath.txt").read_text(encoding="utf-8").strip()
    return os.pathsep.join((str(root / "target/test-classes"), str(root / "target/classes"), dependencies))


def java_command(repository_root: Path, main_class: str, *args: str) -> list[str]:
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin/java") if java_home else shutil.which("java")
    if not java:
        raise RuntimeError("Java 25 is required for the AIlex benchmark")
    return [java, "-cp", benchmark_classpath(repository_root), main_class, *args]
