# sai Desktop 1.3.0

The Windows companion is a Tauri 2 application for local-network project files
and basic Agent conversations. It does not use a cloud relay and cannot approve
dangerous operations on behalf of the phone.

## Pairing

1. Start `phoneagent-desktop.exe` or install the NSIS package.
2. Select **显示配对二维码**. On first use Windows Firewall asks whether the
   application may listen on the network; allow the trusted private network only.
3. In sai Android open **设置 → 电脑连接 → 扫描电脑配对二维码**.
4. Keep the two devices on the same trusted Wi-Fi/LAN.

The QR payload contains an ephemeral X25519 public key, a nonce and the exact
SHA-256 fingerprint of a temporary TLS certificate. After certificate pinning,
both sides derive the session key using X25519 and HKDF-SHA256. Application
messages use AES-256-GCM and reject repeated nonces.

The paired desktop public key and granted scopes are recorded on Android. No API
key or provider credential is sent to the computer. Pairing must currently be
repeated after the desktop app exits; automatic mDNS reconnection and persistent
Windows Credential Manager identities remain a follow-up hardening milestone.

## Build paths

Run `..\scripts\desktop-env.ps1` first. Rust, Cargo, pnpm and build output are
kept under `D:\Code`. `pnpm tauri build` writes the portable executable and NSIS
bundle below `D:\Code\Build\PhoneAgentDesktop\cargo-target\release`.
