const invoke = (...args) => window.__TAURI__.core.invoke(...args);
const listen = (...args) => window.__TAURI__.event.listen(...args);
const $ = (selector) => document.querySelector(selector);
const pair = $("#pair");
const qr = $("#qr");
const status = $("#status");
const pairingText = $("#pairingText");
const prompt = $("#prompt");
const projects = $("#projects");
const sessions = $("#sessions");
const files = $("#files");
const editor = $("#editor");
const fileName = $("#fileName");
const save = $("#save");

let connected = false;
let activeProject = null;
let activeSession = null;
let activeFile = null;
let activeFileHash = null;

pair.addEventListener("click", async () => {
  pair.disabled = true;
  status.textContent = "正在启动加密局域网配对…";
  try {
    const offer = await invoke("create_pairing_offer");
    qr.innerHTML = offer.qr_svg;
    pairingText.textContent = `请在 sai 设置中扫描。核对配对码 ${offer.short_code}，${offer.expires_seconds} 秒内有效。`;
    status.textContent = `等待手机连接 · ${offer.endpoint}`;
  } catch (error) {
    status.textContent = `无法启动配对：${error}`;
  } finally { pair.disabled = false; }
});

$("#send").addEventListener("click", sendChat);
prompt.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); sendChat(); }
});

async function sendChat() {
  const text = prompt.value.trim();
  if (!text || !connected) return;
  appendMessage("user", text);
  try {
    await invoke("send_remote_command", { command: "chat.send", payload: { text, projectId: activeProject?.id || null } });
    prompt.value = "";
  }
  catch (error) { status.textContent = `发送失败：${error}`; }
}

save.addEventListener("click", async () => {
  if (!activeProject || !activeFile) return;
  try {
    await invoke("send_remote_command", { command: "file.write", payload: {
      projectId: activeProject.id, path: activeFile, expectedSha256: activeFileHash,
      content: btoa(unescape(encodeURIComponent(editor.value)))
    }});
    save.disabled = true;
    status.textContent = "正在保存并校验版本…";
  } catch (error) { status.textContent = `保存失败：${error}`; }
});
editor.addEventListener("input", () => { if (activeFile) save.disabled = false; });

listen("phoneagent-status", ({ payload }) => {
  connected = payload.state === "connected";
  $("#send").disabled = !connected;
  if (connected) {
    status.textContent = `已加密连接 · ${payload.address}`;
    $("#welcome").classList.add("hidden");
    $("#stream").classList.remove("hidden");
  } else if (payload.state === "error") status.textContent = payload.message;
  else status.textContent = "连接已断开";
});

listen("phoneagent-event", ({ payload }) => {
  if (payload.type === "response") handleResponse(payload);
  else if (payload.type === "agent.event") {
    appendMessage("assistant", payload.text || payload.event || "Agent 状态已更新");
  }
});

function handleResponse(message) {
  if (!message.ok) { status.textContent = message.error || "手机端请求失败"; return; }
  const result = message.result || {};
  switch (message.command) {
    case "state.list": renderState(result); break;
    case "project.files": renderFiles(result.files || []); break;
    case "file.read":
      activeFile = result.path; activeFileHash = result.sha256;
      fileName.textContent = result.path; editor.value = decodeURIComponent(escape(atob(result.content)));
      save.disabled = true; break;
    case "file.write":
      activeFileHash = result.sha256; save.disabled = true; status.textContent = "文件已安全保存"; break;
    case "chat.send":
      activeSession = result.sessionId || activeSession; status.textContent = "Agent 任务已在手机启动"; break;
  }
}

function renderState(state) {
  projects.className = "list"; projects.innerHTML = "";
  for (const project of state.projects || []) {
    const button = document.createElement("button"); button.className = "list-button"; button.textContent = project.name;
    button.onclick = () => selectProject(project); projects.append(button);
  }
  sessions.className = "list"; sessions.innerHTML = "";
  for (const session of state.sessions || []) {
    const button = document.createElement("button"); button.className = "list-button";
    button.textContent = `${session.title} · ${session.state}`;
    button.onclick = () => { activeSession = session.id; $("#title").textContent = session.title; };
    sessions.append(button);
  }
}

async function selectProject(project) {
  activeProject = project; $("#title").textContent = project.name;
  $("#stream").classList.add("hidden"); $("#workspace").classList.remove("hidden");
  $("#breadcrumbs").textContent = `${project.name} /`;
  await invoke("send_remote_command", { command: "project.files", payload: { projectId: project.id, path: "" } });
}

function renderFiles(items) {
  files.innerHTML = "";
  for (const item of items) {
    const button = document.createElement("button"); button.className = "file-row";
    button.textContent = `${item.directory ? "📁" : "📄"} ${item.name}`;
    button.onclick = async () => {
      if (item.directory) await invoke("send_remote_command", { command: "project.files", payload: { projectId: activeProject.id, path: item.path } });
      else await invoke("send_remote_command", { command: "file.read", payload: { projectId: activeProject.id, path: item.path } });
    };
    files.append(button);
  }
}

function appendMessage(role, text) {
  $("#workspace").classList.add("hidden"); $("#stream").classList.remove("hidden");
  const card = document.createElement("article"); card.className = `message ${role}`; card.textContent = text;
  $("#stream").append(card); card.scrollIntoView({ behavior: "smooth" });
}
