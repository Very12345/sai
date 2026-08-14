const pathParts = location.pathname.split('/').filter(Boolean);
const repo = location.hostname.endsWith('github.io') && pathParts[0]
  ? `${location.hostname.split('.')[0]}/${pathParts[0]}`
  : 'Very12345/sai';
const repoUrl = `https://github.com/${repo}`;
document.querySelectorAll('[data-repo]').forEach(a => a.href = repoUrl);
document.querySelectorAll('[data-security]').forEach(a => a.href = `${repoUrl}/blob/main/SECURITY.md`);
document.querySelectorAll('[data-mobile-download],[data-desktop-download]').forEach(a => a.href = `${repoUrl}/releases`);
document.getElementById('year').textContent = `© ${new Date().getFullYear()}`;
const observer = new IntersectionObserver(entries => entries.forEach(entry => {
  if (entry.isIntersecting) entry.target.classList.add('visible');
}), { threshold: .12 });
document.querySelectorAll('.reveal').forEach(el => observer.observe(el));
if (!repo.startsWith('OWNER/')) {
  fetch(`https://api.github.com/repos/${repo}/releases?per_page=10`, { headers: { Accept: 'application/vnd.github+json' } })
    .then(r => r.ok ? r.json() : Promise.reject())
    .then(releases => {
      const release = releases.find(item => !item.draft);
      if (!release) throw new Error('no release');
      document.getElementById('release-note').textContent = `最新版本 ${release.tag_name} · 发布于 ${new Date(release.published_at).toLocaleDateString('zh-CN')}`;
      const mobile = release.assets.find(a => /arm64.*\.apk$/i.test(a.name));
      const desktop = release.assets.find(a => /setup.*\.exe$|\.msi$/i.test(a.name));
      if (mobile) document.querySelectorAll('[data-mobile-download]').forEach(a => a.href = mobile.browser_download_url);
      if (desktop) document.querySelectorAll('[data-desktop-download]').forEach(a => a.href = desktop.browser_download_url);
    })
    .catch(() => { document.getElementById('release-note').textContent = '前往 GitHub Releases 获取最新构建与校验文件'; });
} else document.getElementById('release-note').textContent = '部署后将自动连接 GitHub Releases';
