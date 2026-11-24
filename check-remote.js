// Проверка внешнего доступа: WebSocket + API на боевом сервере.
const base = process.env.HC_BASE || 'https://headsetconnector.alwaysdata.net'

async function ping() {
  const res = await fetch(base + '/api/ping')
  const data = await res.json()
  console.log('api/ping', res.status, JSON.stringify(data))
}

async function wsCheck() {
  const ws = new WebSocket('wss://headsetconnector.alwaysdata.net/ws')
  const result = await new Promise((resolve) => {
    const timer = setTimeout(() => resolve({ ok: false, error: 'timeout' }), 10000)
    ws.onopen = () => {
      clearTimeout(timer)
      ws.send(JSON.stringify({ type: 'ping' }))
      setTimeout(() => { ws.close(); resolve({ ok: true }) }, 800)
    }
    ws.onerror = () => { clearTimeout(timer); resolve({ ok: false, error: 'ws error' }) }
    ws.onmessage = (e) => { clearTimeout(timer); ws.close(); resolve({ ok: true, msg: e.data }) }
  })
  console.log('wss check', JSON.stringify(result))
}

async function main() {
  await ping()
  await wsCheck()
}
main()
