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

function resolveLoginTarget(permissions) {
  const rescuer = hasRescuerAccess(permissions)
  const parking = hasParkingAccess(permissions)
  if (!rescuer && !parking) return 'none'
  if (rescuer && parking) return 'select'
  return rescuer ? 'rescuer' : 'parking'
}

function shouldStartGps(workspace) {
  return workspace === 'rescuer'
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
  resolveLoginTarget,
  shouldStartGps,
  matchHangtag,
  confirmInputValue,
  normalizeHangtagText
}
