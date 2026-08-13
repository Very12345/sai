# PhoneAgent 1.1.1 desktop-connection test build

This build adds the usable local-network desktop bridge:

- Tauri 2 Windows GUI with pairing QR, project/session lists, file tree, text
  preview/editing and basic Agent conversation stream.
- TLS WebSocket transport bound to the selected private LAN address.
- QR certificate pinning, X25519, HKDF-SHA256, HMAC role proofs, AES-256-GCM and
  replayed-nonce rejection.
- Android QR scanner, paired-desktop Room record and visible foreground service.
- Project-bound path checks, 2 MB file limit, atomic writes and SHA-256 optimistic
  concurrency checks.
- Desktop chat starts a normal PhoneAgent task; approvals remain on the phone.

Automatic mDNS reconnection and persistent Windows Credential Manager identities
are not included yet. Pairing is repeated when the desktop process restarts.
