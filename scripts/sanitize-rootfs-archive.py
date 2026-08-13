#!/usr/bin/env python3
"""Remove build-only network settings from a release rootfs without extracting it."""

from __future__ import annotations

import argparse
import hashlib
import io
import os
import tarfile
from pathlib import Path


PROXY_PATHS = {
    "etc/apt/apt.conf.d/99-sai-build-proxy",
}
SOURCES_PATH = "etc/apt/sources.list"
RUNTIME_PATH_MARKER = "/files/runtime/debian/"
DPKG_STATUS = "var/lib/dpkg/status"
DPKG_STATUS_OLD = "var/lib/dpkg/status-old"
DPKG_LINK_PREFIX = "var/lib/dpkg/.l2s.status"


def normalized(name: str) -> str:
    return name.removeprefix("./").rstrip("/")


def sanitize(source: Path, destination: Path) -> None:
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    temporary.unlink(missing_ok=True)
    with tarfile.open(source, "r:xz") as archive:
        status_member = next(member for member in archive if normalized(member.name) == DPKG_STATUS)
        status_stream = archive.extractfile(status_member)
        if status_stream is None:
            raise SystemExit("rootfs has no readable dpkg status database")
        dpkg_status = status_stream.read()

    removed: list[str] = []
    rebased_links = 0
    with tarfile.open(source, "r:xz") as archive, tarfile.open(
        temporary, "w:xz", preset=6
    ) as output:
        for member in archive:
            path = normalized(member.name)
            if path in PROXY_PATHS:
                removed.append(path)
                continue
            if path.startswith(DPKG_LINK_PREFIX):
                removed.append(path)
                continue
            if member.issym() and RUNTIME_PATH_MARKER in member.linkname:
                target = member.linkname.split(RUNTIME_PATH_MARKER, 1)[1].lstrip("/")
                parent = str(Path(path).parent).replace("\\", "/")
                member.linkname = os.path.relpath(target, parent).replace("\\", "/")
                rebased_links += 1
            payload = archive.extractfile(member) if member.isfile() else None
            if path == DPKG_STATUS_OLD:
                member.type = tarfile.REGTYPE
                member.linkname = ""
                member.mode = 0o644
                member.size = len(dpkg_status)
                payload = io.BytesIO(dpkg_status)
            if path == SOURCES_PATH and payload is not None:
                content = payload.read().replace(
                    b"http://deb.debian.org", b"https://deb.debian.org"
                ).replace(
                    b"http://security.debian.org", b"https://security.debian.org"
                )
                member.size = len(content)
                payload = io.BytesIO(content)
            output.addfile(member, payload)
    if not removed and not rebased_links:
        temporary.unlink(missing_ok=True)
        raise SystemExit("archive contained neither build proxy nor host-bound runtime links")
    os.replace(temporary, destination)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    sanitize(args.source, args.destination)
    print(f"sha256={sha256(args.destination)}")
    print(f"bytes={args.destination.stat().st_size}")


if __name__ == "__main__":
    main()
