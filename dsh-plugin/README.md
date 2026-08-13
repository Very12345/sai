# Deprecated compatibility package

The original single bridge package has moved to the first-party plugin monorepo at
`../dsh-plugins`. It remains only for migration diagnostics; new sai runtimes install the
independently versioned `@sai/dsh-*` bundles.

DeepSeek Harness bridge for the sai Android coding agent. The plugin keeps a single stable
`sai_mobile` tool in the model prefix and forwards approved operations to sai over a loopback or
paired LAN bridge.

## Install

```bash
dsh plugin --profile sai add ./dsh-plugin
SAI_BRIDGE_TOKEN=... dsh --profile sai
```

Set `SAI_BRIDGE_URL` when the bridge is not at `http://127.0.0.1:39271`. The bearer token is
generated per pairing and must be passed through the process environment; it is never part of the
Harness prompt, tool result, or project files.

The Android app remains the authority for file, browser, microphone, screen and device-action
permissions. This package does not bypass an Android approval dialog.

The bundle format follows the official DeepSeek Harness out-of-tree plugin contract tested against
`deepseek-ai/DeepSeek-Harness` `0.1.0-rc.5`.
