'use strict'

// Удаляет тестовые аккаунты (alice_/bob_/victim_), оставшиеся после прогонов
// test-api.js и test-admin.js на реальном сервере.
//
//   node cleanup-test-users.js https://адрес ПАРОЛЬ_ПАНЕЛИ

const base = (process.argv[2] || '').replace(/\/$/, '')
const password = process.argv[3]

if (!base || !password) {
  console.error('Использование: node cleanup-test-users.js https://адрес ПАРОЛЬ_ПАНЕЛИ')
  process.exit(1)
}

async function main() {
  const login = await fetch(base + '/api/admin/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password }),
  })
  if (!login.ok) throw new Error('Вход в панель не удался: HTTP ' + login.status)
  const { token } = await login.json()
  const headers = { 'X-Admin-Token': token }

  const list = await fetch(base + '/api/admin/users?q=', { headers })
  if (!list.ok) throw new Error('Список пользователей: HTTP ' + list.status)
  const { users } = await list.json()

  const test = users.filter((user) => /^(alice_|bob_|victim_)/.test(user.username))
  for (const user of test) {
    const res = await fetch(base + '/api/admin/users/' + user.id, { method: 'DELETE', headers })
    console.log(`  ${res.ok ? 'удалён' : 'ОШИБКА'} @${user.username}`)
  }

  const rooms = await fetch(base + '/api/admin/rooms', { headers })
  const roomList = rooms.ok ? (await rooms.json()).rooms : []
  for (const room of roomList) {
    if (/^(Комната |Тестовая |Публичная )/.test(room.title) || room.members === 0) {
      const res = await fetch(base + '/api/admin/rooms/' + room.id, { method: 'DELETE', headers })
      console.log(`  ${res.ok ? 'удалена' : 'ОШИБКА'} комната «${room.title}»`)
    }
  }

  const after = await fetch(base + '/api/admin/users?q=', { headers })
  const left = (await after.json()).users
  console.log(`Пользователей осталось: ${left.length}`)
  for (const user of left) console.log(`  @${user.username} — ${user.displayName}`)
}

main().catch((error) => {
  console.error(error.message)
  process.exit(1)
})
