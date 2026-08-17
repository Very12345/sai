const pathParts = location.pathname.split('/').filter(Boolean);
const repo = location.hostname.endsWith('github.io') && pathParts[0]
  ? `${location.hostname.split('.')[0]}/${pathParts[0]}`
  : 'Very12345/sai';
const repoUrl = `https://github.com/${repo}`;
const previewTag = 'v1.3.1-preview.1';
const latestUrl = `${repoUrl}/releases/tag/${previewTag}`;
const fallbackAssets = {
  mobile: `${repoUrl}/releases/download/${previewTag}/sai-android-arm64.apk`,
  desktop: `${repoUrl}/releases/download/${previewTag}/sai-desktop-windows-setup.exe`,
  voice: `${repoUrl}/releases/download/${previewTag}/sai-voice-pack-zh-en.apk`,
  checksums: `${repoUrl}/releases/download/${previewTag}/SHA256SUMS.txt`
};

document.querySelectorAll('[data-repo]').forEach((link) => { link.href = repoUrl; });
document.querySelectorAll('[data-release-page]').forEach((link) => { link.href = latestUrl; });
document.querySelectorAll('[data-security]').forEach((link) => { link.href = `${repoUrl}/blob/main/SECURITY.md`; });
document.querySelectorAll('[data-mobile-download]').forEach((link) => { link.href = fallbackAssets.mobile; });
document.querySelectorAll('[data-desktop-download]').forEach((link) => { link.href = fallbackAssets.desktop; });
document.querySelectorAll('[data-voice-download]').forEach((link) => { link.href = fallbackAssets.voice; });
document.querySelectorAll('[data-checksums]').forEach((link) => { link.href = fallbackAssets.checksums; });
document.getElementById('year').textContent = `© ${new Date().getFullYear()}`;

const navToggle = document.querySelector('.nav-toggle');
const navLinks = document.querySelector('.nav-links');
if (navToggle && navLinks) {
  navToggle.addEventListener('click', () => {
    const open = navLinks.classList.toggle('open');
    navToggle.setAttribute('aria-expanded', String(open));
    navToggle.setAttribute('aria-label', open ? '收起导航' : '展开导航');
    navToggle.textContent = open ? '×' : '☰';
  });
  navLinks.querySelectorAll('a').forEach((link) => link.addEventListener('click', () => {
    navLinks.classList.remove('open');
    navToggle.setAttribute('aria-expanded', 'false');
    navToggle.setAttribute('aria-label', '展开导航');
    navToggle.textContent = '☰';
  }));
}

if ('IntersectionObserver' in window) {
  const observer = new IntersectionObserver((entries) => entries.forEach((entry) => {
    if (entry.isIntersecting) {
      entry.target.classList.add('visible');
      observer.unobserve(entry.target);
    }
  }), { threshold: .1, rootMargin: '0px 0px -28px' });
  document.querySelectorAll('.reveal').forEach((element) => observer.observe(element));
} else {
  document.querySelectorAll('.reveal').forEach((element) => element.classList.add('visible'));
}

const releaseNote = document.getElementById('release-note');
fetch(`https://api.github.com/repos/${repo}/releases?per_page=20`, {
  headers: { Accept: 'application/vnd.github+json' }
})
  .then((response) => response.ok ? response.json() : Promise.reject(new Error(`HTTP ${response.status}`)))
  .then((releases) => {
    const release = releases.find((item) => item.prerelease && !item.draft && /preview/i.test(item.tag_name));
    if (!release) throw new Error('没有可用的 Preview Release');
    const asset = (pattern) => release.assets.find((item) => pattern.test(item.name));
    const mobile = asset(/sai-android-arm64\.apk$|arm64.*\.apk$/i);
    const desktop = asset(/sai-desktop-windows-setup\.exe$|setup.*\.exe$|\.msi$/i);
    const voice = asset(/sai-voice-pack-zh-en\.apk$/i);
    const checksums = asset(/SHA256SUMS\.txt$/i);
    if (mobile) document.querySelectorAll('[data-mobile-download]').forEach((link) => { link.href = mobile.browser_download_url; });
    if (desktop) document.querySelectorAll('[data-desktop-download]').forEach((link) => { link.href = desktop.browser_download_url; });
    if (voice) document.querySelectorAll('[data-voice-download]').forEach((link) => { link.href = voice.browser_download_url; });
    if (checksums) document.querySelectorAll('[data-checksums]').forEach((link) => { link.href = checksums.browser_download_url; });
    const date = new Date(release.published_at).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' });
    releaseNote.querySelector('span').textContent = `${release.tag_name} · 开发预览 · ${date} 发布 · ${release.assets.length} 个可下载产物`;
  })
  .catch(() => {
    releaseNote.querySelector('span').textContent = '暂时无法读取 Preview 信息，可直接前往 GitHub Releases 下载';
  });
