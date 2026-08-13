import { createServer, request as httpRequest } from 'node:http'
import { connect as netConnect } from 'node:net'
import { spawn } from 'node:child_process'
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
const child = spawn(process.execPath, [cli, 'web', '--patch', patch, '--host', '127.0.0.1', '--port', String(internalPort)], {
  env: process.env,
  stdio: ['ignore', 'pipe', 'pipe'],
})
child.stdout.pipe(process.stdout)
child.stderr.pipe(process.stderr)
child.on('exit', (code, signal) => {
  if (!stopping) process.stderr.write(`DSH exited (${code ?? signal})\n`)
  server.close(() => process.exit(code ?? 1))
})

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
  const headers = { ...req.headers, host: `127.0.0.1:${internalPort}` }
  delete headers.cookie
  const upstream = httpRequest({ host: '127.0.0.1', port: internalPort, method: req.method, path, headers }, response => {
    res.writeHead(response.statusCode || 502, response.headers)
    response.pipe(res)
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
  proxy(req, res)
})

server.on('upgrade', (req, socket, head) => {
  if (!authorized(req.headers)) return socket.destroy()
  const upstream = netConnect(internalPort, '127.0.0.1', () => {
    const headers = Object.entries(req.headers)
      .filter(([key]) => !['cookie', 'host'].includes(key.toLowerCase()))
      .map(([key, value]) => `${key}: ${Array.isArray(value) ? value.join(', ') : value}`)
    headers.push(`host: 127.0.0.1:${internalPort}`)
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
