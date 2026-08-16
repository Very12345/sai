use aes_gcm::{aead::Aead, Aes256Gcm, KeyInit, Nonce};
use base64::{engine::general_purpose::{STANDARD, URL_SAFE_NO_PAD}, Engine};
use futures_util::{SinkExt, StreamExt};
use hkdf::Hkdf;
use hmac::{Hmac, Mac};
use qrcode::{render::svg, QrCode};
use rand::{rngs::OsRng, RngCore};
use rcgen::generate_simple_self_signed;
use rustls::pki_types::{CertificateDer, PrivateKeyDer, PrivatePkcs8KeyDer};
use serde::Serialize;
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{collections::HashSet, fs, path::{Component, Path, PathBuf}, sync::{Arc, Mutex}, time::Duration};
use std::net::IpAddr;
use tauri::{AppHandle, Emitter, Manager, State};
use tokio::{net::TcpListener, sync::mpsc, task::AbortHandle, time::timeout};
use tokio_rustls::TlsAcceptor;
use tokio_tungstenite::{accept_async, tungstenite::Message};
use x25519_dalek::{PublicKey, StaticSecret};

type HmacSha256 = Hmac<Sha256>;

#[derive(Serialize)]
struct PairingOffer {
    endpoint: String,
    nonce: String,
    public_key: String,
    certificate_pin: String,
    short_code: String,
    expires_seconds: u32,
    qr_svg: String,
}

#[derive(Default)]
struct DesktopState {
    inner: Arc<Mutex<DesktopRuntime>>,
}

#[derive(Default)]
struct DesktopRuntime {
    abort: Option<AbortHandle>,
    outbound: Option<mpsc::UnboundedSender<Value>>,
}

impl Drop for DesktopRuntime {
    fn drop(&mut self) {
        if let Some(abort) = self.abort.take() { abort.abort(); }
    }
}

#[tauri::command]
async fn create_pairing_offer(
    app: AppHandle,
    state: State<'_, DesktopState>,
) -> Result<PairingOffer, String> {
    let ip = preferred_lan_ip()?;
    let listener = TcpListener::bind((ip, 37913)).await.map_err(|e| {
        format!("无法监听 37913 端口；请关闭旧实例或检查防火墙：{e}")
    })?;

    let mut offer_nonce = [0u8; 24];
    OsRng.fill_bytes(&mut offer_nonce);
    let secret = StaticSecret::random_from_rng(OsRng);
    let public = PublicKey::from(&secret);

    let certified = generate_simple_self_signed(vec!["phoneagent.local".into(), ip.to_string()])
        .map_err(|e| e.to_string())?;
    let cert_der: CertificateDer<'static> = certified.cert.der().clone();
    let cert_pin = Sha256::digest(cert_der.as_ref());
    let key_der = PrivatePkcs8KeyDer::from(certified.signing_key.serialize_der());
    let tls = rustls::ServerConfig::builder()
        .with_no_client_auth()
        .with_single_cert(vec![cert_der], PrivateKeyDer::Pkcs8(key_der))
        .map_err(|e| e.to_string())?;

    let endpoint = format!("wss://{ip}:37913/pair");
    let payload = json!({
        "v": 1,
        "endpoint": endpoint,
        "nonce": URL_SAFE_NO_PAD.encode(offer_nonce),
        "publicKey": URL_SAFE_NO_PAD.encode(public.as_bytes()),
        "pin": URL_SAFE_NO_PAD.encode(cert_pin),
        "scopes": ["project.read", "project.write", "chat", "harness.sync"]
    }).to_string();
    let code = QrCode::new(payload.as_bytes()).map_err(|e| e.to_string())?;
    let short = u32::from_be_bytes([offer_nonce[0], offer_nonce[1], offer_nonce[2], offer_nonce[3]]) % 1_000_000;

    let runtime = state.inner.clone();
    let handle = tokio::spawn(async move {
        let result = serve_pairing(listener, TlsAcceptor::from(Arc::new(tls)), secret, offer_nonce, app.clone(), runtime.clone()).await;
        if let Err(error) = result {
            let _ = app.emit("phoneagent-status", json!({"state":"error", "message": error}));
        }
        if let Ok(mut guard) = runtime.lock() { guard.outbound = None; }
    });
    {
        let mut guard = state.inner.lock().map_err(|_| "桌面连接状态锁损坏".to_string())?;
        if let Some(previous) = guard.abort.replace(handle.abort_handle()) { previous.abort(); }
        guard.outbound = None;
    }

    Ok(PairingOffer {
        endpoint: format!("{ip}:37913"),
        nonce: URL_SAFE_NO_PAD.encode(offer_nonce),
        public_key: URL_SAFE_NO_PAD.encode(public.as_bytes()),
        certificate_pin: URL_SAFE_NO_PAD.encode(cert_pin),
        short_code: format!("{short:06}"),
        expires_seconds: 120,
        qr_svg: code.render::<svg::Color>().min_dimensions(256, 256).build(),
    })
}

async fn serve_pairing(
    listener: TcpListener,
    acceptor: TlsAcceptor,
    secret: StaticSecret,
    offer_nonce: [u8; 24],
    app: AppHandle,
    runtime: Arc<Mutex<DesktopRuntime>>,
) -> Result<(), String> {
    let (stream, address) = timeout(Duration::from_secs(120), listener.accept())
        .await.map_err(|_| "配对二维码已过期".to_string())?
        .map_err(|e| e.to_string())?;
    let tls = acceptor.accept(stream).await.map_err(|e| format!("TLS 握手失败：{e}"))?;
    let mut socket = accept_async(tls).await.map_err(|e| format!("WebSocket 握手失败：{e}"))?;
    let first = timeout(Duration::from_secs(15), socket.next()).await
        .map_err(|_| "手机未在时限内发送配对证明".to_string())?
        .ok_or_else(|| "手机在配对前断开".to_string())?
        .map_err(|e| e.to_string())?;
    let request: Value = serde_json::from_str(first.to_text().map_err(|e| e.to_string())?)
        .map_err(|_| "配对消息不是有效 JSON".to_string())?;
    let expected_nonce = URL_SAFE_NO_PAD.encode(offer_nonce);
    if request.get("type").and_then(Value::as_str) != Some("pair") ||
       request.get("nonce").and_then(Value::as_str) != Some(expected_nonce.as_str()) {
        return Err("配对 nonce 不匹配".into());
    }
    let phone_public_bytes = decode_fixed::<32>(request.get("publicKey").and_then(Value::as_str), "手机公钥")?;
    let phone_public = PublicKey::from(phone_public_bytes);
    let shared = secret.diffie_hellman(&phone_public);
    let mut session_key = [0u8; 32];
    Hkdf::<Sha256>::new(Some(&offer_nonce), shared.as_bytes())
        .expand(b"phoneagent-lan-v1", &mut session_key)
        .map_err(|_| "HKDF 派生失败".to_string())?;
    let phone_proof = request.get("proof").and_then(Value::as_str)
        .ok_or_else(|| "缺少手机配对证明".to_string())?;
    verify_proof(&session_key, b"phoneagent-phone", phone_proof)?;
    let desktop_proof = make_proof(&session_key, b"phoneagent-desktop")?;
    socket.send(Message::Text(json!({"type":"paired", "proof":desktop_proof}).to_string().into()))
        .await.map_err(|e| e.to_string())?;

    let (tx, mut rx) = mpsc::unbounded_channel();
    runtime.lock().map_err(|_| "桌面连接状态锁损坏".to_string())?.outbound = Some(tx);
    let _ = app.emit("phoneagent-status", json!({"state":"connected", "address":address.to_string()}));
    let _ = runtime.lock().ok().and_then(|guard| guard.outbound.as_ref().map(|sender| {
        let _ = sender.send(json!({"type":"state.list", "id":new_request_id()}));
    }));
    let cipher = Aes256Gcm::new_from_slice(&session_key).map_err(|_| "AES 初始化失败".to_string())?;
    let mut received_nonces = HashSet::new();

    loop {
        tokio::select! {
            incoming = socket.next() => match incoming {
                Some(Ok(Message::Text(text))) => {
                    let envelope: Value = serde_json::from_str(&text).map_err(|_| "无效的加密消息".to_string())?;
                    let plain = decrypt_envelope(&cipher, &envelope, &mut received_nonces)?;
                    let event: Value = serde_json::from_slice(&plain).map_err(|_| "解密内容不是 JSON".to_string())?;
                    let _ = app.emit("phoneagent-event", event);
                }
                Some(Ok(Message::Close(_))) | None => break,
                Some(Ok(Message::Ping(data))) => { socket.send(Message::Pong(data)).await.map_err(|e| e.to_string())?; }
                Some(Ok(_)) => {}
                Some(Err(error)) => return Err(format!("连接错误：{error}")),
            },
            outbound = rx.recv() => match outbound {
                Some(value) => {
                    let encrypted = encrypt_envelope(&cipher, &serde_json::to_vec(&value).map_err(|e| e.to_string())?)?;
                    socket.send(Message::Text(encrypted.to_string().into())).await.map_err(|e| e.to_string())?;
                }
                None => break,
            }
        }
    }
    let _ = app.emit("phoneagent-status", json!({"state":"disconnected"}));
    Ok(())
}

#[tauri::command]
fn send_chat(text: String, state: State<'_, DesktopState>) -> Result<String, String> {
    send_command(&state, json!({"type":"chat.send", "id":new_request_id(), "text":text}))
}

#[tauri::command]
fn send_remote_command(command: String, payload: Value, state: State<'_, DesktopState>) -> Result<String, String> {
    let id = new_request_id();
    send_command(&state, json!({"type":command, "id":id, "payload":payload}))?;
    Ok(id)
}

#[tauri::command]
fn save_synced_session(
    app: AppHandle,
    harness: String,
    path: String,
    content: String,
    sha256: String,
    dsh_root: Option<String>,
) -> Result<String, String> {
    if !matches!(harness.as_str(), "codex" | "claude-code" | "dsh") {
        return Err("未知 Harness".into());
    }
    let relative = Path::new(&path);
    if relative.as_os_str().is_empty() || relative.is_absolute() ||
        relative.components().any(|part| !matches!(part, Component::Normal(_))) {
        return Err("会话同步路径非法".into());
    }
    let bytes = STANDARD.decode(content).map_err(|_| "会话内容 Base64 无效".to_string())?;
    if bytes.len() > 16 * 1024 * 1024 { return Err("会话文件超过 16 MB".into()); }
    let actual = format!("{:x}", Sha256::digest(&bytes));
    if actual != sha256 { return Err("会话内容摘要不匹配".into()); }
    let root = desktop_harness_roots(&app, dsh_root.as_deref())?
        .remove(&harness).ok_or_else(|| "未知 Harness".to_string())?;
    let target = root.join(relative);
    let parent = target.parent().ok_or_else(|| "会话路径缺少父目录".to_string())?;
    fs::create_dir_all(parent).map_err(|e| format!("无法创建同步目录：{e}"))?;
    let temporary = parent.join(format!(".{}.sync.tmp", target.file_name().unwrap_or_default().to_string_lossy()));
    if target.is_file() {
        let existing = fs::read(&target).map_err(|e| format!("无法读取电脑端会话：{e}"))?;
        let existing_sha = format!("{:x}", Sha256::digest(&existing));
        if existing_sha == sha256 { return Ok(target.to_string_lossy().into_owned()); }
        return Err(format!("同步冲突，电脑端会话已变化：{}", target.display()));
    }
    fs::write(&temporary, bytes).map_err(|e| format!("无法写入同步文件：{e}"))?;
    fs::rename(&temporary, &target).or_else(|_| {
        fs::copy(&temporary, &target).map(|_| ()).and_then(|_| fs::remove_file(&temporary))
    }).map_err(|e| format!("无法提交同步文件：{e}"))?;
    Ok(target.to_string_lossy().into_owned())
}

#[tauri::command]
fn local_harness_manifest(app: AppHandle, dsh_root: Option<String>) -> Result<Value, String> {
    let mut files = Vec::new();
    for (harness, root) in desktop_harness_roots(&app, dsh_root.as_deref())? {
        collect_session_files(&root, &root, &harness, &mut files)?;
    }
    Ok(json!({"version": 1, "files": files}))
}

#[tauri::command]
fn read_local_harness_session(
    app: AppHandle,
    harness: String,
    path: String,
    dsh_root: Option<String>,
) -> Result<Value, String> {
    let roots = desktop_harness_roots(&app, dsh_root.as_deref())?;
    let root = roots.get(&harness).ok_or_else(|| "未知 Harness".to_string())?;
    let relative = checked_relative(&path)?;
    let target = root.join(relative);
    let metadata = fs::symlink_metadata(&target).map_err(|_| "电脑端会话文件不存在".to_string())?;
    if !metadata.file_type().is_file() || metadata.file_type().is_symlink() || metadata.len() > 16 * 1024 * 1024 {
        return Err("电脑端会话文件非法或超过 16 MB".into());
    }
    let bytes = fs::read(&target).map_err(|e| format!("无法读取电脑端会话：{e}"))?;
    Ok(json!({
        "harness": harness,
        "path": path,
        "sha256": format!("{:x}", Sha256::digest(&bytes)),
        "content": STANDARD.encode(bytes),
    }))
}

fn desktop_harness_roots(app: &AppHandle, dsh_root: Option<&str>) -> Result<std::collections::HashMap<String, PathBuf>, String> {
    let home = app.path().home_dir().map_err(|e| format!("无法定位用户主目录：{e}"))?;
    let fallback_dsh = app.path().app_local_data_dir().map_err(|e| e.to_string())?.join("session-sync/dsh");
    let dsh = match dsh_root.map(str::trim).filter(|value| !value.is_empty()) {
        Some(value) => {
            let path = PathBuf::from(value);
            if !path.is_absolute() { return Err("DSH 会话目录必须是绝对路径".into()); }
            path
        }
        None => fallback_dsh,
    };
    Ok(std::collections::HashMap::from([
        ("codex".to_string(), home.join(".codex/sessions")),
        ("claude-code".to_string(), home.join(".claude/projects")),
        ("dsh".to_string(), dsh),
    ]))
}

fn checked_relative(path: &str) -> Result<&Path, String> {
    let relative = Path::new(path);
    if relative.as_os_str().is_empty() || relative.is_absolute() ||
        relative.components().any(|part| !matches!(part, Component::Normal(_))) {
        return Err("会话同步路径非法".into());
    }
    Ok(relative)
}

fn collect_session_files(root: &Path, directory: &Path, harness: &str, output: &mut Vec<Value>) -> Result<(), String> {
    if output.len() >= 5_000 || !directory.exists() { return Ok(()); }
    let metadata = fs::symlink_metadata(directory).map_err(|e| e.to_string())?;
    if metadata.file_type().is_symlink() { return Ok(()); }
    for entry in fs::read_dir(directory).map_err(|e| format!("无法读取会话目录：{e}"))? {
        if output.len() >= 5_000 { break; }
        let entry = entry.map_err(|e| e.to_string())?;
        let path = entry.path();
        let metadata = fs::symlink_metadata(&path).map_err(|e| e.to_string())?;
        if metadata.file_type().is_symlink() { continue; }
        if metadata.is_dir() {
            collect_session_files(root, &path, harness, output)?;
        } else if metadata.is_file() && metadata.len() <= 16 * 1024 * 1024 {
            let relative = path.strip_prefix(root).map_err(|_| "会话路径逃逸".to_string())?
                .to_string_lossy().replace('\\', "/");
            let bytes = fs::read(&path).map_err(|e| format!("无法读取会话文件：{e}"))?;
            output.push(json!({
                "harness": harness,
                "path": relative,
                "size": metadata.len(),
                "modifiedAt": metadata.modified().ok().and_then(|time| time.duration_since(std::time::UNIX_EPOCH).ok()).map(|value| value.as_millis() as u64).unwrap_or(0),
                "sha256": format!("{:x}", Sha256::digest(&bytes)),
            }));
        }
    }
    Ok(())
}

fn send_command(state: &State<'_, DesktopState>, value: Value) -> Result<String, String> {
    let id = value.get("id").and_then(Value::as_str).unwrap_or_default().to_string();
    let guard = state.inner.lock().map_err(|_| "桌面连接状态锁损坏".to_string())?;
    guard.outbound.as_ref().ok_or_else(|| "尚未与手机建立加密会话".to_string())?
        .send(value).map_err(|_| "手机连接已关闭".to_string())?;
    Ok(id)
}

fn encrypt_envelope(cipher: &Aes256Gcm, plain: &[u8]) -> Result<Value, String> {
    let mut nonce = [0u8; 12];
    OsRng.fill_bytes(&mut nonce);
    let encrypted = cipher.encrypt(Nonce::from_slice(&nonce), plain)
        .map_err(|_| "消息加密失败".to_string())?;
    Ok(json!({"type":"encrypted", "nonce":URL_SAFE_NO_PAD.encode(nonce), "ciphertext":URL_SAFE_NO_PAD.encode(encrypted)}))
}

fn decrypt_envelope(cipher: &Aes256Gcm, value: &Value, seen: &mut HashSet<String>) -> Result<Vec<u8>, String> {
    if value.get("type").and_then(Value::as_str) != Some("encrypted") { return Err("拒绝明文会话消息".into()); }
    let nonce_text = value.get("nonce").and_then(Value::as_str).ok_or_else(|| "缺少消息 nonce".to_string())?;
    if !seen.insert(nonce_text.to_string()) { return Err("检测到重放消息".into()); }
    let nonce = decode_fixed::<12>(Some(nonce_text), "消息 nonce")?;
    let ciphertext = URL_SAFE_NO_PAD.decode(value.get("ciphertext").and_then(Value::as_str).ok_or_else(|| "缺少密文".to_string())?)
        .map_err(|_| "密文 Base64 无效".to_string())?;
    cipher.decrypt(Nonce::from_slice(&nonce), ciphertext.as_ref()).map_err(|_| "消息认证失败".to_string())
}

fn decode_fixed<const N: usize>(value: Option<&str>, label: &str) -> Result<[u8; N], String> {
    let decoded = URL_SAFE_NO_PAD.decode(value.ok_or_else(|| format!("缺少{label}"))?)
        .map_err(|_| format!("{label} Base64 无效"))?;
    decoded.try_into().map_err(|_| format!("{label}长度错误"))
}

fn make_proof(key: &[u8], data: &[u8]) -> Result<String, String> {
    let mut mac = <HmacSha256 as Mac>::new_from_slice(key).map_err(|_| "HMAC 初始化失败".to_string())?;
    mac.update(data);
    Ok(URL_SAFE_NO_PAD.encode(mac.finalize().into_bytes()))
}

fn verify_proof(key: &[u8], data: &[u8], proof: &str) -> Result<(), String> {
    let expected = URL_SAFE_NO_PAD.decode(proof).map_err(|_| "配对证明 Base64 无效".to_string())?;
    let mut mac = <HmacSha256 as Mac>::new_from_slice(key).map_err(|_| "HMAC 初始化失败".to_string())?;
    mac.update(data);
    mac.verify_slice(&expected).map_err(|_| "配对证明无效".to_string())
}

fn new_request_id() -> String {
    let mut bytes = [0u8; 12];
    OsRng.fill_bytes(&mut bytes);
    URL_SAFE_NO_PAD.encode(bytes)
}

fn preferred_lan_ip() -> Result<IpAddr, String> {
    let mut candidates = local_ip_address::list_afinet_netifas().map_err(|e| e.to_string())?
        .into_iter()
        .filter(|(_, address)| matches!(address, IpAddr::V4(ip) if ip.is_private() && !ip.is_loopback()))
        .collect::<Vec<_>>();
    candidates.sort_by_key(|(name, _)| {
        let lower = name.to_ascii_lowercase();
        if lower.contains("wi-fi") || lower.contains("wifi") || lower.contains("wlan") { 0 }
        else if lower.contains("ethernet") { 1 }
        else { 2 }
    });
    candidates.first().map(|(_, address)| *address)
        .ok_or_else(|| "未找到可供手机访问的 Wi-Fi/以太网 IPv4 地址".to_string())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .manage(DesktopState::default())
        .invoke_handler(tauri::generate_handler![
            create_pairing_offer,
            send_chat,
            send_remote_command,
            save_synced_session,
            local_harness_manifest,
            read_local_harness_session,
        ])
        .run(tauri::generate_context!())
        .expect("sai Desktop failed");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn encrypted_envelope_round_trips_and_rejects_replay() {
        let cipher = Aes256Gcm::new_from_slice(&[7u8; 32]).unwrap();
        let envelope = encrypt_envelope(&cipher, br#"{"type":"state.list"}"#).unwrap();
        let mut seen = HashSet::new();
        let plain = decrypt_envelope(&cipher, &envelope, &mut seen).unwrap();
        assert_eq!(plain, br#"{"type":"state.list"}"#);
        assert!(decrypt_envelope(&cipher, &envelope, &mut seen).unwrap_err().contains("重放"));
    }

    #[test]
    fn proofs_are_bound_to_each_side() {
        let key = [9u8; 32];
        let phone = make_proof(&key, b"phoneagent-phone").unwrap();
        assert!(verify_proof(&key, b"phoneagent-phone", &phone).is_ok());
        assert!(verify_proof(&key, b"phoneagent-desktop", &phone).is_err());
    }

    #[test]
    fn selected_listener_address_is_private_lan() {
        match preferred_lan_ip().unwrap() {
            IpAddr::V4(address) => assert!(address.is_private() && !address.is_loopback()),
            IpAddr::V6(_) => panic!("sai Desktop v0.2 pairing currently advertises IPv4"),
        }
    }
}
