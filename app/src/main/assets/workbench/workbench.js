(() => {
  const mode = new URLSearchParams(location.search).get('mode') || 'files';
  const $ = (id) => document.getElementById(id);
  const page = $(mode);
  page?.classList.remove('hidden');
  let state = {};
  let selectedPath = null;
  let toastTimer;
  let terminal;
  let renderedTerminal = '';
  let fullPathText = '/';

  const send = (action, payload = {}) => {
    if (!window.saiBridge) return;
    window.saiBridge.postMessage(JSON.stringify({action, ...payload}));
  };
  const toast = (text) => {
    if (!text) return;
    const node = $('toast'); node.textContent = text; node.classList.add('show');
    clearTimeout(toastTimer); toastTimer = setTimeout(() => node.classList.remove('show'), 2200);
  };
  const bytes = (value) => {
    if (!Number.isFinite(value)) return '—';
    const units = ['B','KB','MB','GB']; let size = value; let unit = 0;
    while (size >= 1024 && unit < units.length - 1) { size /= 1024; unit++; }
    return `${size.toFixed(unit ? 1 : 0)} ${units[unit]}`;
  };
  const icon = (item) => {
    if (item.directory) return '📁';
    const ext = item.path.split('.').pop().toLowerCase();
    if (['png','jpg','jpeg','gif','webp','svg'].includes(ext)) return '🖼️';
    if (ext === 'pdf') return '📕';
    if (['zip','tar','gz','xz','7z'].includes(ext)) return '🗜️';
    if (['mp4','mkv','webm','mov'].includes(ext)) return '🎞️';
    if (['md','txt','rst'].includes(ext)) return '📄';
    return '📜';
  };
  const updateToolbar = () => {
    document.querySelectorAll('#fileToolbar [data-action="copy"],#fileToolbar [data-action="cut"],#fileToolbar [data-action="share"],#fileToolbar [data-action="trash"]').forEach((button) => button.disabled = !selectedPath);
    const paste = document.querySelector('#fileToolbar [data-action="paste"]'); if (paste) paste.disabled = !state.clipboard;
  };
  const fitPathText = () => {
    const node = $('pathText');
    if (!node) return;
    node.textContent = fullPathText;
    if (node.scrollWidth <= node.clientWidth) return;
    const parts = fullPathText.split('/').filter(Boolean);
    let tail = `/${parts.pop() || ''}`;
    node.textContent = `…${tail}`;
    while (parts.length) {
      const candidate = `/${parts[parts.length - 1]}${tail}`;
      node.textContent = `…${candidate}`;
      if (node.scrollWidth > node.clientWidth) {
        node.textContent = `…${tail}`;
        break;
      }
      tail = candidate;
      parts.pop();
    }
    while (node.scrollWidth > node.clientWidth && tail.length > 2) {
      tail = tail.slice(1);
      node.textContent = `…${tail}`;
    }
  };
  const renderFiles = () => {
    const fullPath = `/${state.rootTitle || 'sai'}${state.directory ? '/' + state.directory : ''}`;
    fullPathText = fullPath;
    fitPathText();
    requestAnimationFrame(fitPathText);
    $('pathText').title = fullPath;
    $('searchInput').value = state.search || '';
    $('storage').textContent = `已用 ${bytes(state.usedBytes)} · 可用 ${bytes(state.availableBytes)}`;
    $('places').innerHTML = (state.locations || []).map((place) => `<button class="place ${place.id === state.rootId ? 'active' : ''}" data-root="${encodeURIComponent(place.id)}"><span class="place-icon">${place.id === 'sai' ? '⌂' : '▰'}</span><span>${escapeHtml(place.title)}</span></button>`).join('');
    const files = state.files || [];
    $('fileGrid').innerHTML = files.length ? files.map((item) => {
      const name = item.path.split('/').pop();
      return `<button class="file-card ${item.path === selectedPath ? 'selected' : ''}" data-path="${encodeURIComponent(item.path)}" data-dir="${item.directory}" title="${escapeHtml(name)}"><span class="file-icon">${icon(item)}</span><span class="file-name">${escapeHtml(name)}</span>${item.directory ? '' : `<span class="file-size">${bytes(item.size)}</span>`}</button>`;
    }).join('') : '<div class="empty">此位置为空</div>';
    updateToolbar();
  };
  const renderTerminal = () => {
    if (!terminal) initTerminal();
    $('terminalTabs').innerHTML = (state.tabs || []).map((tab) => `<div class="terminal-tab ${tab.id === state.selectedTabId ? 'active' : ''}" data-tab="${tab.id}"><button class="terminal-tab-select" data-select-tab="${tab.id}">${escapeHtml(tab.title)}</button><button class="terminal-tab-close" data-close-tab="${tab.id}" aria-label="关闭 ${escapeHtml(tab.title)}">×</button></div>`).join('');
    $('terminalPower').textContent = state.connected ? '■' : '▶';
    const output = state.output || '';
    if (output !== renderedTerminal) {
      if (output.startsWith(renderedTerminal)) terminal.write(output.slice(renderedTerminal.length));
      else { terminal.reset(); terminal.write(output); }
      renderedTerminal = output;
    }
    fitTerminal();
  };
  const initTerminal = () => {
    terminal = new Terminal({cursorBlink:true,convertEol:false,scrollback:8000,fontSize:13,fontFamily:'JetBrains Mono,monospace',theme:{background:'#07111f',foreground:'#d9e6f5',cursor:'#63e6cf',selectionBackground:'#355477'}});
    terminal.open($('terminalHost'));
    terminal.onData((data) => send('terminal-write', {data}));
    terminal.onResize(({cols,rows}) => send('terminal-resize',{cols,rows}));
    const focusTerminal = () => terminal?.focus();
    $('terminalHost').addEventListener('click', focusTerminal);
    $('terminalHost').addEventListener('touchend', focusTerminal, {passive:true});
    new ResizeObserver(fitTerminal).observe($('terminalHost'));
  };
  const fitTerminal = () => {
    if (!terminal) return;
    const host = $('terminalHost'); const cols = Math.max(20, Math.floor((host.clientWidth - 14) / 8)); const rows = Math.max(5, Math.floor((host.clientHeight - 10) / 17));
    if (terminal.cols !== cols || terminal.rows !== rows) terminal.resize(cols, rows);
  };
  const escapeHtml = (text) => String(text ?? '').replace(/[&<>"']/g, (char) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
  const fitEditorDialog = () => {
    const dialog = $('editorDialog');
    if (!dialog?.open) return;
    const viewport = window.visualViewport;
    const available = Math.max(280, (viewport?.height || window.innerHeight) - 24);
    const height = Math.min(760, available);
    dialog.style.inset = 'auto';
    dialog.style.left = '0';
    dialog.style.right = '0';
    dialog.style.top = `${(viewport?.offsetTop || 0) + 12}px`;
    dialog.style.height = `${height}px`;
    dialog.style.maxHeight = `${height}px`;
    dialog.style.margin = '0 auto';
  };
  const ask = (title, value, callback) => {
    $('dialogTitle').textContent = title; $('dialogInput').value = value || '';
    const dialog = $('textDialog'); dialog.showModal(); setTimeout(() => $('dialogInput').focus(), 80);
    dialog.onclose = () => { if (dialog.returnValue === 'default') callback($('dialogInput').value); };
  };

  window.saiWorkbench = {
    render(next) {
      state = next || {};
      if (state.message) toast(state.message);
      if (mode === 'files') {
        renderFiles();
        if (state.selectedFile) {
          $('editorTitle').textContent = state.selectedFile;
          $('editorText').value = state.editorText || '';
          $('editorText').readOnly = !!state.editorReadOnly;
          $('editorBadge').textContent = state.editorReadOnly ? '只读' : '';
          $('editorSave').disabled = !!state.editorReadOnly;
          if (!$('editorDialog').open) {
            $('editorDialog').showModal();
            fitEditorDialog();
            // A modal dialog focuses its first control by default. On Android
            // that opened the IME before the user chose to edit the file.
            $('editorDialog').focus({preventScroll:true});
          } else fitEditorDialog();
        }
      } else renderTerminal();
    },
  };

  if (window.saiBridge) window.saiBridge.onmessage = (event) => {
    try { const reply = JSON.parse(event.data); if (reply.message) toast(reply.message); } catch (_) {}
  };

  $('placesButton')?.addEventListener('click', () => $('places').classList.toggle('open'));
  $('pathButton')?.addEventListener('click', () => ask('打开路径', $('pathText').textContent, (value) => send('open-path',{value})));
  let searchTimer; $('searchInput')?.addEventListener('input', (event) => { clearTimeout(searchTimer); searchTimer = setTimeout(() => send('search',{value:event.target.value}),180); });
  $('places')?.addEventListener('click', (event) => { const button = event.target.closest('[data-root]'); if (button) { send('select-root',{id:decodeURIComponent(button.dataset.root)}); $('places').classList.remove('open'); selectedPath=null; } });
  let navigationLockedUntil = 0;
  $('fileGrid')?.addEventListener('click', (event) => {
    if (Date.now() < navigationLockedUntil) return;
    const card=event.target.closest('[data-path]'); if(!card)return;
    const path=decodeURIComponent(card.dataset.path);
    navigationLockedUntil=Date.now()+450;
    send(card.dataset.dir==='true'?'open-directory':'open-file',{path});
    selectedPath=null;
  });
  $('fileGrid')?.addEventListener('contextmenu', (event) => {
    const card=event.target.closest('[data-path]'); if(!card)return;
    event.preventDefault();
    selectedPath=decodeURIComponent(card.dataset.path);
    renderFiles();
    toast('已选中；可复制、剪切、分享或移入回收站');
  });
  $('fileToolbar')?.addEventListener('click', (event) => {
    const action=event.target.closest('[data-action]')?.dataset.action; if(!action)return;
    if(action==='up'||action==='refresh'||action==='paste'||action==='import'||action==='hidden') send(action);
    else if(action==='copy'||action==='cut'||action==='share'||action==='trash') send(action,{path:selectedPath});
    else if(action==='new-folder'||action==='new-file') ask(action==='new-folder'?'新建文件夹':'新建文件','',(name)=>send(action,{name}));
  });
  $('editorDialog')?.addEventListener('close', () => {
    if ($('editorDialog').returnValue === 'default') send('editor-save',{text:$('editorText').value});
    else send('editor-close');
  });
  window.visualViewport?.addEventListener('resize', fitEditorDialog);
  window.visualViewport?.addEventListener('scroll', fitEditorDialog);
  window.addEventListener('resize', fitPathText);
  $('newTerminal')?.addEventListener('click',()=>send('terminal-new'));
  $('terminalPower')?.addEventListener('click',()=>send(state.connected?'terminal-stop':'terminal-start'));
  $('terminalTabs')?.addEventListener('click',(event)=>{
    const close=event.target.closest('[data-close-tab]');
    if(close){send('terminal-close',{id:close.dataset.closeTab});return;}
    const tab=event.target.closest('[data-select-tab]')||event.target.closest('[data-tab]');
    const id=tab?.dataset.selectTab||tab?.dataset.tab;
    if(id){renderedTerminal='';terminal?.reset();send('terminal-select',{id});}
  });
  document.querySelector('.terminal-keys')?.addEventListener('click',(event)=>{const key=event.target.closest('[data-seq]');if(key)send('terminal-write',{data:key.dataset.seq});});
  send('ready',{mode});
})();
