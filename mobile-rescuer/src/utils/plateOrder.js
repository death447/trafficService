'use strict'

const ORDER_STATUS_TEXT = {
  PENDING: '待派',
  DISPATCHED: '已派',
  ACCEPTED: '已接单'
}

function normalizePlate(raw) {
  return String(raw || '')
    .replace(/[\s·.]/gu, '')
    .toUpperCase()
}

function shouldSearchPlate(raw) {
  return normalizePlate(raw).length >= 2
}

function classifyMatches(list) {
  const rows = list || []
  if (rows.length === 0) return { kind: 'none' }
  if (rows.length === 1) return { kind: 'one', order: rows[0] }
  return { kind: 'many', orders: rows }
}

function applyEmptyOrderFields(form, order) {
  if (!form || !order) return
  if (!String(form.vehicleType || '').trim() && order.vehicleTypeName) {
    form.vehicleType = order.vehicleTypeName
  }
  if (!String(form.rescueAddress || '').trim() && order.accidentAddress) {
    form.rescueAddress = order.accidentAddress
  }
}

function pad2(n) {
  return String(n).padStart(2, '0')
}

function formatCheckedInAt(raw) {
  if (raw == null || raw === '') return ''
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
}

function applyCheckedInAt(form, order) {
  if (!form || !order) return
  const text = formatCheckedInAt(order.checkedInAt)
  if (text) form.rescueTime = text
}

function damagePreviewUrls(paths, toUrl) {
  const list = paths || []
  const out = []
  for (const p of list) {
    if (!p) continue
    out.push(toUrl(p))
  }
  return out
}

function resetRescueTimeNow(form, nowText) {
  if (!form) return
  form.rescueTime = nowText
}

module.exports = {
  ORDER_STATUS_TEXT,
  normalizePlate,
  shouldSearchPlate,
  classifyMatches,
  applyEmptyOrderFields,
  formatCheckedInAt,
  applyCheckedInAt,
  damagePreviewUrls,
  resetRescueTimeNow
}
