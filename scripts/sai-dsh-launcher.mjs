import { createServer, request as httpRequest } from 'node:http'
import { connect as netConnect } from 'node:net'
import { spawn, spawnSync } from 'node:child_process'
import {
  existsSync,
  cpSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  renameSync,
  rmSync,
  writeFileSync,
} from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { timingSafeEqual } from 'node:crypto'

const here = dirname(fileURLToPath(import.meta.url))
const externalPort = Number(process.env.SAI_DSH_PORT || '3080')
const internalPort = externalPort + 1
const secret = process.env.SAI_WEB_TOKEN || ''
if (secret.length < 32) throw new Error('SAI_WEB_TOKEN is missing')

const cli = join(here, 'node_modules', '@deepseek-ai', 'dsh', 'lib', 'bin.js')
const patch = join(here, 'sai.cordis.patch.yml')
const dshHome = process.env.DSH_HOME || join(process.env.HOME || '.', '.dsh')
const extensionsPatch = join(dshHome, 'sai-extensions.cordis.patch.yml')
const profileRoot = join(dshHome, 'profiles', 'web')
if (!existsSync(join(profileRoot, 'package.json'))) {
  const initialized = spawnSync(process.execPath, [...process.execArgv, cli, 'web', '--dump-default-config'], {
    env: process.env,
    stdio: 'ignore',
  })
  if (initialized.status !== 0) throw new Error(`Unable to initialize DSH web profile (${initialized.status})`)
}
const bundledSaiScope = join(here, 'node_modules', '@sai')
const profileSaiScope = join(profileRoot, 'node_modules', '@sai')
mkdirSync(profileSaiScope, { recursive: true })
const profilePackagePath = join(profileRoot, 'package.json')
const profilePackage = JSON.parse(readFileSync(profilePackagePath, 'utf8'))
profilePackage.dependencies ||= {}
let profilePackageChanged = false
for (const packageName of readdirSync(bundledSaiScope)) {
  const target = join(profileSaiScope, packageName)
  const source = join(bundledSaiScope, packageName)
  const dependencyName = `@sai/${packageName}`
  const dependencyVersion = JSON.parse(readFileSync(join(source, 'package.json'), 'utf8')).version
  if (profilePackage.dependencies[dependencyName] !== dependencyVersion) {
    profilePackage.dependencies[dependencyName] = dependencyVersion
    profilePackageChanged = true
  }
  rmSync(target, { recursive: true, force: true })
  cpSync(source, target, { recursive: true, force: true })
}
if (profilePackageChanged) {
  const temporaryProfilePackage = `${profilePackagePath}.sai.tmp`
  writeFileSync(temporaryProfilePackage, `${JSON.stringify(profilePackage, null, 2)}\n`, { mode: 0o600 })
  renameSync(temporaryProfilePackage, profilePackagePath)
}
const externalAuthority = `127.0.0.1:${externalPort}`
const child = spawn(process.execPath, [
  ...process.execArgv,
  cli,
  'web',
  '--patch', patch,
  ...existsSync(extensionsPatch) ? ['--patch', extensionsPatch] : [],
  '--host', '127.0.0.1',
  '--port', String(internalPort),
  '--trusted-host', externalAuthority,
], {
  env: process.env,
  stdio: ['ignore', 'pipe', 'pipe'],
})
child.stdout.pipe(process.stdout)
child.stderr.pipe(process.stderr)
child.on('exit', (code, signal) => {
  if (!stopping) process.stderr.write(`DSH exited (${code ?? signal})\n`)
  server.close(() => process.exit(code ?? 1))
})

const saiUiStandalone = readFileSync(join(here, 'node_modules', '@sai', 'dsh-ui', 'standalone.js'))

function equal(a, b) {
  const left = Buffer.from(a || '')
  const right = Buffer.from(b || '')
  return left.length === right.length && timingSafeEqual(left, right)
}

function authorized(headers) {
  const cookie = String(headers.cookie || '').split(';').map(v => v.trim()).find(v => v.startsWith('sai_auth='))
  return equal(cookie?.slice('sai_auth='.length), encodeURIComponent(secret))
}

function healthAuthorized(headers) {
  return equal(String(headers.authorization || '').replace(/^Bearer /, ''), secret)
}

function proxy(req, res, path = req.url) {
  // The browser sees the authenticated outer authority. Preserve that fixed
  // authority through the private hop so DSH's Host/Origin anti-rebinding
  // fence validates the real browser origin instead of the hidden port.
  const headers = { ...req.headers, host: externalAuthority }
  delete headers.cookie
  if (req.method === 'GET' && path === '/') headers['accept-encoding'] = 'identity'
  const upstream = httpRequest({ host: '127.0.0.1', port: internalPort, method: req.method, path, headers }, response => {
    const injectUi = req.method === 'GET' && path === '/' &&
      String(response.headers['content-type'] || '').includes('text/html')
    if (!injectUi) {
      res.writeHead(response.statusCode || 502, response.headers)
      response.pipe(res)
      return
    }
    const chunks = []
    response.on('data', chunk => chunks.push(chunk))
    response.on('end', () => {
      const html = Buffer.concat(chunks).toString('utf8')
      const body = html.replace('</body>', '<script src="/__sai_plugin/ui.js"></script></body>')
      const responseHeaders = { ...response.headers, 'content-length': Buffer.byteLength(body) }
      delete responseHeaders['content-encoding']
      delete responseHeaders['transfer-encoding']
      res.writeHead(response.statusCode || 502, responseHeaders)
      res.end(body)
    })
  })
  upstream.on('error', error => {
    if (!res.headersSent) res.writeHead(503, { 'content-type': 'text/plain; charset=utf-8' })
    res.end(`DSH unavailable: ${error.code || 'proxy-error'}`)
  })
  req.pipe(upstream)
}

const server = createServer((req, res) => {
  if (req.url === '/__sai_health' && healthAuthorized(req.headers)) return proxy(req, res, '/')
  if (!authorized(req.headers)) {
    res.writeHead(401, { 'content-type': 'text/plain; charset=utf-8', 'cache-control': 'no-store' })
    return res.end('Unauthorized')
  }
  if (req.url === '/__sai_plugin/ui.js') {
    res.writeHead(200, {
      'content-type': 'text/javascript; charset=utf-8',
      'cache-control': 'no-store',
      'content-length': saiUiStandalone.length,
    })
    return res.end(saiUiStandalone)
  }
  proxy(req, res)
})

server.on('upgrade', (req, socket, head) => {
  if (!authorized(req.headers)) return socket.destroy()
  const upstream = netConnect(internalPort, '127.0.0.1', () => {
    const headers = Object.entries(req.headers)
      .filter(([key]) => !['cookie', 'host'].includes(key.toLowerCase()))
      .map(([key, value]) => `${key}: ${Array.isArray(value) ? value.join(', ') : value}`)
    headers.push(`host: ${externalAuthority}`)
    upstream.write(`${req.method} ${req.url} HTTP/${req.httpVersion}\r\n${headers.join('\r\n')}\r\n\r\n`)
    if (head.length) upstream.write(head)
    socket.pipe(upstream).pipe(socket)
  })
  upstream.on('error', () => socket.destroy())
})

server.listen(externalPort, '127.0.0.1')
let stopping = false
function stop() {
  if (stopping) return
  stopping = true
  child.kill('SIGTERM')
  server.close(() => process.exit(0))
  setTimeout(() => child.kill('SIGKILL'), 3000).unref()
}
process.on('SIGTERM', stop)
process.on('SIGINT', stop)
