# sai architecture

sai is a multi-Harness mobile workbench. Android owns the device lifecycle and security boundary;
DeepSeek Harness, official Codex `app-server`, and Claude Code's Agent SDK/CLI each remain the source
of truth for their own sessions, tools, approvals and history. sai adapts those real clients to a phone
instead of maintaining imitation agent loops.

| Module | Responsibility |
| --- | --- |
| `app` | Compose shell, project/file/terminal/settings screens, foreground services, Room migration, Keystore and Android capability bridge |
| `core:dsh` | Offline runtime verification/provisioning, authenticated loopback host, DSH RPC client and rollback |
| `core:harness` | Shared Harness kinds, lifecycle contracts and normalized task state |
| `core:runtime` | Debian/PRoot boundary, PTY, offline Git/GitHub CLI and optional toolchains |
| `core:data` | Native settings, market cache, Android-only state and encrypted secret storage |
| `core:extensions` | Staging, static audit and catalog compatibility for MCP, Skills and plugin packages |
| `dsh-plugins` | Independently versioned first-party DSH UI, Android, voice, model, market, request-guard, pet and artifact plugins |

The former Kotlin provider/agent loop remains only as a temporary legacy reader while v3/v4 Room
sessions are exported. The main Web UI, voice service, desktop chat and end-to-end diagnostic all
submit work through DSH. After the migration compatibility window it can be removed without changing
the DSH session format.

## Runtime and IPC

The APK carries Node 24.19.0, `@deepseek-ai/dsh 0.1.0-rc.6`, Codex 0.147.0 and Claude Code 2.1.233
for ARM64/x86_64. Codex is driven by `app-server`; Claude Code uses its version-matched Agent SDK.
The archive is
SHA-256 verified, unpacked into app-private storage and retains one previous runtime for rollback.
The `current`/`previous` swap is atomic and a rollback marker prevents startup from immediately
overwriting the selected previous closure. “Restore bundled runtime” clears that marker and performs
the normal verified activation path.
DSH runs inside Debian/PRoot with the complete private `workspaces` directory mounted at
`/home/phoneagent`; each session receives its own project cwd.

An Android foreground service supervises the process and starts a local authentication proxy. The
external `127.0.0.1` origin requires a random per-process HttpOnly cookie for HTTP and WebSocket,
while health checks use a separate bearer check. The Compose WebView rejects navigation outside that
origin. No website receives `addJavascriptInterface`.

Host plugins reach Android through a loopback-only authenticated bridge. API keys and GitHub tokens
remain in Android Keystore; the replacement DSH credential provider resolves a named reference for
one request and does not write the value to settings, environment snapshots, logs or exports.
GitHub browser device login runs `gh` against an isolated temporary config directory, copies only the
resulting credential to Keystore, and deletes the directory before returning control to the UI.

The paired Windows client can copy native DSH, Codex and Claude conversation artifacts over the same
X25519/HKDF/AES-GCM channel. Paths are confined to the three Harness stores, files are size-bounded,
and every read/write is SHA-256 checked so a newer phone-side transcript is never silently replaced.

## Session migration

Before DSH starts, Android writes a non-secret, atomic migration handoff. User/assistant turns become
native DSH `turn`, `step` and message events; titles and provider/model attribution are retained.
Legacy events that have no equivalent remain in a per-session read-only JSON attachment. The importer
flushes every session through DSH persistence and renames the handoff only after all imports succeed.

## Security boundary

PRoot provides Linux compatibility, not Docker-grade isolation. DSH and third-party plugins execute
with the Android app UID. Plugin installation therefore requires path/symlink/archive validation,
license and permission review, explicit enablement and a restartable DSH profile. Third-party install,
prepare and postinstall scripts are never executed.
