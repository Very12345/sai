# Harness GUI source provenance

sai does not reimplement the Codex or Claude Code agent loop. The offline runtime builds two
MIT-licensed responsive clients over the official runtime protocols:

| Client | Source | Audited commit | Runtime |
| --- | --- | --- | --- |
| Codex mobile GUI | `https://github.com/friuns2/codex-mobile` | `fac2291b0e606c869d4760f56c0f49172214cb79` | Codex `app-server` 0.147.0 |
| Claude Code WebUI | `https://github.com/sugyan/claude-code-webui` | `9109263873cfc2a6eb0a160396fdfd60c2d6a37f` | Claude Code 2.1.233 + Agent SDK 0.3.233 |

The upstream Claude WebUI targeted the old `@anthropic-ai/claude-code` JavaScript export. Claude
Code 2.x moved that API to `@anthropic-ai/claude-agent-sdk`, so sai changes its `query` and history
type imports to the version-matched Agent SDK and keeps the actual CLI path explicit. The upstream
backend type check and test suite must pass before `scripts/prepare-dsh-runtime.ps1` accepts its
compiled `backend/dist` directory.

Large checkouts, npm stores and compiled GUI artifacts stay under `D:\Code`. The build script copies
only production bundles and both upstream `LICENSE` files into the signed runtime closure. Their
repository URLs, exact commits and license identifiers are also recorded in the runtime manifest.
