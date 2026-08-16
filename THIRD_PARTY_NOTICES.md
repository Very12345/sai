# Third-party notices

## DeepSeek Harness

sai embeds the official `@deepseek-ai/dsh 0.1.0-rc.6` runtime from audited source commit
`de944f41071912c5c9bf00eb3ad11b581eb5136b`. DeepSeek Harness is MIT licensed. Its license,
npm lockfile and transitive package notices are included in the offline runtime and release SBOM.

## Mobile Harness clients

- Codex mobile GUI is derived from
  [`friuns2/codex-mobile`](https://github.com/friuns2/codex-mobile) under the MIT License.
  It remains a presentation/client layer and drives the bundled official Codex runtime through
  Codex `app-server`; it does not replace the Codex agent loop.
- Claude Code mobile GUI is derived from
  [`sugyan/claude-code-webui`](https://github.com/sugyan/claude-code-webui) under the MIT License.
  sai replaces its pinned CLI dependency at runtime with the audited bundled Claude Code version
  and retains the upstream license in the offline runtime.

## DeepSeek Reasonix historical architectural reference

sai's former stable prompt prefix, append-only transcript, steering, tool-contract snapshot,
and context-compaction design were reviewed against DeepSeek-Reasonix `main-v2` commit
`f53edb599a929afe0c6fa514c8e441899c166afc`. Reasonix is MIT licensed. sai retains a
historical Kotlin implementation and retains this attribution for the earlier design. New interactive
tasks run through DeepSeek Harness rather than that implementation.

## Optional DeepSeek Harness community presets

sai includes two removable, non-default DSH presets. Both are installed from
audited, pinned source snapshots and remain clearly marked experimental in the
application.

- `Anchored Standard` from
  [`xiaobright/dsh-anchored-standard`](https://github.com/xiaobright/dsh-anchored-standard),
  commit `95b98af6552d8e6176f80ac1b17b9d1186bfebf7`, MIT License. Portions of its
  Standard composition are adapted from DeepSeek Harness and retain the
  upstream notice.
- `Router Standard` from
  [`yjh051108/dsh-router-standard`](https://github.com/yjh051108/dsh-router-standard),
  commit `d4655d5874883c6994721236f0ece97499570eac`, MIT License.

The separate `dsh-super-injector` component from `dsh-routing-suite` is not
embedded: arbitrary runtime injection and route mutation would bypass sai's
reviewed installation, approval and rollback boundary.

## sherpa-onnx speech models

Local speech recognition uses sherpa-onnx and models shipped in the independently uninstallable
`sai Voice Pack` APK rather than the base application. The
streaming model is `sherpa-onnx-x-asr-160ms-streaming-zipformer-transducer-zh-en-punct-int8-2026-06-05`,
sourced from the official k2-fsa GitHub release and verified during the build and first extraction against SHA-256
`8a6fca056e1a342546edd78be4d50274e2c01898e7b8ae8fc336f6410319c399`.

- AndroidX and Jetpack Compose: Apache License 2.0.
- OkHttp: Apache License 2.0.
- sherpa-onnx 1.13.4: Apache License 2.0; sai uses its Android ARM64/x86_64
  runtime for optional, fully local speech recognition.
- Paraformer Small Chinese/English INT8 model (2024-03-09): bundled from the
  sherpa-onnx signed GitHub release asset and pinned by SHA-256; its
  upstream model source is `crazyant/speech_paraformer_asr_nat-zh-cn-16k-common-vocab8358-onnx`.
- kotlinx.coroutines and kotlinx.serialization: Apache License 2.0.
- Sora Editor: LGPL-2.1-or-later.
- Termux terminal-view and terminal-emulator code, when vendored for release:
  Apache License 2.0.
- PRoot 5.1.107.89 (Termux fork): GPL-2.0, staged from SHA-256 pinned Termux
  packages; the matching source and reproducible workflow are in `native/`.
- GitHub CLI 2.97.0: MIT License; official Linux ARM64/x86_64 release archives
  are embedded and SHA-256 pinned for offline installation into the private Debian runtime.
- libtalloc 2.4.3: LGPL-3.0-or-later.
- libandroid-shmem 0.7: BSD-3-Clause.
- Debian userspace components: distributed under their package licenses with
  matching source and copyright metadata.

Release automation must generate a complete SBOM and license inventory before
publishing an APK or rootfs bundle.
