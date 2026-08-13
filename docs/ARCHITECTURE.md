# sai architecture

sai uses the official DeepSeek Harness (DSH) as its only interactive Agent engine. Android owns the
device lifecycle and security boundary; DSH owns sessions, model execution, tool orchestration,
append-only events, steering, compaction and the conversation client.

| Module | Responsibility |
| --- | --- |
| `app` | Compose shell, project/file/terminal/settings screens, foreground services, Room migration, Keystore and Android capability bridge |
| `core:dsh` | Offline runtime verification/provisioning, authenticated loopback host, DSH RPC client and crash supervision |
| `core:runtime` | Debian/PRoot boundary, PTY, offline Git/GitHub CLI and optional toolchains |
| `core:data` | Native settings, market cache, Android-only state and encrypted secret storage |
| `core:extensions` | Staging, static audit and catalog compatibility for MCP, Skills and plugin packages |
| `dsh-plugins` | Independently versioned first-party DSH UI, Android, voice, model, market, request-guard, pet and artifact plugins |

The former Kotlin provider/agent loop remains only as a temporary legacy reader while v3/v4 Room
sessions are exported. The main Web UI, voice service, desktop chat and end-to-end diagnostic all
submit work through DSH. After the migration compatibility window it can be removed without changing
the DSH session format.

## Runtime and IPC

The APK carries Node 24.19.0 and `@deepseek-ai/dsh 0.1.0-rc.6` for ARM64/x86_64. The archive is
SHA-256 verified, unpacked into app-private storage and retains one previous runtime for rollback.
DSH runs inside Debian/PRoot with the complete private `workspaces` directory mounted at
`/home/phoneagent`; each session receives its own project cwd.

An Android foreground service supervises the process and starts a local authentication proxy. The
external `127.0.0.1` origin requires a random per-process HttpOnly cookie for HTTP and WebSocket,
while health checks use a separate bearer check. The Compose WebView rejects navigation outside that
origin. No website receives `addJavascriptInterface`.

Host plugins reach Android through a loopback-only authenticated bridge. API keys and GitHub tokens
remain in Android Keystore; the replacement DSH credential provider resolves a named reference for
one request and does not write the value to settings, environment snapshots, logs or exports.

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
