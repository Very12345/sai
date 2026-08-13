#!/usr/bin/env python3
"""Safely extract a Linux tarball on Windows, materializing symlinks as files."""

from __future__ import annotations

import argparse
import os
import shutil
import tarfile
from pathlib import Path, PurePosixPath


def safe_path(root: Path, name: str, strip: int) -> Path | None:
    parts = PurePosixPath(name.lstrip("/")).parts[strip:]
    if not parts:
        return None
    if any(part in ("", ".", "..") for part in parts):
        raise ValueError(f"unsafe tar path: {name}")
    output = (root / Path(*parts)).resolve()
    output.relative_to(root.resolve())
    return output


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("archive", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--strip-components", type=int, default=0)
    args = parser.parse_args()
    root = args.destination.resolve()
    root.mkdir(parents=True, exist_ok=True)
    links: list[tuple[Path, str]] = []
    with tarfile.open(args.archive, "r:*") as archive:
        for member in archive:
            output = safe_path(root, member.name, args.strip_components)
            if output is None:
                continue
            if member.isdir():
                output.mkdir(parents=True, exist_ok=True)
            elif member.isfile():
                output.parent.mkdir(parents=True, exist_ok=True)
                source = archive.extractfile(member)
                if source is None:
                    raise ValueError(f"missing tar payload: {member.name}")
                with source, output.open("wb") as sink:
                    shutil.copyfileobj(source, sink, 1024 * 1024)
                try:
                    os.chmod(output, member.mode & 0o777)
                except OSError:
                    pass
            elif member.issym() or member.islnk():
                links.append((output, member.linkname))
            else:
                raise ValueError(f"unsupported tar entry: {member.name}")
    for output, linkname in links:
        target = (output.parent / Path(*PurePosixPath(linkname).parts)).resolve()
        target.relative_to(root)
        if not target.is_file():
            raise ValueError(f"link target is unavailable: {linkname}")
        output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(target, output)


if __name__ == "__main__":
    main()
