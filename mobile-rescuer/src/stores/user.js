const KEYS = ['token', 'userId', 'username', 'permissions', 'roles']

function readPermissions() {
  try {
    const raw = uni.getStorageSync('permissions')
    if (!raw) return []
    return typeof raw === 'string' ? JSON.parse(raw) : raw
  } catch (_) {
    return []
  }
}

function readRoles() {
  try {
    const raw = uni.getStorageSync('roles')
    if (!raw) return []
    return typeof raw === 'string' ? JSON.parse(raw) : raw
  } catch (_) {
    return []
  }
}

export function getUserState() {
  return {
    token: uni.getStorageSync('token') || '',
    userId: uni.getStorageSync('userId') || null,
    username: uni.getStorageSync('username') || '',
    permissions: readPermissions(),
    roles: readRoles()
  }
}

export function setSession(data) {
  uni.setStorageSync('token', data.token || '')
  uni.setStorageSync('userId', data.userId != null ? String(data.userId) : '')
  uni.setStorageSync('username', data.username || '')
  uni.setStorageSync('permissions', JSON.stringify(data.permissions || []))
  uni.setStorageSync('roles', JSON.stringify(data.roles || []))
}

export function clearSession() {
  KEYS.forEach((k) => uni.removeStorageSync(k))
}

export function hasRescuerAccess(permissions = readPermissions()) {
  const list = permissions || []
  return list.some(
    (p) => p === 'mobile:rescuer' || (typeof p === 'string' && p.startsWith('rescuer:'))
  )
}

export function logout() {
  clearSession()
  uni.reLaunch({ url: '/pages/login/index' })
}

export default {
  getUserState,
  setSession,
  clearSession,
  hasRescuerAccess,
  logout
}
