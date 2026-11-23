'use strict'

/**
 * Проверка API HeadsetConnector: два пользователя, друзья, личка, комната по коду,
 * публичная комната, голосовое сообщение и скачивание медиа.
 *
 * Запуск: node test-api.js [http://127.0.0.1:8787]
 */

const base = process.argv[2] || 'http://127.0.0.1:8787'
let failures = 0

function check(name, condition, extra = '') {
  if (condition) {
    console.log(`  ok   ${name}`)
  } else {
    failures += 1
    console.log(`  FAIL ${name} ${extra}`)
  }
}

async function call(method, path, { token, body } = {}) {
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

async function main() {
  const suffix = Date.now().toString().slice(-6)
  const aliceName = `alice_${suffix}`
  const bobName = `bob_${suffix}`

  console.log(`Сервер: ${base}`)

  const ping = await call('GET', '/api/ping')
  check('ping', ping.status === 200 && ping.data.ok === true, JSON.stringify(ping.data))

  const alice = await call('POST', '/api/register', {
    body: {
      username: aliceName,
      password: 'secret123',
      displayName: 'Алиса',
      email: `alice_${suffix}@example.com`,
      headset: 'pico4',
    },
  })
  check('регистрация Алисы', alice.status === 201 && !!alice.data.token, JSON.stringify(alice.data))
  check('Алиса: тег шлема в профиле', alice.data.user.headsetLabel === 'Pico 4')
  check('Алиса: почта только в своём профиле', alice.data.user.email === `alice_${suffix}@example.com`)

  const bob = await call('POST', '/api/register', {
    body: {
      username: bobName,
      password: 'secret123',
      displayName: 'Боб',
      email: `bob_${suffix}@example.com`,
      headset: 'quest3',
    },
  })
  check('регистрация Боба', bob.status === 201 && !!bob.data.token)

  const dup = await call('POST', '/api/register', {
    body: {
      username: aliceName,
      password: 'secret123',
      displayName: 'Алиса',
      email: `other_${suffix}@example.com`,
      headset: 'pico4',
    },
  })
  check('повторный логин занят (409)', dup.status === 409)

  const dupEmail = await call('POST', '/api/register', {
    body: {
      username: `extra_${suffix}`,
      password: 'secret123',
      displayName: 'Дубль',
      email: `alice_${suffix}@example.com`,
      headset: 'pico4',
    },
  })
  check('повторная почта занята (409)', dupEmail.status === 409)

  const badEmail = await call('POST', '/api/register', {
    body: {
      username: `nomail_${suffix}`,
      password: 'secret123',
      displayName: 'Нет почты',
      email: 'not-an-email',
      headset: 'pico4',
    },
  })
  check('кривая почта отклонена (400)', badEmail.status === 400)

  const badHeadset = await call('POST', '/api/register', {
    body: {
      username: `nohead_${suffix}`,
      password: 'secret123',
      displayName: 'Нет шлема',
      email: `nohead_${suffix}@example.com`,
      headset: 'gearvr',
    },
  })
  check('старая модель отклонена (400)', badHeadset.status === 400)

  const login = await call('POST', '/api/login', {
    body: { username: aliceName, password: 'secret123' },
  })
  check('вход по логину', login.status === 200 && !!login.data.token)

  const loginEmail = await call('POST', '/api/login', {
    body: { username: `alice_${suffix}@example.com`, password: 'secret123' },
  })
  check('вход по почте', loginEmail.status === 200 && !!loginEmail.data.token)

  const badLogin = await call('POST', '/api/login', {
    body: { username: aliceName, password: 'wrong' },
  })
  check('неверный пароль (401)', badLogin.status === 401)

  const aliceToken = alice.data.token
  const bobToken = bob.data.token
  const bobId = bob.data.user.id

  const me = await call('GET', '/api/me', { token: aliceToken })
  check('профиль', me.status === 200 && me.data.user.username === aliceName)

  const search = await call('GET', `/api/users/search?q=${bobName}`, { token: aliceToken })
  const foundBob = (search.data.users || []).find((u) => u.id === bobId)
  check(
    'поиск по юзернейму',
    search.status === 200 && !!foundBob,
    JSON.stringify(search.data),
  )
  check('шлем виден другим', !!foundBob && foundBob.headsetLabel === 'Quest 3')
  check('почта не видна другим', !!foundBob && foundBob.email === undefined)

  const request = await call('POST', '/api/friends/request', {
    token: aliceToken,
    body: { username: bobName },
  })
  check('заявка в друзья', request.status === 201 && request.data.status === 'pending', JSON.stringify(request.data))

  const bobFriends = await call('GET', '/api/friends', { token: bobToken })
  check(
    'Боб видит входящую заявку',
    bobFriends.status === 200 && bobFriends.data.incoming.some((u) => u.username === aliceName),
    JSON.stringify(bobFriends.data),
  )

  const accept = await call('POST', '/api/friends/answer', {
    token: bobToken,
    body: { userId: alice.data.user.id, accept: true },
  })
  check('заявка принята', accept.status === 200 && accept.data.status === 'accepted')

  const dm = await call('POST', '/api/chats/dm', {
    token: aliceToken,
    body: { username: bobName },
  })
  check('личный чат создан', dm.status === 200 && dm.data.chat.type === 'dm', JSON.stringify(dm.data))
  const dmId = dm.data.chat.id

  const dmAgain = await call('POST', '/api/chats/dm', {
    token: aliceToken,
    body: { username: bobName },
  })
  check('личный чат не дублируется', dmAgain.data.chat.id === dmId)

  const sent = await call('POST', `/api/chats/${dmId}/messages`, {
    token: aliceToken,
    body: { text: 'Привет с Pico!' },
  })
  check('текст отправлен', sent.status === 201 && sent.data.message.text === 'Привет с Pico!')

  const bobReads = await call('GET', `/api/chats/${dmId}/messages`, { token: bobToken })
  check(
    'Боб читает сообщение',
    bobReads.status === 200 && bobReads.data.messages.some((m) => m.text === 'Привет с Pico!'),
  )

  const stranger = await call('GET', `/api/chats/${dmId}/messages`, { token: 'deadbeef' })
  check('без токена не пускает (401)', stranger.status === 401)

  const room = await call('POST', '/api/chats/room', {
    token: bobToken,
    body: { title: 'Закрытая комната', description: 'Только по коду', isPublic: false },
  })
  check(
    'закрытая комната с кодом',
    room.status === 201 && !!room.data.chat.code && room.data.chat.isPublic === false,
    JSON.stringify(room.data),
  )
  check('описание комнаты сохранилось', room.data.chat.description === 'Только по коду')
  const roomId = room.data.chat.id
  const roomCode = room.data.chat.code

  const joinRoom = await call('POST', '/api/chats/join', {
    token: aliceToken,
    body: { code: roomCode },
  })
  check('вход по коду', joinRoom.status === 200 && joinRoom.data.chat.memberCount === 2, JSON.stringify(joinRoom.data))

  const voiceBytesRaw = Buffer.from('fake-audio-bytes-for-test')
  const voiceBytes = voiceBytesRaw.toString('base64')
  const voice = await call('POST', `/api/chats/${roomId}/messages`, {
    token: aliceToken,
    body: { audio: { data: voiceBytes, mime: 'audio/mp4', duration: 4 } },
  })
  check('голосовое отправлено', voice.status === 201 && !!voice.data.message.audioUrl, JSON.stringify(voice.data))

  const audioUrl = voice.data.message.audioUrl
  const audioResponse = await fetch(base + audioUrl)
  const audioSize = (await audioResponse.arrayBuffer()).byteLength
  check('голосовое скачивается', audioResponse.status === 200 && audioSize === voiceBytesRaw.length, `size=${audioSize}`)

  const publicRoom = await call('POST', '/api/chats/room', {
    token: bobToken,
    body: { title: 'Общая комната', isPublic: true },
  })
  check('публичная комната без кода', publicRoom.status === 201 && !publicRoom.data.chat.code)

  const rooms = await call('GET', '/api/rooms', { token: aliceToken })
  check(
    'публичная комната в списке',
    rooms.status === 200 && rooms.data.rooms.some((r) => r.title === 'Общая комната' && r.isMember === false),
    JSON.stringify(rooms.data),
  )

  const joinPublic = await call('POST', `/api/chats/${publicRoom.data.chat.id}/join`, {
    token: aliceToken,
  })
  check('вход в публичную комнату', joinPublic.status === 200 && joinPublic.data.chat.memberCount === 2)

  const chats = await call('GET', '/api/chats', { token: aliceToken })
  check(
    'чаты Алисы содержат личку и комнаты',
    chats.status === 200 && chats.data.chats.length >= 3,
    JSON.stringify(chats.data.chats.map((c) => c.type)),
  )

  const avatarBytes = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
    'base64',
  ).toString('base64')
  const avatar = await call('POST', '/api/me/avatar', {
    token: aliceToken,
    body: { data: avatarBytes, mime: 'image/png' },
  })
  check('аватар загружен', avatar.status === 200 && !!avatar.data.user.avatar, JSON.stringify(avatar.data))

  const avatarResponse = await fetch(base + avatar.data.user.avatar)
  check('аватар скачивается', avatarResponse.status === 200)

  const rename = await call('PATCH', '/api/me', { token: bobToken, body: { displayName: 'Бобоб' } })
  check('смена имени', rename.status === 200 && rename.data.user.displayName === 'Бобоб')

  const profile = await call('PATCH', '/api/me', {
    token: aliceToken,
    body: { bio: 'Люблю VR', accent: 'blue', headset: 'pico4ultra' },
  })
  check(
    'био и акцентный цвет',
    profile.status === 200 && profile.data.user.bio === 'Люблю VR' && profile.data.user.accent === '#42a5f5',
    JSON.stringify(profile.data),
  )
  check('смена шлема в профиле', profile.data.user.headsetLabel === 'Pico 4 Ultra')

  const banner = await call('POST', '/api/me/banner', {
    token: aliceToken,
    body: { data: avatarBytes, mime: 'image/png' },
  })
  check('баннер загружен', banner.status === 200 && !!banner.data.user.banner, JSON.stringify(banner.data))

  // Кик и автозакрытие комнаты владельцем.
  const kickRoom = await call('POST', '/api/chats/room', {
    token: bobToken,
    body: { title: 'На кик', isPublic: true },
  })
  const kickRoomId = kickRoom.data.chat.id
  await call('POST', `/api/chats/${kickRoomId}/join`, { token: aliceToken })
  const notOwnerKick = await call('DELETE', `/api/chats/${kickRoomId}/members/${bobId}`, {
    token: aliceToken,
  })
  check('не владелец не может кикать (403)', notOwnerKick.status === 403)
  const kick = await call('DELETE', `/api/chats/${kickRoomId}/members/${alice.data.user.id}`, {
    token: bobToken,
  })
  check('владелец кикает участника', kick.status === 200)
  const leave = await call('POST', `/api/chats/${kickRoomId}/leave`, { token: bobToken })
  check('выход владельца закрывает комнату', leave.status === 200 && leave.data.closed === true, JSON.stringify(leave.data))

  console.log(failures === 0 ? '\nВСЁ ОК' : `\nПРОВАЛОВ: ${failures}`)
  process.exit(failures === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('Тест упал:', e)
  process.exit(1)
})
