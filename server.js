'use strict'

/**
 * HeadsetConnector — сервер мессенджера для Pico и Meta Quest.
 *
 * Без внешних зависимостей: HTTP на встроенном модуле, база — встроенный SQLite
 * (node:sqlite). Запуск: node server.js
 *
 * Порты и данные:
 *   PORT        — порт HTTP (по умолчанию 8787)
 *   DATA_DIR    — папка для базы и медиа (по умолчанию ./data)
 *   IP / HOST   — адрес прослушивания (хостинги вроде alwaysdata задают сами)
 *
 * Настройки можно положить в файл .env рядом с server.js:
 *   ADMIN_PASSWORD=...
 *   PUBLIC_URL=https://имя.alwaysdata.net
 */

const http = require('node:http')
const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')

// Простой .env без зависимостей: KEY=VALUE, # — комментарий.
try {
  const envFile = path.join(__dirname, '.env')
  if (fs.existsSync(envFile)) {
    for (const line of fs.readFileSync(envFile, 'utf8').split(/\r?\n/)) {
      const trimmed = line.trim()
      if (!trimmed || trimmed.startsWith('#')) continue
      const eq = trimmed.indexOf('=')
      if (eq < 1) continue
      const key = trimmed.slice(0, eq).trim()
      let value = trimmed.slice(eq + 1).trim()
      if (
        (value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))
      ) {
        value = value.slice(1, -1)
      }
      if (process.env[key] === undefined) process.env[key] = value
    }
  }
} catch (_) {
  // файла нет или он недоступен — работаем на переменных окружения
}

const { DatabaseSync } = require('node:sqlite')

const PORT = Number(process.env.PORT || 8787)
const DATA_DIR = path.resolve(process.env.DATA_DIR || path.join(__dirname, 'data'))
const MEDIA_DIR = path.join(DATA_DIR, 'media')
const DB_PATH = path.join(DATA_DIR, 'headsetconnector.db')
const MAX_BODY = 6 * 1024 * 1024

fs.mkdirSync(MEDIA_DIR, { recursive: true })

const db = new DatabaseSync(DB_PATH)
db.exec(`
  PRAGMA journal_mode = WAL;
  CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    pass_hash TEXT NOT NULL,
    salt TEXT NOT NULL,
    avatar TEXT,
    email TEXT,
    headset TEXT,
    bio TEXT,
    accent TEXT,
    banner TEXT,
    badge TEXT,
    banned_until INTEGER,
    created_at INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS tokens (
    token TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    created_at INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS friends (
    a TEXT NOT NULL,
    b TEXT NOT NULL,
    status TEXT NOT NULL,
    requested_by TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (a, b)
  );
  CREATE TABLE IF NOT EXISTS chats (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    title TEXT,
    description TEXT,
    is_public INTEGER NOT NULL DEFAULT 0,
    code TEXT,
    owner_id TEXT,
    created_at INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS chat_members (
    chat_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    PRIMARY KEY (chat_id, user_id)
  );
  CREATE TABLE IF NOT EXISTS messages (
    id TEXT PRIMARY KEY,
    chat_id TEXT NOT NULL,
    sender_id TEXT NOT NULL,
    text TEXT,
    audio_url TEXT,
    audio_duration INTEGER,
    created_at INTEGER NOT NULL
  );
  CREATE INDEX IF NOT EXISTS idx_messages_chat ON messages(chat_id, created_at);
  CREATE INDEX IF NOT EXISTS idx_members_user ON chat_members(user_id);
  CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
  );
`)

// Миграции для баз, созданных до появления блокировок, почты и тега шлема.
const userColumns = db.prepare('PRAGMA table_info(users)').all()
const hasColumn = (name) => userColumns.some((column) => column.name === name)
if (!hasColumn('banned')) {
  db.exec('ALTER TABLE users ADD COLUMN banned INTEGER NOT NULL DEFAULT 0')
}
if (!hasColumn('email')) {
  db.exec('ALTER TABLE users ADD COLUMN email TEXT')
}
if (!hasColumn('headset')) {
  db.exec('ALTER TABLE users ADD COLUMN headset TEXT')
}
if (!hasColumn('banned_until')) {
  db.exec('ALTER TABLE users ADD COLUMN banned_until INTEGER')
  // Переносим старые вечные баны в новую схему.
  db.exec('UPDATE users SET banned_until = 0 WHERE banned = 1')
}
if (!hasColumn('bio')) {
  db.exec('ALTER TABLE users ADD COLUMN bio TEXT')
}
if (!hasColumn('accent')) {
  db.exec('ALTER TABLE users ADD COLUMN accent TEXT')
}
if (!hasColumn('banner')) {
  db.exec('ALTER TABLE users ADD COLUMN banner TEXT')
}
if (!hasColumn('badge')) {
  db.exec('ALTER TABLE users ADD COLUMN badge TEXT')
}

// Почта уникальна без учёта регистра. NULL разрешён — старые аккаунты.
db.exec(
  'CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users(lower(email)) WHERE email IS NOT NULL',
)

// Описание комнаты — миграция для старых баз.
const chatColumns = db.prepare('PRAGMA table_info(chats)').all()
if (!chatColumns.some((column) => column.name === 'description')) {
  db.exec('ALTER TABLE chats ADD COLUMN description TEXT')
}

const now = () => Date.now()
const uid = () => crypto.randomUUID()
const token = () => crypto.randomBytes(32).toString('hex')

// ------------------------------------------------------- админ-панель

const ADMIN_ENV = process.env.ADMIN_PASSWORD || ''
let adminHash = ''
const adminSessions = new Map()
const ADMIN_TTL = 12 * 60 * 60 * 1000

function makeHash(password, salt = crypto.randomBytes(16).toString('hex')) {
  return `${salt}:${crypto.scryptSync(password, salt, 32).toString('hex')}`
}

function checkAdminPassword(password) {
  if (!adminHash) return false
  const [salt, hash] = adminHash.split(':')
  const actual = crypto.scryptSync(String(password), salt, 32)
  const expected = Buffer.from(hash, 'hex')
  return actual.length === expected.length && crypto.timingSafeEqual(actual, expected)
}

function initAdmin() {
  if (ADMIN_ENV) {
    adminHash = makeHash(ADMIN_ENV)
    db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('admin_hash', ?)").run(adminHash)
    console.log('  админ-пароль: из ADMIN_PASSWORD')
    return
  }
  const saved = db.prepare("SELECT value FROM settings WHERE key = 'admin_hash'").get()
  if (saved) {
    adminHash = saved.value
    return
  }
  const generated = crypto.randomBytes(9).toString('base64url')
  adminHash = makeHash(generated)
  db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('admin_hash', ?)").run(adminHash)
  console.log('')
  console.log('  Пароль для панели /admin: ' + generated)
  console.log('  Сохранён в базе. Изменить: ADMIN_PASSWORD=новый node server.js')
  console.log('')
}

function adminAuthorized(req) {
  const value = String(req.headers['x-admin-token'] || '')
  const expires = adminSessions.get(value)
  if (!expires) return false
  if (expires < Date.now()) {
    adminSessions.delete(value)
    return false
  }
  return true
}


// ------------------------------------------------------------------ acc.

function hashPassword(password, salt) {
  return crypto.scryptSync(password, salt, 64).toString('hex')
}

function createUser(username, password, displayName, email, headset) {
  const salt = crypto.randomBytes(16).toString('hex')
  const id = uid()
  db.prepare(
    `INSERT INTO users (id, username, display_name, pass_hash, salt, avatar, email, headset, created_at)
     VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?)`,
  ).run(
    id,
    username,
    displayName,
    hashPassword(password, salt),
    salt,
    email,
    headset,
    now(),
  )
  return getUser(id)
}

function getUser(id) {
  return db.prepare('SELECT * FROM users WHERE id = ?').get(id) || null
}

function getUserByName(username) {
  return db.prepare('SELECT * FROM users WHERE lower(username) = lower(?)').get(username) || null
}

function getUserByEmail(email) {
  return db.prepare('SELECT * FROM users WHERE lower(email) = lower(?)').get(email) || null
}

function publicUser(user) {
  if (!user) return null
  return {
    id: user.id,
    username: user.username,
    displayName: user.display_name,
    avatar: user.avatar,
    headset: user.headset || null,
    headsetLabel: headsetLabel(user.headset),
    bio: user.bio || null,
    accent: user.accent || null,
    banner: user.banner || null,
    badge: user.badge || null,
  }
}

// Свой профиль: добавляет почту, которую другим не показываем.
function privateUser(user) {
  return { ...publicUser(user), email: user.email || null }
}

// Бан по расписанию: banned_until = 0 значит «навсегда», иначе — момент разбана.
function banActive(user) {
  return user.banned_until != null && (user.banned_until === 0 || user.banned_until > Date.now())
}

function clearExpiredBan(user) {
  if (user.banned_until != null && user.banned_until !== 0 && user.banned_until <= Date.now()) {
    db.prepare('UPDATE users SET banned_until = NULL WHERE id = ?').run(user.id)
    user.banned_until = null
  }
}

function issueToken(userId) {
  const value = token()
  db.prepare('INSERT INTO tokens (token, user_id, created_at) VALUES (?, ?, ?)')
    .run(value, userId, now())
  return value
}

function userByToken(value) {
  if (!value) return null
  const user = db.prepare(
    `SELECT u.* FROM users u JOIN tokens t ON t.user_id = u.id WHERE t.token = ?`,
  ).get(value)
  if (!user) return null
  clearExpiredBan(user)
  if (banActive(user)) return null
  return user
}

// ------------------------------------------------------------------ chats

function normalizePair(a, b) {
  return a < b ? [a, b] : [b, a]
}

function areFriends(a, b) {
  const [x, y] = normalizePair(a, b)
  const row = db.prepare('SELECT status FROM friends WHERE a = ? AND b = ?').get(x, y)
  return row && row.status === 'accepted'
}

function friendState(a, b) {
  const [x, y] = normalizePair(a, b)
  return db.prepare('SELECT * FROM friends WHERE a = ? AND b = ?').get(x, y) || null
}

function chatMembers(chatId) {
  return db.prepare(
    `SELECT u.* FROM chat_members m JOIN users u ON u.id = m.user_id
     WHERE m.chat_id = ? ORDER BY u.created_at`,
  ).all(chatId)
}

function isMember(chatId, userId) {
  return !!db.prepare('SELECT 1 FROM chat_members WHERE chat_id = ? AND user_id = ?')
    .get(chatId, userId)
}

function lastMessage(chatId) {
  const row = db.prepare(
    'SELECT * FROM messages WHERE chat_id = ? ORDER BY created_at DESC LIMIT 1',
  ).get(chatId)
  return row ? messageView(row) : null
}

function messageView(row) {
  const sender = getUser(row.sender_id)
  return {
    id: row.id,
    chatId: row.chat_id,
    senderId: row.sender_id,
    senderName: sender ? sender.display_name : '—',
    senderAvatar: sender ? sender.avatar : null,
    text: row.text,
    audioUrl: row.audio_url,
    audioDuration: row.audio_duration,
    createdAt: row.created_at,
  }
}

function chatView(chat, me) {
  const members = chatMembers(chat.id)
  const others = members.filter((m) => m.id !== me)
  const dm = chat.type === 'dm'
  return {
    id: chat.id,
    type: chat.type,
    title: dm ? (others[0] ? others[0].display_name : 'Диалог') : chat.title,
    description: chat.description || null,
    avatar: dm ? (others[0] ? others[0].avatar : null) : null,
    isPublic: !!chat.is_public,
    code: chat.owner_id === me ? chat.code : null,
    ownerId: chat.owner_id,
    ownerName: chat.owner_id ? (getUser(chat.owner_id) || {}).display_name || null : null,
    members: members.map(publicUser),
    memberCount: members.length,
    lastMessage: lastMessage(chat.id),
    createdAt: chat.created_at,
  }
}

function findDirectChat(a, b) {
  const rows = db.prepare(
    `SELECT c.* FROM chats c
     JOIN chat_members m1 ON m1.chat_id = c.id AND m1.user_id = ?
     JOIN chat_members m2 ON m2.chat_id = c.id AND m2.user_id = ?
     WHERE c.type = 'dm'`,
  ).all(a, b)
  return rows[0] || null
}

// ------------------------------------------------------------------ media

function saveMedia(base64, extension) {
  const clean = String(base64 || '').replace(/^data:[^,]+,/, '')
  const bytes = Buffer.from(clean, 'base64')
  if (!bytes.length) return null
  const name = `${crypto.randomBytes(16).toString('hex')}.${extension}`
  fs.writeFileSync(path.join(MEDIA_DIR, name), bytes)
  return `/media/${name}`
}

function extensionFor(mime) {
  const value = String(mime || '').toLowerCase()
  if (value.includes('png')) return 'png'
  if (value.includes('webp')) return 'webp'
  if (value.includes('jpeg') || value.includes('jpg')) return 'jpg'
  if (value.includes('mp4') || value.includes('m4a') || value.includes('aac')) return 'm4a'
  if (value.includes('ogg') || value.includes('opus')) return 'ogg'
  if (value.includes('mpeg') || value.includes('mp3')) return 'mp3'
  return 'bin'
}

const MIME = {
  png: 'image/png',
  jpg: 'image/jpeg',
  webp: 'image/webp',
  m4a: 'audio/mp4',
  ogg: 'audio/ogg',
  mp3: 'audio/mpeg',
  bin: 'application/octet-stream',
}

// ------------------------------------------------------------------- диск

function dirSize() {
  let bytes = 0
  for (const file of fs.readdirSync(MEDIA_DIR)) {
    try {
      bytes += fs.statSync(path.join(MEDIA_DIR, file)).size
    } catch (_) {
      // файл мог исчезнуть
    }
  }
  return bytes
}

// Когда диск почти полон — удаляем самое старое. Трогаем только вложения
// (голосовые и прочие файлы); аватарки и баннеры не удаляются никогда.
function cleanupDisk(force = false) {
  const limit = Number(process.env.DISK_LIMIT_MB || 900) * 1_048_576
  if (!force && dirSize() < limit) return 0
  const target = Math.floor(limit * 0.75)

  const referenced = new Set()
  for (const user of db.prepare('SELECT avatar, banner FROM users').all()) {
    if (user.avatar) referenced.add(path.basename(user.avatar))
    if (user.banner) referenced.add(path.basename(user.banner))
  }

  const expendable = fs.readdirSync(MEDIA_DIR)
    .map((name) => {
      try {
        return { name, mtime: fs.statSync(path.join(MEDIA_DIR, name)).mtimeMs }
      } catch (_) {
        return { name, mtime: 0 }
      }
    })
    .filter((entry) => !referenced.has(entry.name))
    .sort((a, b) => a.mtime - b.mtime)

  let freed = 0
  for (const entry of expendable) {
    if (dirSize() <= target) break
    const full = path.join(MEDIA_DIR, entry.name)
    try {
      freed += fs.statSync(full).size
      fs.unlinkSync(full)
      console.log(`автоочистка: удалён ${entry.name}`)
    } catch (_) {
      // файл уже удалён
    }
  }
  return freed
}

// ----------------------------------------------------------- обновление APK

function appVersion() {
  try {
    const file = path.join(__dirname, 'version.json')
    if (!fs.existsSync(file)) return null
    const data = JSON.parse(fs.readFileSync(file, 'utf8'))
    return {
      versionCode: Number(data.versionCode || 0),
      versionName: String(data.versionName || ''),
      apkUrl: (process.env.PUBLIC_URL || '').trimEnd('/') + '/app/latest.apk',
    }
  } catch (_) {
    return null
  }
}

// ------------------------------------------------------------------ http

function json(res, status, payload) {
  const body = JSON.stringify(payload)
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Authorization, Content-Type',
    'Access-Control-Allow-Methods': 'GET, POST, PATCH, DELETE, OPTIONS',
    'Cache-Control': 'no-store',
  })
  res.end(body)
}

function fail(res, status, message) {
  json(res, status, { error: message })
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0
    const chunks = []
    req.on('data', (chunk) => {
      size += chunk.length
      if (size > MAX_BODY) {
        reject(new Error('Слишком большой запрос'))
        req.destroy()
        return
      }
      chunks.push(chunk)
    })
    req.on('end', () => {
      if (!chunks.length) return resolve({})
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')))
      } catch (_) {
        reject(new Error('Некорректный JSON'))
      }
    })
    req.on('error', reject)
  })
}

function bearer(req) {
  const header = String(req.headers.authorization || '')
  return header.startsWith('Bearer ') ? header.slice(7).trim() : ''
}

function validUsername(value) {
  return /^[a-zA-Z0-9_]{3,20}$/.test(String(value || ''))
}

// Список шлемов для тега в профиле. Ключ — то, что лежит в базе,
// label — то, что видит человек. Старые модели не добавляем.
const HEADSETS = [
  { key: 'quest1', label: 'Quest 1' },
  { key: 'quest2', label: 'Quest 2' },
  { key: 'quest3', label: 'Quest 3' },
  { key: 'questpro', label: 'Quest Pro' },
  { key: 'piconeo3', label: 'Pico Neo 3' },
  { key: 'pico4', label: 'Pico 4' },
  { key: 'pico4ultra', label: 'Pico 4 Ultra' },
  { key: 'pico4pro', label: 'Pico 4 Pro' },
]
const HEADSET_KEYS = new Set(HEADSETS.map((item) => item.key))

function headsetLabel(key) {
  const found = HEADSETS.find((item) => item.key === key)
  return found ? found.label : null
}

function validEmail(value) {
  const email = String(value || '').trim()
  if (email.length < 6 || email.length > 120) return false
  return /^[^\s@]+@[^\s@]+\.[a-zA-Z]{2,}$/.test(email)
}

// Акцентный цвет профиля: #rrggbb / #rrggbbaa или готовое имя. Пусто = сброс.
const ACCENT_NAMES = {
  red: '#ef5350',
  orange: '#fb8c00',
  amber: '#fdd835',
  green: '#66bb6a',
  teal: '#26a69a',
  blue: '#42a5f5',
  indigo: '#5c6bc0',
  purple: '#ab47bc',
  pink: '#ec407a',
}

function normalizeAccent(value) {
  const v = String(value || '').trim()
  if (!v) return ''
  if (/^#[0-9a-fA-F]{6}$/.test(v)) return v.toLowerCase()
  if (/^#[0-9a-fA-F]{8}$/.test(v)) return v.slice(0, 7).toLowerCase()
  if (ACCENT_NAMES[v]) return ACCENT_NAMES[v]
  return false
}

// ------------------------------------------------------------ защита от спама

const TRUST_PROXY = process.env.TRUST_PROXY === '1'
const rateState = new Map()

function clientIp(req) {
  if (TRUST_PROXY) {
    const forwarded = String(req.headers['x-forwarded-for'] || '').split(',')[0].trim()
    if (forwarded) return forwarded
  }
  return req.socket.remoteAddress || 'unknown'
}

function tooManyAttempts(req, bucket, limit = 15, windowMs = 60_000) {
  const key = `${bucket}:${clientIp(req)}`
  const now = Date.now()
  const entry = rateState.get(key)
  if (!entry || entry.reset < now) {
    rateState.set(key, { count: 1, reset: now + windowMs })
    return false
  }
  entry.count += 1
  return entry.count > limit
}

setInterval(() => {
  const now = Date.now()
  for (const [key, entry] of rateState) {
    if (entry.reset < now) rateState.delete(key)
  }
}, 60_000).unref()

const server = http.createServer(async (req, res) => {
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'Authorization, Content-Type',
      'Access-Control-Allow-Methods': 'GET, POST, PATCH, DELETE, OPTIONS',
    })
    return res.end()
  }

  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`)
  const pathname = decodeURIComponent(url.pathname)

  // ---------------------------------------------------------- медиа и главная
  if (pathname.startsWith('/media/')) {
    const name = path.basename(pathname)
    const file = path.join(MEDIA_DIR, name)
    if (!name || !fs.existsSync(file)) return fail(res, 404, 'Файл не найден')
    const ext = path.extname(name).slice(1).toLowerCase()
    const stats = fs.statSync(file)
    res.writeHead(200, {
      'Content-Type': MIME[ext] || MIME.bin,
      'Content-Length': stats.size,
      'Cache-Control': 'public, max-age=31536000, immutable',
    })
    return fs.createReadStream(file).pipe(res)
  }

  if (pathname === '/' || pathname === '/index.html') {
    const html = `<!doctype html><meta charset="utf-8"><title>HeadsetConnector</title>
      <body style="background:#0B1020;color:#EAEEF9;font-family:system-ui;padding:40px">
      <h1>HeadsetConnector</h1><p>Сервер мессенджера работает.</p>
      <p>Пользователей: ${db.prepare('SELECT count(*) AS n FROM users').get().n},
         сообщений: ${db.prepare('SELECT count(*) AS n FROM messages').get().n}</p>
      </body>`
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
    return res.end(html)
  }

  if (pathname === '/admin' || pathname === '/admin/' || pathname === '/admin.html') {
    const file = path.join(__dirname, 'admin.html')
    if (!fs.existsSync(file)) return fail(res, 500, 'admin.html не найден рядом с server.js')
    res.writeHead(200, {
      'Content-Type': 'text/html; charset=utf-8',
      'Cache-Control': 'no-store',
    })
    return fs.createReadStream(file).pipe(res)
  }

  // APK для автообновления приложения.
  if (pathname === '/app/latest.apk') {
    const file = path.join(__dirname, 'app.apk')
    if (!fs.existsSync(file)) return fail(res, 404, 'Файл обновления не загружен')
    res.writeHead(200, {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Length': fs.statSync(file).size,
      'Cache-Control': 'no-store',
    })
    return fs.createReadStream(file).pipe(res)
  }

  if (!pathname.startsWith('/api/')) return fail(res, 404, 'Не найдено')

  let body = {}
  if (req.method === 'POST' || req.method === 'PATCH') {
    try {
      body = await readBody(req)
    } catch (e) {
      return fail(res, 400, e.message)
    }
  }

  const me = userByToken(bearer(req))

  try {
    // -------------------------------------------------------------- сервис
    if (pathname === '/api/ping') {
      return json(res, 200, { ok: true, name: 'HeadsetConnector', version: 1 })
    }

    // Последняя доступная версия приложения (для автообновления).
    if (pathname === '/api/app/latest') {
      const latest = appVersion()
      if (!latest) return json(res, 200, { versionCode: 0, versionName: '', apkUrl: '' })
      return json(res, 200, latest)
    }

    // --------------------------------------------------------------- панель
    if (pathname === '/api/admin/login' && req.method === 'POST') {
      if (tooManyAttempts(req, 'admin_login', 8, 300_000)) {
        return fail(res, 429, 'Слишком много попыток. Подождите 5 минут.')
      }
      if (!checkAdminPassword(body.password)) {
        console.log(`Панель: неверный пароль с ${clientIp(req)}`)
        return fail(res, 401, 'Неверный пароль')
      }
      const value = crypto.randomBytes(24).toString('hex')
      adminSessions.set(value, Date.now() + ADMIN_TTL)
      console.log(`Панель: вход с ${clientIp(req)}`)
      return json(res, 200, { token: value, ttl: ADMIN_TTL })
    }

    if (pathname.startsWith('/api/admin/')) {
      if (!adminAuthorized(req)) return fail(res, 401, 'Нужен вход в панель')

      if (pathname === '/api/admin/logout' && req.method === 'POST') {
        adminSessions.delete(String(req.headers['x-admin-token'] || ''))
        return json(res, 200, { ok: true })
      }

      if (pathname === '/api/admin/overview' && req.method === 'GET') {
        const count = (sql) => db.prepare(sql).get().n
        let mediaBytes = 0
        for (const file of fs.readdirSync(MEDIA_DIR)) {
          try {
            mediaBytes += fs.statSync(path.join(MEDIA_DIR, file)).size
          } catch (_) {
            // файл мог исчезнуть
          }
        }
        const recent = db.prepare('SELECT * FROM users ORDER BY created_at DESC LIMIT 8').all()
        const recentMessages = db.prepare(
          `SELECT m.*, u.username AS username, u.display_name AS name
           FROM messages m JOIN users u ON u.id = m.sender_id
           ORDER BY m.created_at DESC LIMIT 10`,
        ).all()
        return json(res, 200, {
          stats: {
            users: count('SELECT count(*) AS n FROM users'),
            banned: count(
              `SELECT count(*) AS n FROM users
               WHERE banned_until IS NOT NULL AND (banned_until = 0 OR banned_until > ${Date.now()})`,
            ),
            messages: count('SELECT count(*) AS n FROM messages'),
            messages24h: count(
              `SELECT count(*) AS n FROM messages WHERE created_at > ${Date.now() - 86_400_000}`,
            ),
            rooms: count("SELECT count(*) AS n FROM chats WHERE type = 'room'"),
            new7d: count(
              `SELECT count(*) AS n FROM users WHERE created_at > ${Date.now() - 7 * 86_400_000}`,
            ),
            onlineTokens: count('SELECT count(*) AS n FROM tokens'),
            mediaMb: (mediaBytes / 1_048_576).toFixed(1),
            diskLimitMb: Number(process.env.DISK_LIMIT_MB || 900),
          },
          recent: recent.map((user) => ({
            ...publicUser(user),
            createdAt: user.created_at,
            banned: banActive(user),
          })),
          recentMessages: recentMessages.map((row) => ({
            sender: row.name,
            username: row.username,
            text: (row.text || '').slice(0, 120),
            audio: !!row.audio_url,
            createdAt: row.created_at,
          })),
          server: `${require('node:os').hostname()} · Node ${process.version} · порт ${PORT}`,
        })
      }

      if (pathname === '/api/admin/users' && req.method === 'GET') {
        const query = String(url.searchParams.get('q') || '').trim().toLowerCase()
        const base = `SELECT u.*, (SELECT count(*) FROM messages m WHERE m.sender_id = u.id) AS messages
                      FROM users u`
        const rows = query
          ? db.prepare(
            `${base} WHERE lower(u.username) LIKE ? OR lower(u.display_name) LIKE ?
               OR lower(COALESCE(u.email, '')) LIKE ?
             ORDER BY u.created_at DESC LIMIT 200`,
          ).all(`%${query}%`, `%${query}%`, `%${query}%`)
          : db.prepare(`${base} ORDER BY u.created_at DESC LIMIT 200`).all()
        return json(res, 200, {
          users: rows.map((row) => ({
            ...publicUser(row),
            email: row.email || null,
            banned: banActive(row),
            bannedUntil: row.banned_until || null,
            messages: row.messages,
            createdAt: row.created_at,
          })),
        })
      }

      const userAction = pathname.match(/^\/api\/admin\/users\/([^/]+)\/(ban|unban|reset|badge)$/)
      if (userAction && req.method === 'POST') {
        const user = getUser(userAction[1])
        if (!user) return fail(res, 404, 'Пользователь не найден')
        const id = user.id

        if (userAction[2] === 'ban') {
          const duration = Math.max(0, Number(body.duration || 0) || 0)
          const until = duration > 0 ? Date.now() + duration * 60_000 : 0
          db.prepare('UPDATE users SET banned_until = ? WHERE id = ?').run(until, id)
          db.prepare('DELETE FROM tokens WHERE user_id = ?').run(id)
          console.log(
            `Панель: забанен @${user.username}` + (duration > 0 ? ` на ${duration} мин` : ' навсегда'),
          )
          return json(res, 200, { ok: true, banned: true, bannedUntil: until || null })
        }

        if (userAction[2] === 'unban') {
          db.prepare('UPDATE users SET banned_until = NULL WHERE id = ?').run(id)
          console.log(`Панель: разбанен @${user.username}`)
          return json(res, 200, { ok: true, banned: false })
        }

        if (userAction[2] === 'badge') {
          const badge = String(body.badge || '').trim().slice(0, 24) || null
          db.prepare('UPDATE users SET badge = ? WHERE id = ?').run(badge, id)
          console.log(`Панель: значок @${user.username} -> ${badge || 'снят'}`)
          return json(res, 200, { ok: true, badge })
        }

        const password = crypto.randomBytes(5).toString('base64url')
        const salt = crypto.randomBytes(16).toString('hex')
        const hash = crypto.scryptSync(password, salt, 64).toString('hex')
        db.prepare('UPDATE users SET pass_hash = ?, salt = ? WHERE id = ?').run(hash, salt, id)
        db.prepare('DELETE FROM tokens WHERE user_id = ?').run(id)
        console.log(`Панель: сброшен пароль @${user.username}`)
        return json(res, 200, { password })
      }

      const userDelete = pathname.match(/^\/api\/admin\/users\/([^/]+)$/)
      if (userDelete && req.method === 'DELETE') {
        const user = getUser(userDelete[1])
        if (!user) return fail(res, 404, 'Пользователь не найден')

        db.prepare('DELETE FROM messages WHERE sender_id = ?').run(user.id)
        db.prepare('DELETE FROM chat_members WHERE user_id = ?').run(user.id)
        db.prepare('DELETE FROM friends WHERE a = ? OR b = ?').run(user.id, user.id)
        db.prepare('DELETE FROM tokens WHERE user_id = ?').run(user.id)
        db.prepare('DELETE FROM users WHERE id = ?').run(user.id)
        db.prepare('DELETE FROM chats WHERE id NOT IN (SELECT chat_id FROM chat_members)').run()

        if (user.avatar) {
          try {
            fs.unlinkSync(path.join(MEDIA_DIR, path.basename(user.avatar)))
          } catch (_) {
            // файла уже нет
          }
        }

        console.log(`Панель: удалён @${user.username}`)
        return json(res, 200, { ok: true })
      }

      if (pathname === '/api/admin/rooms' && req.method === 'GET') {
        const rows = db.prepare(
          `SELECT c.*, u.username AS owner,
             (SELECT count(*) FROM chat_members m WHERE m.chat_id = c.id) AS members,
             (SELECT count(*) FROM messages m WHERE m.chat_id = c.id) AS messages
           FROM chats c LEFT JOIN users u ON u.id = c.owner_id
           WHERE c.type = 'room' ORDER BY c.created_at DESC LIMIT 200`,
        ).all()
        return json(res, 200, {
          rooms: rows.map((row) => ({
            id: row.id,
            title: row.title,
            description: row.description || null,
            isPublic: !!row.is_public,
            owner: row.owner,
            members: row.members,
            messages: row.messages,
            createdAt: row.created_at,
          })),
        })
      }

      const roomDelete = pathname.match(/^\/api\/admin\/rooms\/([^/]+)$/)
      if (roomDelete && req.method === 'DELETE') {
        const chat = db.prepare('SELECT * FROM chats WHERE id = ?').get(roomDelete[1])
        if (!chat) return fail(res, 404, 'Комната не найдена')
        db.prepare('DELETE FROM messages WHERE chat_id = ?').run(chat.id)
        db.prepare('DELETE FROM chat_members WHERE chat_id = ?').run(chat.id)
        db.prepare('DELETE FROM chats WHERE id = ?').run(chat.id)
        console.log(`Панель: удалена комната «${chat.title}»`)
        return json(res, 200, { ok: true })
      }

      // Список медиафайлов с размерами и типом (для ручной чистки).
      if (pathname === '/api/admin/media' && req.method === 'GET') {
        const referenced = new Set()
        for (const user of db.prepare('SELECT avatar, banner FROM users').all()) {
          if (user.avatar) referenced.add(path.basename(user.avatar))
          if (user.banner) referenced.add(path.basename(user.banner))
        }
        const files = fs.readdirSync(MEDIA_DIR)
          .map((name) => {
            const full = path.join(MEDIA_DIR, name)
            try {
              const stat = fs.statSync(full)
              return {
                name,
                size: stat.size,
                age: Date.now() - stat.mtimeMs,
                avatar: referenced.has(name),
              }
            } catch (_) {
              return null
            }
          })
          .filter(Boolean)
          .sort((a, b) => a.age - b.age)
        const total = files.reduce((sum, file) => sum + file.size, 0)
        return json(res, 200, { files, total, count: files.length })
      }

      const mediaDelete = pathname.match(/^\/api\/admin\/media\/([^/]+)$/)
      if (mediaDelete && req.method === 'DELETE') {
        const name = path.basename(mediaDelete[1])
        if (name !== mediaDelete[1]) return fail(res, 400, 'Недопустимое имя')
        try {
          fs.unlinkSync(path.join(MEDIA_DIR, name))
        } catch (_) {
          return fail(res, 404, 'Файл не найден')
        }
        console.log(`Панель: удалён файл ${name}`)
        return json(res, 200, { ok: true })
      }

      // Принудительная очистка диска (то же делает автоочистка по расписанию).
      if (pathname === '/api/admin/cleanup' && req.method === 'POST') {
        const freed = cleanupDisk(true)
        return json(res, 200, { freed })
      }

      return fail(res, 404, 'Неизвестный запрос панели')
    }

    // ------------------------------------------------------------- аккаунт
    if (pathname === '/api/register' && req.method === 'POST') {
      if (tooManyAttempts(req, 'register', 8, 600_000)) {
        return fail(res, 429, 'Слишком много попыток. Попробуйте позже.')
      }
      const username = String(body.username || '').trim()
      const password = String(body.password || '')
      const email = String(body.email || '').trim().toLowerCase()
      const headset = String(body.headset || '').trim().toLowerCase()

      if (!validUsername(username)) {
        return fail(res, 400, 'Логин: 3–20 символов, латиница, цифры и _')
      }
      if (password.length < 6) return fail(res, 400, 'Пароль минимум 6 символов')
      if (!validEmail(email)) return fail(res, 400, 'Укажите настоящую почту')
      if (!HEADSET_KEYS.has(headset)) return fail(res, 400, 'Выберите модель шлема')
      if (getUserByName(username)) return fail(res, 409, 'Такой логин уже занят')
      if (getUserByEmail(email)) return fail(res, 409, 'Эта почта уже используется')

      const displayName = String(body.displayName || username).trim().slice(0, 32) || username
      const user = createUser(
        username,
        password,
        displayName.toLowerCase() === username.toLowerCase() ? username : displayName,
        email,
        headset,
      )
      return json(res, 201, { token: issueToken(user.id), user: privateUser(user) })
    }

    if (pathname === '/api/login' && req.method === 'POST') {
      if (tooManyAttempts(req, 'login', 20, 60_000)) {
        return fail(res, 429, 'Слишком много попыток входа. Подождите минуту.')
      }
      const login = String(body.username || body.login || '').trim()
      const password = String(body.password || '')
      const user = getUserByName(login) || (validEmail(login) ? getUserByEmail(login) : null)
      if (!user) return fail(res, 401, 'Неверный логин или пароль')
      clearExpiredBan(user)
      if (banActive(user)) return fail(res, 403, 'Аккаунт заблокирован')
      const expected = Buffer.from(user.pass_hash, 'hex')
      const actual = Buffer.from(hashPassword(password, user.salt), 'hex')
      if (expected.length !== actual.length || !crypto.timingSafeEqual(expected, actual)) {
        return fail(res, 401, 'Неверный логин или пароль')
      }
      return json(res, 200, { token: issueToken(user.id), user: privateUser(user) })
    }

    if (!me) return fail(res, 401, 'Нужен вход')

    if (pathname === '/api/me' && req.method === 'GET') {
      return json(res, 200, { user: privateUser(me) })
    }

    if (pathname === '/api/me' && req.method === 'PATCH') {
      const displayName = body.displayName === undefined ? null : String(body.displayName || '').trim().slice(0, 32)
      if (displayName !== null && !displayName) return fail(res, 400, 'Имя не может быть пустым')
      const headset = body.headset === undefined ? null : String(body.headset || '').trim().toLowerCase()
      if (headset !== null && !HEADSET_KEYS.has(headset)) {
        return fail(res, 400, 'Неизвестная модель шлема')
      }
      const bio = body.bio === undefined ? null : String(body.bio || '').trim().slice(0, 200)
      const accent = body.accent === undefined ? null : normalizeAccent(body.accent)
      if (accent !== null && accent === false) {
        return fail(res, 400, 'Неверный цвет акцента')
      }
      db.prepare(
        `UPDATE users SET display_name = COALESCE(?, display_name),
           headset = COALESCE(?, headset),
           bio = COALESCE(?, bio),
           accent = COALESCE(?, accent)
         WHERE id = ?`,
      ).run(displayName, headset, bio, accent, me.id)
      return json(res, 200, { user: privateUser(getUser(me.id)) })
    }

    if (pathname === '/api/me/avatar' && req.method === 'POST') {
      const url = saveMedia(body.data, extensionFor(body.mime))
      if (!url) return fail(res, 400, 'Пустая картинка')
      db.prepare('UPDATE users SET avatar = ? WHERE id = ?').run(url, me.id)
      return json(res, 200, { user: privateUser(getUser(me.id)) })
    }

    if (pathname === '/api/me/banner' && req.method === 'POST') {
      const url = saveMedia(body.data, extensionFor(body.mime))
      if (!url) return fail(res, 400, 'Пустая картинка')
      db.prepare('UPDATE users SET banner = ? WHERE id = ?').run(url, me.id)
      return json(res, 200, { user: privateUser(getUser(me.id)) })
    }

    // --------------------------------------------------------------- люди
    if (pathname === '/api/users/search' && req.method === 'GET') {
      const query = String(url.searchParams.get('q') || '').trim().toLowerCase()
      if (query.length < 2) return json(res, 200, { users: [] })
      const rows = db.prepare(
        `SELECT * FROM users
         WHERE id != ? AND (lower(username) LIKE ? OR lower(display_name) LIKE ?)
         ORDER BY lower(username) LIMIT 20`,
      ).all(me.id, `%${query}%`, `%${query}%`)
      return json(res, 200, {
        users: rows.map((row) => {
          const state = friendState(me.id, row.id)
          let relation = 'none'
          if (state) {
            if (state.status === 'accepted') relation = 'friends'
            else relation = state.requested_by === me.id ? 'outgoing' : 'incoming'
          }
          return { ...publicUser(row), relation }
        }),
      })
    }

    // ------------------------------------------------------------- друзья
    if (pathname === '/api/friends' && req.method === 'GET') {
      const rows = db.prepare(
        'SELECT * FROM friends WHERE (a = ? OR b = ?) ORDER BY created_at DESC',
      ).all(me.id, me.id)
      const friends = []
      const incoming = []
      const outgoing = []
      for (const row of rows) {
        const otherId = row.a === me.id ? row.b : row.a
        const other = getUser(otherId)
        if (!other) continue
        const item = { ...publicUser(other), since: row.created_at }
        if (row.status === 'accepted') friends.push(item)
        else if (row.requested_by === me.id) outgoing.push(item)
        else incoming.push(item)
      }
      return json(res, 200, { friends, incoming, outgoing })
    }

    if (pathname === '/api/friends/request' && req.method === 'POST') {
      const other = getUserByName(String(body.username || '').trim())
      if (!other) return fail(res, 404, 'Пользователь не найден')
      if (other.id === me.id) return fail(res, 400, 'Это ты сам')

      const state = friendState(me.id, other.id)
      if (state) {
        if (state.status === 'accepted') return fail(res, 409, 'Вы уже друзья')
        if (state.requested_by === me.id) return fail(res, 409, 'Заявка уже отправлена')
        db.prepare('UPDATE friends SET status = ? WHERE a = ? AND b = ?')
          .run('accepted', state.a, state.b)
        return json(res, 200, { status: 'accepted', user: publicUser(other) })
      }

      const [a, b] = normalizePair(me.id, other.id)
      db.prepare(
        'INSERT INTO friends (a, b, status, requested_by, created_at) VALUES (?, ?, ?, ?, ?)',
      ).run(a, b, 'pending', me.id, now())
      return json(res, 201, { status: 'pending', user: publicUser(other) })
    }

    if (pathname === '/api/friends/answer' && req.method === 'POST') {
      const other = getUser(String(body.userId || ''))
      if (!other) return fail(res, 404, 'Пользователь не найден')
      const [a, b] = normalizePair(me.id, other.id)
      const state = db.prepare('SELECT * FROM friends WHERE a = ? AND b = ?').get(a, b)
      if (!state || state.status !== 'pending' || state.requested_by === me.id) {
        return fail(res, 404, 'Заявка не найдена')
      }
      if (body.accept) {
        db.prepare('UPDATE friends SET status = ? WHERE a = ? AND b = ?').run('accepted', a, b)
        return json(res, 200, { status: 'accepted' })
      }
      db.prepare('DELETE FROM friends WHERE a = ? AND b = ?').run(a, b)
      return json(res, 200, { status: 'declined' })
    }

    const friendDelete = pathname.match(/^\/api\/friends\/([^/]+)$/)
    if (friendDelete && req.method === 'DELETE') {
      const [a, b] = normalizePair(me.id, friendDelete[1])
      db.prepare('DELETE FROM friends WHERE a = ? AND b = ?').run(a, b)
      return json(res, 200, { ok: true })
    }

    // --------------------------------------------------------------- чаты
    if (pathname === '/api/chats' && req.method === 'GET') {
      const rows = db.prepare(
        `SELECT c.* FROM chats c JOIN chat_members m ON m.chat_id = c.id
         WHERE m.user_id = ?`,
      ).all(me.id)
      const chats = rows.map((chat) => chatView(chat, me.id))
      chats.sort((x, y) => {
        const xt = x.lastMessage ? x.lastMessage.createdAt : x.createdAt
        const yt = y.lastMessage ? y.lastMessage.createdAt : y.createdAt
        return yt - xt
      })
      return json(res, 200, { chats })
    }

    if (pathname === '/api/chats/dm' && req.method === 'POST') {
      const other = getUserByName(String(body.username || '').trim())
      if (!other) return fail(res, 404, 'Пользователь не найден')
      if (other.id === me.id) return fail(res, 400, 'Нельзя написать самому себе')
      if (!areFriends(me.id, other.id)) return fail(res, 403, 'Сначала добавь в друзья')

      let chat = findDirectChat(me.id, other.id)
      if (!chat) {
        const id = uid()
        db.prepare(
          'INSERT INTO chats (id, type, title, is_public, code, owner_id, created_at) VALUES (?, ?, NULL, 0, NULL, NULL, ?)',
        ).run(id, 'dm', now())
        db.prepare('INSERT INTO chat_members (chat_id, user_id) VALUES (?, ?)').run(id, me.id)
        db.prepare('INSERT INTO chat_members (chat_id, user_id) VALUES (?, ?)').run(id, other.id)
        chat = db.prepare('SELECT * FROM chats WHERE id = ?').get(id)
      }
      return json(res, 200, { chat: chatView(chat, me.id) })
    }

    if (pathname === '/api/chats/room' && req.method === 'POST') {
      const title = String(body.title || '').trim().slice(0, 40)
      if (!title) return fail(res, 400, 'Введите название комнаты')
      const description = String(body.description || '').trim().slice(0, 200) || null
      const isPublic = body.isPublic ? 1 : 0
      const code = isPublic ? null : randomCode()
      const id = uid()
      db.prepare(
        'INSERT INTO chats (id, type, title, description, is_public, code, owner_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
      ).run(id, 'room', title, description, isPublic, code, me.id, now())
      db.prepare('INSERT INTO chat_members (chat_id, user_id) VALUES (?, ?)').run(id, me.id)
      const chat = db.prepare('SELECT * FROM chats WHERE id = ?').get(id)
      return json(res, 201, { chat: chatView(chat, me.id) })
    }

    if (pathname === '/api/chats/join' && req.method === 'POST') {
      const code = String(body.code || '').trim().toUpperCase()
      const chat = db.prepare("SELECT * FROM chats WHERE code = ? AND type = 'room'").get(code)
      if (!chat) return fail(res, 404, 'Комната с таким кодом не найдена')
      db.prepare('INSERT OR IGNORE INTO chat_members (chat_id, user_id) VALUES (?, ?)')
        .run(chat.id, me.id)
      return json(res, 200, { chat: chatView(chat, me.id) })
    }

    const joinPublic = pathname.match(/^\/api\/chats\/([^/]+)\/join$/)
    if (joinPublic && req.method === 'POST') {
      const chat = db.prepare('SELECT * FROM chats WHERE id = ?').get(joinPublic[1])
      if (!chat) return fail(res, 404, 'Комната не найдена')
      if (!chat.is_public) return fail(res, 403, 'Комната закрытая')
      db.prepare('INSERT OR IGNORE INTO chat_members (chat_id, user_id) VALUES (?, ?)')
        .run(chat.id, me.id)
      return json(res, 200, { chat: chatView(chat, me.id) })
    }

    if (pathname === '/api/rooms' && req.method === 'GET') {
      const rows = db.prepare(
        "SELECT * FROM chats WHERE type = 'room' AND is_public = 1 ORDER BY created_at DESC LIMIT 50",
      ).all()
      const rooms = rows.map((chat) => {
        const view = chatView(chat, me.id)
        return {
          id: view.id,
          title: view.title,
          description: chat.description || null,
          memberCount: view.memberCount,
          isMember: isMember(chat.id, me.id),
          lastMessage: view.lastMessage,
        }
      })
      return json(res, 200, { rooms })
    }

    // Выход из комнаты. Если выходит владелец — комната закрывается сама.
    const leaveRoute = pathname.match(/^\/api\/chats\/([^/]+)\/leave$/)
    if (leaveRoute && req.method === 'POST') {
      const chat = db.prepare('SELECT * FROM chats WHERE id = ? AND type = ?').get(leaveRoute[1], 'room')
      if (!chat) return fail(res, 404, 'Комната не найдена')
      if (chat.owner_id === me.id) {
        db.prepare('DELETE FROM messages WHERE chat_id = ?').run(chat.id)
        db.prepare('DELETE FROM chat_members WHERE chat_id = ?').run(chat.id)
        db.prepare('DELETE FROM chats WHERE id = ?').run(chat.id)
        console.log(`Комната «${chat.title}» закрыта владельцем`)
        return json(res, 200, { closed: true })
      }
      db.prepare('DELETE FROM chat_members WHERE chat_id = ? AND user_id = ?').run(chat.id, me.id)
      return json(res, 200, { closed: false })
    }

    // Выкинуть участника (только владелец).
    const kickRoute = pathname.match(/^\/api\/chats\/([^/]+)\/members\/([^/]+)$/)
    if (kickRoute && req.method === 'DELETE') {
      const chat = db.prepare('SELECT * FROM chats WHERE id = ? AND type = ?').get(kickRoute[1], 'room')
      if (!chat) return fail(res, 404, 'Комната не найдена')
      if (chat.owner_id !== me.id) return fail(res, 403, 'Кикать может только владелец')
      if (kickRoute[2] === me.id) return fail(res, 400, 'Владелец не может кикнуть сам себя')
      db.prepare('DELETE FROM chat_members WHERE chat_id = ? AND user_id = ?').run(chat.id, kickRoute[2])
      return json(res, 200, { ok: true })
    }

    // Изменить название/описание комнаты (только владелец).
    const editRoomRoute = pathname.match(/^\/api\/chats\/([^/]+)$/)
    if (editRoomRoute && req.method === 'PATCH') {
      const chat = db.prepare('SELECT * FROM chats WHERE id = ? AND type = ?').get(editRoomRoute[1], 'room')
      if (!chat) return fail(res, 404, 'Комната не найдена')
      if (chat.owner_id !== me.id) return fail(res, 403, 'Менять может только владелец')
      const title = String(body.title || '').trim().slice(0, 40)
      const description = body.description === undefined ? undefined : String(body.description || '').trim().slice(0, 200) || null
      if (body.title !== undefined) {
        if (!title) return fail(res, 400, 'Название не может быть пустым')
        db.prepare('UPDATE chats SET title = ? WHERE id = ?').run(title, chat.id)
      }
      if (description !== undefined) {
        db.prepare('UPDATE chats SET description = ? WHERE id = ?').run(description, chat.id)
      }
      const updated = db.prepare('SELECT * FROM chats WHERE id = ?').get(chat.id)
      return json(res, 200, { chat: chatView(updated, me.id) })
    }

    const messagesRoute = pathname.match(/^\/api\/chats\/([^/]+)\/messages$/)
    if (messagesRoute) {
      const chatId = messagesRoute[1]
      if (!isMember(chatId, me.id)) return fail(res, 403, 'Ты не в этой комнате')
      const chat = db.prepare('SELECT * FROM chats WHERE id = ?').get(chatId)
      if (!chat) return fail(res, 404, 'Чат не найден')

      if (req.method === 'GET') {
        const after = Number(url.searchParams.get('after') || 0)
        const limit = Math.min(Number(url.searchParams.get('limit') || 200), 200)
        const rows = db.prepare(
          `SELECT * FROM messages WHERE chat_id = ? AND created_at > ?
           ORDER BY created_at ASC LIMIT ?`,
        ).all(chatId, after, limit)
        return json(res, 200, {
          messages: rows.map(messageView),
          chat: chatView(chat, me.id),
        })
      }

      if (req.method === 'POST') {
        const text = String(body.text || '').trim().slice(0, 4000)
        let audioUrl = null
        let audioDuration = null

        if (body.audio && body.audio.data) {
          const size = String(body.audio.data).length
          if (size > 3 * 1024 * 1024) return fail(res, 400, 'Голосовое слишком длинное')
          audioUrl = saveMedia(body.audio.data, extensionFor(body.audio.mime))
          if (!audioUrl) return fail(res, 400, 'Пустое голосовое')
          audioDuration = Math.max(0, Math.min(Number(body.audio.duration || 0), 300))
        }

        if (!text && !audioUrl) return fail(res, 400, 'Пустое сообщение')

        const id = uid()
        db.prepare(
          `INSERT INTO messages (id, chat_id, sender_id, text, audio_url, audio_duration, created_at)
           VALUES (?, ?, ?, ?, ?, ?, ?)`,
        ).run(id, chatId, me.id, text || null, audioUrl, audioDuration, now())
        const row = db.prepare('SELECT * FROM messages WHERE id = ?').get(id)
        return json(res, 201, { message: messageView(row) })
      }
    }

    return fail(res, 404, 'Неизвестный запрос')
  } catch (e) {
    console.error('API error:', e)
    return fail(res, 500, 'Внутренняя ошибка сервера')
  }
})

function randomCode() {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  let value = ''
  for (let i = 0; i < 6; i += 1) {
    value += alphabet[crypto.randomInt(alphabet.length)]
  }
  return value
}

initAdmin()

// Хостинги вроде alwaysdata дают готовый адрес и порт в переменных IP/PORT —
// тогда слушаем именно их, иначе все интерфейсы (домашний Wi-Fi, VPS).
const HOST = process.env.IP || process.env.HOST || '0.0.0.0'

function banner() {
  console.log(`HeadsetConnector server на порту ${PORT}`)
  if (process.env.PUBLIC_URL) {
    console.log(`  адрес: ${process.env.PUBLIC_URL}`)
  } else if (HOST === '0.0.0.0' || HOST === '::') {
    const nets = require('node:os').networkInterfaces()
    for (const list of Object.values(nets)) {
      for (const net of list || []) {
        if (net.family === 'IPv4' && !net.internal) console.log(`  http://${net.address}:${PORT}`)
      }
    }
  } else {
    console.log(`  адрес: http://${HOST.includes(':') ? `[${HOST}]` : HOST}:${PORT}`)
  }
  console.log(`  база: ${DB_PATH}`)
}

server.on('error', (error) => {
  console.error(`Не удалось запустить сервер: ${error.message}`)
  if (error.code === 'EADDRINUSE') {
    console.error(`  порт ${PORT} уже занят — остановите старый процесс`)
  }
  process.exit(1)
})

server.listen(PORT, HOST, banner)

// Автоочистка диска: проверяем раз в 15 минут и при старте.
setInterval(() => {
  try {
    cleanupDisk()
  } catch (_) {
    // не критично
  }
}, 15 * 60 * 1000).unref()

function shutdown(signal) {
  console.log(`\n${signal}: останавливаю сервер…`)
  server.close(() => {
    try {
      db.close()
    } catch (_) {
      // база уже закрыта
    }
    process.exit(0)
  })
  setTimeout(() => process.exit(0), 5_000).unref()
}

process.on('SIGTERM', () => shutdown('SIGTERM'))
process.on('SIGINT', () => shutdown('SIGINT'))

