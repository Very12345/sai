# sai 1.1.2 test build

This build introduces the `sai` brand while retaining the existing Android package, database and private storage identifiers for upgrade compatibility.

Highlights:

- compact mobile conversation rendering with completed work folded into one history card;
- tail-following only while the user remains near the bottom;
- active reasoning limited to its latest lines and task status kept in the header;
- provider-first, searchable model selection shared by the composer and settings;
- loopback-only cleartext policy so the Agent browser can reach `127.0.0.1` development servers without enabling arbitrary HTTP;
- Markdown URL and workspace-file recognition with internal browser links and open/share/save file cards;
- message-level rewind from any user turn, optionally restoring the session Git checkpoint;
- colorful sailboat identity and visible `sai` naming across Android, MCP and desktop UI.

Validation:

- all JVM unit tests pass;
- an Android instrumentation test proves the hidden Agent WebView can load a server bound to device loopback;
- ARM64 APK installs and launches on vivo V2458A / Android 16 without a fatal exception.

Debug instrumentation builds now use `com.phoneagent.app.debug` by default. An intentional in-place device build must pass `-PphoneAgentDebugPackageSuffix=false` and use the established signing key.
