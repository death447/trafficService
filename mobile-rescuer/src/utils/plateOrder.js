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

module.exports = {
  ORDER_STATUS_TEXT,
  normalizePlate,
  shouldSearchPlate,
  classifyMatches,
  applyEmptyOrderFields
}
