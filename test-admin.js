'use strict'

/**
 * Проверка админ-панели: вход, обзор, поиск, бан, разбан, сброс пароля,
 * удаление пользователя, комнаты, выход.
 *
 * Требует сервер с ADMIN_PASSWORD (по умолчанию для теста admin123):
 *   $env:ADMIN_PASSWORD="admin123"; node server.js
 *   node test-admin.js http://127.0.0.1:8787 admin123
 */

const base = process.argv[2] || 'http://127.0.0.1:8787'
const adminPassword = process.argv[3] || process.env.ADMIN_PASSWORD || 'admin123'
const adminPath = process.env.ADMIN_PATH || '/admin'
let failures = 0

function check(name, condition, extra = '') {
  if (condition) {
    console.log(`  ok   ${name}`)
  } else {
    failures += 1
    console.log(`  FAIL ${name} ${extra}`)
  }
}

async function call(method, path, { token, admin, body } = {}) {
  const headers = { 'Content-Type': 'application/json', 'X-Admin-Path': adminPath }
  if (token) headers.Authorization = `Bearer ${token}`
  if (admin) headers['X-Admin-Token'] = admin
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

async function main() {
  const suffix = Date.now().toString().slice(-6)
  const username = `victim_${suffix}`
  const password = 'secret123'

  console.log(`Сервер: ${base}`)

  const page = await fetch(base + adminPath)
  const html = await page.text()
  check('страница панели отдаётся', page.status === 200 && html.includes('Панель управления'))

  const bad = await call('POST', '/api/admin/login', { body: { password: 'nope' } })
  check('неверный пароль панели (401)', bad.status === 401)

  const login = await call('POST', '/api/admin/login', { body: { password: adminPassword } })
  check('вход в панель', login.status === 200 && !!login.data.token, JSON.stringify(login.data))
  const admin = login.data.token

  const noAuth = await call('GET', '/api/admin/overview')
  check('без токена панель закрыта (401)', noAuth.status === 401)

  const overview = await call('GET', '/api/admin/overview', { admin })
  check(
    'обзор со статистикой',
    overview.status === 200 && typeof overview.data.stats.users === 'number',
    JSON.stringify(overview.data),
  )

  const user = await call('POST', '/api/register', {
    body: {
      username,
      password,
      displayName: 'Подопытный',
      email: `victim_${Date.now()}@example.com`,
      headset: 'quest2',
    },
  })
  check('создан тестовый пользователь', user.status === 201, JSON.stringify(user.data))
  const userId = user.data.user.id
  const userToken = user.data.token

  const found = await call('GET', `/api/admin/users?q=${username}`, { admin })
  check(
    'поиск в панели',
    found.status === 200 && found.data.users.some((u) => u.id === userId),
    JSON.stringify(found.data),
  )

  const badge = await call('POST', `/api/admin/users/${userId}/badge`, {
    admin,
    body: { badges: ['developer'] },
  })
  check(
    'значки выданы',
    badge.status === 200 && badge.data.badges.length === 1 && badge.data.badgeLabels.includes('Разработчик'),
    JSON.stringify(badge.data),
  )

  const badBadge = await call('POST', `/api/admin/users/${userId}/badge`, {
    admin,
    body: { badges: ['vip'] },
  })
  check('удалённый значок отклонён (400)', badBadge.status === 400)

  const removedBadge = await call('POST', `/api/admin/users/${userId}/badge`, {
    admin,
    body: { badges: [] },
  })
  check('значки сняты', removedBadge.status === 200 && removedBadge.data.badges.length === 0)

  const ban = await call('POST', `/api/admin/users/${userId}/ban`, { admin, body: { duration: 60 } })
  check('бан на время', ban.status === 200 && ban.data.banned === true && !!ban.data.bannedUntil)

  const bannedLogin = await call('POST', '/api/login', { body: { username, password } })
  check('забаненный не входит (403)', bannedLogin.status === 403, JSON.stringify(bannedLogin.data))

  const bannedToken = await call('GET', '/api/me', { token: userToken })
  check('старый токен забаненного не работает (401)', bannedToken.status === 401)

  const unban = await call('POST', `/api/admin/users/${userId}/unban`, { admin })
  check('разбан', unban.status === 200 && unban.data.banned === false)

  const afterUnban = await call('POST', '/api/login', { body: { username, password } })
  check('после разбана вход работает', afterUnban.status === 200)

  const reset = await call('POST', `/api/admin/users/${userId}/reset`, { admin })
  const tempPassword = reset.data.password
  check('сброс пароля выдаёт новый', reset.status === 200 && typeof tempPassword === 'string' && tempPassword.length >= 6)

  const oldLogin = await call('POST', '/api/login', { body: { username, password } })
  check('старый пароль больше не работает (401)', oldLogin.status === 401)

  const newLogin = await call('POST', '/api/login', {
    body: { username, password: tempPassword },
  })
  check('новый пароль работает', newLogin.status === 200)

  const room = await call('POST', '/api/chats/room', {
    token: newLogin.data.token,
    body: { title: `Комната ${suffix}`, isPublic: true },
  })
  check('комната создана', room.status === 201, JSON.stringify(room.data))
  const roomId = room.data.chat.id

  const rooms = await call('GET', '/api/admin/rooms', { admin })
  check(
    'комната видна в панели',
    rooms.status === 200 && rooms.data.rooms.some((r) => r.id === roomId),
    JSON.stringify(rooms.data),
  )

  const deleteRoom = await call('DELETE', `/api/admin/rooms/${roomId}`, { admin })
  check('комната удалена', deleteRoom.status === 200)

  const roomsAfter = await call('GET', '/api/admin/rooms', { admin })
  check(
    'удалённой комнаты нет',
    !roomsAfter.data.rooms.some((r) => r.id === roomId),
  )

  const deleteUser = await call('DELETE', `/api/admin/users/${userId}`, { admin })
  check('пользователь удалён', deleteUser.status === 200)

  const searchAfter = await call('GET', `/api/admin/users?q=${username}`, { admin })
  check(
    'удалённого нет в поиске',
    searchAfter.status === 200 && !searchAfter.data.users.some((u) => u.id === userId),
  )

  const logout = await call('POST', '/api/admin/logout', { admin })
  check('выход из панели', logout.status === 200)

  const afterLogout = await call('GET', '/api/admin/overview', { admin })
  check('токен после выхода не работает (401)', afterLogout.status === 401)

  console.log(failures === 0 ? '\nВСЁ ОК' : `\nПРОВАЛОВ: ${failures}`)
  process.exit(failures === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('Тест упал:', e)
  process.exit(1)
})
