'use strict'

function hasRescuerAccess(permissions) {
  const list = permissions || []
  return list.some(
    (p) => p === 'mobile:rescuer' || (typeof p === 'string' && p.startsWith('rescuer:'))
  )
}

function hasParkingAccess(permissions) {
  return (permissions || []).includes('detain:query')
}

function hasPoliceAccess(permissions) {
  const list = permissions || []
  return list.some(
    (p) => p === 'accident:manage' || (typeof p === 'string' && p.startsWith('accident:'))
  )
}

function resolveLoginTarget(permissions) {
  const kinds = []
  if (hasRescuerAccess(permissions)) kinds.push('rescuer')
  if (hasParkingAccess(permissions)) kinds.push('parking')
  if (hasPoliceAccess(permissions)) kinds.push('police')
  if (kinds.length === 0) return 'none'
  if (kinds.length === 1) return kinds[0]
  return 'select'
}

function shouldStartGps(workspace) {
  return workspace === 'rescuer'
}

function workspaceMatchesKind(kind, workspace) {
  if (kind === 'rescuer' || kind === 'parking' || kind === 'police') {
    return workspace === kind
  }
  return true
}

function canRatePoliceTask(status, rated) {
  return status === 'COMPLETED' && !rated
}

function confirmInputValue(event, fallback) {
  if (event && event.detail && Object.prototype.hasOwnProperty.call(event.detail, 'value')) {
    return String(event.detail.value)
  }
  return fallback == null ? '' : String(fallback)
}

function normalizeHangtagText(payload) {
  return String(payload || '')
    .replace(/[\u200B-\u200D\uFEFF]/g, '')
    .normalize('NFKC')
    .trim()
}

function matchHangtag(list, payload) {
  const text = normalizeHangtagText(payload)
  if (!text) return { kind: 'empty' }
  if (/^RV:\d+$/.test(text)) return { kind: 'bind-qr' }
  const record = (list || []).find((row) => row && normalizeHangtagText(row.detainNo) === text)
  if (!record) return { kind: 'not-found' }
  if (record.status === 'IN_YARD') return { kind: 'in-yard', record }
  return { kind: 'not-in-yard', record }
}

module.exports = {
  hasRescuerAccess,
  hasParkingAccess,
  hasPoliceAccess,
  resolveLoginTarget,
  shouldStartGps,
  workspaceMatchesKind,
  canRatePoliceTask,
  matchHangtag,
  confirmInputValue,
  normalizeHangtagText
}
