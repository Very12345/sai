# Local runtime artifacts

The APK does not contain a mutable Linux rootfs. The manifests pin upstream Debian 13 ARM64 and x86_64 PRoot-Distro archives by URL and SHA-256. The installer supports resume, checksum verification, safe extraction, top-level-directory normalization and offline import.

After extraction, `RuntimeProvisioner` installs Python 3, pip/venv, pytest, Git, curl, OpenSSH client, ripgrep, jq and CA certificates with apt, removes apt caches, and runs a version probe. Readiness is recorded only after that probe succeeds. Interrupted provisioning can be retried without downloading the rootfs again.

Every release must publish the rootfs recipe, Debian package manifest, corresponding sources or source URLs, SHA-256 digest, and an SPDX or CycloneDX SBOM.

ARM64 builds embed the minimal `sai-debian13-arm64-git-v1.tar.xz` base so first-run initialization is independent of GitHub and APT availability. Python and larger development toolchains remain optional packages.
