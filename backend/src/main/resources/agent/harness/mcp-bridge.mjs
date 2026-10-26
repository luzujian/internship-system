import readline from 'node:readline'

// A stdio MCP transport only. Business decisions and authorization remain in the backend.
const endpoint = process.env.DEEPINTERN_TOOL_ENDPOINT
const lease = process.env.DEEPINTERN_TOOL_LEASE
async function bridge(body) {
  const response = await fetch(endpoint, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Tool-Lease': lease },
    body: JSON.stringify(body), signal: AbortSignal.timeout(120000),
  })
  const value = await response.json()
  if (!response.ok) return { isError: true, content: [{ type: 'text', text: JSON.stringify(value) }] }
  return value
}
async function handle(message) {
  if (message.id === undefined) return
  let result
  switch (message.method) {
    case 'initialize':
      result = { protocolVersion: message.params?.protocolVersion || '2025-03-26', capabilities: { tools: {} }, serverInfo: { name: 'deepintern-business', version: '1.0.0' } }
      break
    case 'ping': result = {}; break
    case 'tools/list': result = await bridge({ operation: 'list' }); break
    case 'tools/call': {
      const value = await bridge({ operation: 'call', name: message.params?.name, arguments: message.params?.arguments || {} })
      result = value.isError ? value : { content: [{ type: 'text', text: JSON.stringify(value.result) }] }
      break
    }
    default:
      process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id: message.id, error: { code: -32601, message: 'Method not supported' } }) + '\n')
      return
  }
  process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id: message.id, result }) + '\n')
}
const lines = readline.createInterface({ input: process.stdin })
for await (const line of lines) {
  let message
  try { message = JSON.parse(line); await handle(message) }
  catch { if (message?.id !== undefined) process.stdout.write(JSON.stringify({ jsonrpc: '2.0', id: message.id, error: { code: -32603, message: 'Tool transport failed' } }) + '\n') }
}
