# sai

sai is a GPLv3 Android coding agent designed around a local workspace. Its name combines AI with a sail: a compact agent built to move work forward from a phone,
a Debian/PRoot-compatible runtime, explicit tool approvals, and bring-your-own
model API credentials.

## Current implementation (1.2.0 DSH preview 2)

- Official DeepSeek Harness is the sole interactive Agent engine. The APK starts pinned DSH 0.1.0-rc.6
  on offline Node 24.19.0 inside app-private Debian/PRoot and embeds its authenticated Web client in
  the Compose shell.
- Android projects, models and legacy conversations migrate without exposing credentials. DSH resolves
  provider references through an Android Keystore credential plugin, and the old Kotlin loop no longer
  receives main UI, voice, desktop or diagnostic tasks.
- First-party capabilities live in `dsh-plugins/` as separately versioned DSH bundles: UI, Android,
  voice, models, request guard, GitHub, market, pet, artifacts and legacy import. The public ecosystem
  topic is exactly `dsh-plugin`.
- The DSH local origin is protected by a random HttpOnly session cookie, authenticated health checks
  and an allowlisted Android bridge; no general JavaScript interface is exposed.

- Native Kotlin and Jetpack Compose UI for Android 10 and newer.
- DSH event-sourced sessions with steering, approvals, compaction and background recovery.
- OpenAI Responses, OpenAI-compatible Chat Completions, Anthropic-compatible,
  and Gemini protocol adapters.
- Multi-project and multi-session workspace UI, structured event rendering,
  collapsible composer, attachment source selection, ZIP project import, Git
  import checkpoints, file tools, patch previews, command risk classification,
  and resumable jobs.
- Room v3 provider profiles with multiple models per provider, encrypted API
  keys, model discovery/manual models, reasoning controls, and task-level usage
  reporting. DeepSeek prices and totals are displayed in CNY.
- An operational extension center for installed extensions, discovery, MCP,
  Skills ZIP/Git imports, Hooks, plugin manifests, static diagnostics, live MCP
  probes, built-in recommendations, rollback metadata, and safe disabled-by-
  default installation.
- DSH-native append-only events, Steer input, context compaction, code tools,
  Git operations, browser capabilities and explicit speech requests.
- Press-to-talk input with send/microphone state merging, upward cancellation,
  live partial captions, and an optional, independently uninstallable Voice Pack
  using streaming Zipformer plus Paraformer final correction. Voice-call mode supports short `speak`
  summaries and Steer-style interruption.
- An Agent-only WebView environment with expiring DOM node identifiers,
  observe/click/input/select/submit/navigation/screenshot operations, and no
  JavaScript interface exposed to web pages.
- A Tauri 2 Windows companion with QR pairing, a pinned-certificate TLS
  WebSocket, X25519/HKDF/AES-GCM application encryption, project/session lists,
  conflict-safe text editing, and basic Agent conversations. Desktop requests
  cannot approve dangerous actions on behalf of the phone.
- ABI-matched PRoot/loader libraries for ARM64 and x86_64, a native `forkpty`
  bridge, and an interactive terminal surface.
- Offline-bundled GitHub CLI 2.97.0 for ARM64/x86_64, with browser device login
  and an optional advanced Token entry. Both paths migrate the credential into
  Android Keystore, erase the temporary `gh` config, and inject `GH_TOKEN` only
  into the individual trusted child process.
- A shared request-protection layer for model APIs, GitHub, MCP/Skill catalogs:
  bounded concurrency, GitHub mutation serialization, `Retry-After` handling,
  exponential backoff for idempotent requests, and host cooldowns.
- `dsh-plugin/` is an installable DeepSeek Harness bundle exposing one stable
  `sai_mobile` bridge tool while leaving all Android approvals on the phone.
- The base Debian 13 runtime, Git, GitHub CLI, Node 24 and pinned DSH are bundled
  for offline first start with SHA-256 verification. Optional language toolchains
  and the independently uninstallable Voice Pack remain separate downloads.
- Runtime activation is atomic. The previous verified DSH closure remains available
  from the failure screen; a rollback is pinned across app restarts until the user
  explicitly restores the APK-bundled runtime.

The provider harness, GUI, persistence, approval system, native PRoot runtime,
PTY bridge and rootfs installer build together as an ARM64 or x86_64 APK.
Native artifacts can be reproduced with the pinned staging or from-source
workflows documented in [native/README.md](native/README.md). The 1.2.0 preview 2 ARM64
build is intended for in-place validation on a vivo X200s-class device without clearing app data. It
includes the compact two-row composer, canvas-style PTY terminal, single-instance
task pet, settings search, and full application themes.

The `desktop` directory contains the Tauri 2 companion and its security notes.
The encrypted live session is implemented in 0.2.0. Automatic mDNS reconnection
and persistent Windows Credential Manager identity are still tracked as the
next connection-hardening milestone; a desktop restart currently requires a
fresh QR scan.

## Website and downloads

The responsive product website lives in [`site/`](site/) and is deployed by
GitHub Pages on every push to `main`. It introduces both the Android app and the
Windows companion, and resolves its download buttons against the latest GitHub
Release at runtime.

Pushing a version tag such as `v1.2.0-dsh-preview.2` runs the unified release workflow. It
tests and packages the ARM64/x86_64 Android builds, builds the Windows NSIS
installer, creates the optional `sai-voice-pack-zh-en.apk`, generates
`SHA256SUMS.txt`, CycloneDX SBOM and license notices, and publishes all outputs
in one Release. The two ABI-specific DSH Runtime Packs are released alongside a
detached Ed25519 signature so the runtime and the Android shell have independent
update artifacts.

## Build

The project pins Android Gradle Plugin 9.2.0, Gradle 9.4.1, Kotlin 2.3.21,
compile SDK 37, target SDK 36, and minimum SDK 29. Set `sdk.dir` in
`local.properties`, then run `scripts/build.ps1`.

All large development data is redirected away from the system drive:

- Android Studio: `D:\Code\Android Studio`
- Android SDK/NDK: `D:\Code\Android\Sdk`
- JDK 17: `D:\Code\Java\jdk-17.0.20+8`
- AVD images: `D:\Code\Android\Avd`
- Gradle cache and distributions: `D:\Code\GradleHome`
- Android Studio indexes and plugins: `D:\Code\Android\StudioData`

Use `scripts/start-android-studio.ps1` so the IDE also inherits these paths.

More detail is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and
[docs/BUILDING.md](docs/BUILDING.md).

## Security boundary

PRoot provides Linux userspace compatibility, not Docker-grade isolation.
Do not execute untrusted repositories or extensions. Provider secrets stay in
Android Keystore-backed encrypted storage and are never injected into shell
environments by default.
