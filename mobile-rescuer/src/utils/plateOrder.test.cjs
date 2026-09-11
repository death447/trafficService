'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const {
  normalizePlate,
  shouldSearchPlate,
  classifyMatches,
  applyEmptyOrderFields,
  applyCheckedInAt,
  damagePreviewUrls,
  resetRescueTimeNow
} = require('./plateOrder.js')

test('normalizePlate strips space middle-dot dot and uppercases', () => {
  assert.equal(normalizePlate(' 粤b·12345 '), '粤B12345')
  assert.equal(normalizePlate('粤B.12345'), '粤B12345')
  assert.equal(normalizePlate(null), '')
})

test('shouldSearchPlate requires two normalized chars', () => {
  assert.equal(shouldSearchPlate(' ·A. '), false)
  assert.equal(shouldSearchPlate('粤B'), true)
})

test('classifyMatches one many none', () => {
  assert.equal(classifyMatches([]).kind, 'none')
  assert.equal(classifyMatches([{ id: 1 }]).kind, 'one')
  assert.equal(classifyMatches([{ id: 1 }]).order.id, 1)
  assert.equal(classifyMatches([{ id: 1 }, { id: 2 }]).kind, 'many')
  assert.equal(classifyMatches([{ id: 1 }, { id: 2 }]).orders.length, 2)
})

test('applyEmptyOrderFields fills only blanks', () => {
  const blank = { vehicleType: '', rescueAddress: '' }
  applyEmptyOrderFields(blank, { vehicleTypeName: '小型汽车', accidentAddress: '事故路' })
  assert.equal(blank.vehicleType, '小型汽车')
  assert.equal(blank.rescueAddress, '事故路')

  const filled = { vehicleType: '已填', rescueAddress: '已有地点' }
  applyEmptyOrderFields(filled, { vehicleTypeName: '覆盖', accidentAddress: '覆盖地' })
  assert.equal(filled.vehicleType, '已填')
  assert.equal(filled.rescueAddress, '已有地点')
})

test('applyCheckedInAt overwrites only when checked in', () => {
  const form = { rescueTime: '2026-09-11 10:00:00' }
  applyCheckedInAt(form, {})
  assert.equal(form.rescueTime, '2026-09-11 10:00:00')
  applyCheckedInAt(form, { checkedInAt: '2026-09-09T16:38:15' })
  assert.equal(form.rescueTime, '2026-09-09 16:38:15')
})

test('damagePreviewUrls maps paths', () => {
  const urls = damagePreviewUrls(['dispatch/3/a.jpg', '', null], (p) => `/uploads/${p}`)
  assert.deepEqual(urls, ['/uploads/dispatch/3/a.jpg'])
})

test('resetRescueTimeNow writes provided now', () => {
  const form = { rescueTime: 'old' }
  resetRescueTimeNow(form, '2026-09-11 11:00:00')
  assert.equal(form.rescueTime, '2026-09-11 11:00:00')
})
