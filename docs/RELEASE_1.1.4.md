# sai 1.1.4 offline-base test build

The ARM64 APK now embeds a pinned Debian 13 base with Bash, CA certificates and Git 2.47.3. First-run initialization copies the bundled XZ asset, verifies its signed SHA-256, extracts it locally and creates the default Git repository. It does not contact GitHub or an APT mirror.

Python, Node.js, Rust, Go, Java, C/C++, LaTeX and the remaining development packages stay optional in the toolchain manager.

Offline runtime asset:

- asset: `assets/runtime/sai-debian13-arm64-git-v1.tar.xz`
- compressed size: 55,448,276 bytes
- SHA-256: `6c3571174998d836c72aae4f329c4d141666375a5673229367f533df206e84d3`
- upstream rootfs: Termux proot-distro Debian Trixie v4.26.0
- Git: Debian 13 package, version 2.47.3

Validation performed on vivo V2458A / ARM64:

- clean isolated app install with no ADB reverse or build proxy;
- embedded asset copied, verified and extracted;
- `git version 2.47.3` executed inside the actual PRoot environment;
- default `main` repository and initial checkpoint created;
- 34 JVM tests passed.
