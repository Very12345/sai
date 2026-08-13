# Security

Report vulnerabilities privately to the project maintainers before opening a public issue.

PhoneAgent treats model output, repositories, Skills, Hooks, MCP servers and shell output as untrusted input. PRoot is not a security sandbox. Do not use the app to execute repositories you would not run as your Android app user.

Provider credentials are encrypted with an Android Keystore-derived AES-GCM key. They must not appear in Room rows, exported event logs, the Debian environment, tool output, build logs or crash reports. Destructive filesystem actions, package installation, external-directory writes and Git history rewrites require explicit approval.
