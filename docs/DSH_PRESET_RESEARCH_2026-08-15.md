# DSH optimization preset review — 2026-08-15

This review records why sai bundles two community presets and excludes the
runtime injector that accompanies one of them. The repositories were audited
at fixed commits; popularity is discovery evidence, not a security guarantee.

## Anchored Standard

- Repository: https://github.com/xiaobright/dsh-anchored-standard
- Audited commit: `95b98af6552d8e6176f80ac1b17b9d1186bfebf7`
- License: MIT
- Upstream validation: 39 zero-dependency tests pass at the audited commit.

The preset makes the first request look like DSH Minimal: the exact Minimal
persona, persistent `bash`, `str_replace_editor`, and no automatic workspace or
Skill catalog injection. After the first durable tool call or assistant reply,
it restores the full Standard catalog and normal context injection.

The authors report Project2 scores of 98/99 for DeepSeek V4 Pro, compared with
91 for Standard and 99/96 for Minimal in their fixed evaluation. This is
evidence for that benchmark and configuration, not a universal quality claim.
The strongest intended use is an existing codebase task where inspection,
maintenance and long tool chains benefit from a Minimal-aligned first
trajectory while later retaining Standard tools.

Current repository feedback also reports limitations: some long conversations
or heavy Skill/AGENTS.md injection drift back to `let me`; early revisions had
listener-order races, first-request budget assumptions and subagent bootstrap
problems. The audited commit includes the current race and tool-schema fixes,
but the project is only about one day old and remains experimental.

## Router Standard

- Suite: https://github.com/yjh051108/dsh-routing-suite
- Preset: https://github.com/yjh051108/dsh-router-standard
- Audited preset commit: `d4655d5874883c6994721236f0ece97499570eac`
- Version: `0.1.1`
- License: MIT
- Upstream validation: 15 zero-dependency tests pass at the audited commit.

This preset classifies the session's first user request and selects a stable
behavior band:

- `spec` for maintenance, debugging and repair: plan/read first;
- `react` for greenfield construction: produce, verify and fix;
- `weak` for ambiguous requests: let the model classify before acting;
- `mixed` exists only as an explicit experimental override because the authors'
  measurements identify it as an unstable transition region.

The weak persona is model-specific. DeepSeek Flash receives a neutral
`classify then act` instruction plus continuation and anti-environment-scan
anchors; Pro receives a more specification-oriented classifier. A fixed guide
is inserted near each real user message because the repository's experiments
found near-field guidance more reliable than changing the stable system
prefix. The preset adds `dev_router_status`, `dev_router_mode` and an isolated
mode subagent tool.

This is useful when sai alternates between new-project construction and
existing-project repair, especially with Flash. Its evidence is still limited:
many cells use only two to five runs, only two main task families are scored,
the classifier is keyword based, and an open Linux-support issue concerns the
suite installation path. The preset code itself is platform-neutral and does
not require the injector.

## Why the super injector is excluded

`dsh-routing-suite` also aggregates `dsh-super-injector` v0.3.3. It can inject,
reload, promote and remove arbitrary packages at runtime, mutate loader and
route state, create junctions, and persist an injection registry. That is a
developer hot-reload tool, not a model-quality preset. Preinstalling it would
bypass sai's staged archive inspection, explicit enablement, Android approval
gate and atomic rollback model. Its repository also has active installation and
Linux support reports, and its source checkout declares BSD-3-Clause without a
corresponding license text in the audited tree.

sai therefore bundles only Router Standard. Third-party runtime injectors may
still be displayed by the market, but they must not be silently installed or
activated.

## Product policy

- Both presets are installed by default, visible in **Extensions → Installed**,
  removable and reinstallable.
- Neither preset silently replaces Standard or changes existing sessions.
- Both remain pinned until a newer revision passes source review, unit tests,
  DSH rc.6 composition validation and Android tool-call regression tests.
- Both are fixed recommendations with an in-app source and risk summary.
