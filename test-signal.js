'use strict'

/**
 * Проверка WebSocket-сигналинга голосовых комнат.
 * Запуск: node test-signal.js [http://127.0.0.1:8787]
 */

const base = process.argv[2] || 'http://127.0.0.1:8787'
const wsBase = base.replace(/^http/, 'ws') + '/ws'
let failures = 0

function check(name, condition, extra = '') {
  if (condition) {
    console.log(`  ok   ${name}`)
  } else {
    failures += 1
    console.log(`  FAIL ${name} ${extra}`)
  }
}

async function api(method, path, { token, body } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(base + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await response.text()
  let data = {}
  try {
    data = text ? JSON.parse(text) : {}
  } catch (_) {
    data = { raw: text }
  }
  return { status: response.status, data }
}

function connect(url) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url)
    const inbox = []
    const waiters = []
    ws.onmessage = (event) => {
      const msg = JSON.parse(event.data)
      const index = waiters.findIndex((w) => w.pred(msg))
      if (index >= 0) {
        const w = waiters.splice(index, 1)[0]
        clearTimeout(w.timer)
        w.resolve(msg)
      } else {
        inbox.push(msg)
      }
    }
    ws.onopen = () => {
      resolve({
        ws,
        next: (pred, timeout = 5000) =>
          new Promise((res, rej) => {
            const idx = inbox.findIndex(pred)
            if (idx >= 0) {
              res(inbox.splice(idx, 1)[0])
              return
            }
            const w = {
              pred,
              resolve: res,
              timer: setTimeout(() => {
                const i = waiters.indexOf(w)
                if (i >= 0) waiters.splice(i, 1)
                rej(new Error('timeout'))
              }, timeout),
            }
            waiters.push(w)
          }),
        send: (obj) => ws.send(JSON.stringify(obj)),
        close: () => ws.close(),
      })
    }
    ws.onerror = () => reject(new Error('Не удалось подключиться по WebSocket'))
  })
}

async function main() {
  const suffix = Date.now().toString().slice(-6)
  const a = await api('POST', '/api/register', {
    body: { username: `sa_${suffix}`, password: 'secret123', displayName: 'А', email: `sa_${suffix}@e.com`, headset: 'pico4' },
  })
  const b = await api('POST', '/api/register', {
    body: { username: `sb_${suffix}`, password: 'secret123', displayName: 'Б', email: `sb_${suffix}@e.com`, headset: 'quest3' },
  })
  const aToken = a.data.token
  const bToken = b.data.token
  const aId = a.data.user.id
  const bId = b.data.user.id

  const room = await api('POST', '/api/chats/room', { token: aToken, body: { title: 'Голос', isPublic: true } })
  const roomId = room.data.chat.id
  await api('POST', `/api/chats/${roomId}/join`, { token: bToken })

  const A = await connect(wsBase)
  const B = await connect(wsBase)
  check('WebSocket открывается', true)

  A.send({ type: 'join', token: aToken, roomId })
  const joinedA = await A.next((m) => m.type === 'joined')
  check('Алиса входит в комнату', joinedA.users.some((u) => u.id === aId), JSON.stringify(joinedA))

  B.send({ type: 'join', token: bToken, roomId })
  await B.next((m) => m.type === 'joined')
  const peerAtA = await A.next((m) => m.type === 'peer-joined' && m.user.id === bId)
  check('Алиса видит Боба', !!peerAtA)

  A.send({ type: 'offer', to: bId, sdp: 'sdp-from-a' })
  const offerAtB = await B.next((m) => m.type === 'offer')
  check('офер доходит до Боба', offerAtB.from === aId && offerAtB.sdp === 'sdp-from-a')

  B.send({ type: 'answer', to: aId, sdp: 'sdp-from-b' })
  const answerAtA = await A.next((m) => m.type === 'answer')
  check('ответ доходит до Алисы', answerAtA.from === bId && answerAtA.sdp === 'sdp-from-b')

  B.send({ type: 'ice', to: aId, candidate: 'cand1' })
  const iceAtA = await A.next((m) => m.type === 'ice')
  check('ICE доходит', iceAtA.candidate === 'cand1')

  A.send({ type: 'speaking', speaking: true })
  const speakAtB = await B.next((m) => m.type === 'speaking')
  check('статус «говорит» доходит', speakAtB.userId === aId && speakAtB.speaking === true)

  B.close()
  const leftAtA = await A.next((m) => m.type === 'peer-left')
  check('выход Боба виден Алисе', leftAtA.userId === bId)

  A.close()

  console.log(failures === 0 ? '\nВСЁ ОК' : `\nПРОВАЛОВ: ${failures}`)
  process.exit(failures === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('Тест упал:', e)
  process.exit(1)
})
