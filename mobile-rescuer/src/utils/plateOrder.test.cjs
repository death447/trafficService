'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const {
  normalizePlate,
  shouldSearchPlate,
  classifyMatches,
  applyEmptyOrderFields
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
