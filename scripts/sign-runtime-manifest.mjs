import { createPrivateKey, sign } from 'node:crypto'
import { readFile, writeFile } from 'node:fs/promises'

const [, , source, destination, signatureDestination] = process.argv
if (!source || !destination || !signatureDestination) {
  throw new Error('usage: node sign-runtime-manifest.mjs SOURCE DESTINATION SIGNATURE')
}
const privateKey = process.env.SAI_RUNTIME_ED25519_PRIVATE_KEY
if (!privateKey) throw new Error('SAI_RUNTIME_ED25519_PRIVATE_KEY is required; unsigned runtime releases are forbidden')
const payload = await readFile(source)
const signature = sign(null, payload, createPrivateKey(privateKey)).toString('base64')
await writeFile(destination, payload)
await writeFile(signatureDestination, `${signature}\n`)
