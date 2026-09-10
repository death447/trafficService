import { startLocationReporter, stopLocationReporter } from '../utils/locationReporter'
import { hasRescuerAccess as checkRescuerAccess, shouldStartGps } from '../utils/workspace.js'

const KEYS = ['token', 'userId', 'username', 'permissions', 'roles', 'workspace']

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
    roles: readRoles(),
    workspace: uni.getStorageSync('workspace') || ''
  }
}

export function setSession(data) {
  uni.setStorageSync('token', data.token || '')
  uni.setStorageSync('userId', data.userId != null ? String(data.userId) : '')
  uni.setStorageSync('username', data.username || '')
  uni.setStorageSync('permissions', JSON.stringify(data.permissions || []))
  uni.setStorageSync('roles', JSON.stringify(data.roles || []))
  uni.setStorageSync('workspace', data.workspace || '')
}

export function clearSession() {
  KEYS.forEach((k) => uni.removeStorageSync(k))
}

export function enterWorkspace(name) {
  uni.setStorageSync('workspace', name)
  if (shouldStartGps(name)) {
    startLocationReporter()
  } else {
    stopLocationReporter()
  }
}

export function hasRescuerAccess(permissions = readPermissions()) {
  return checkRescuerAccess(permissions)
}

export function logout() {
  stopLocationReporter()
  clearSession()
  uni.reLaunch({ url: '/pages/login/index' })
}

export default {
  getUserState,
  setSession,
  clearSession,
  enterWorkspace,
  hasRescuerAccess,
  logout
}
