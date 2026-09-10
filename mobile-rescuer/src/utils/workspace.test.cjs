const test = require('node:test')
const assert = require('node:assert/strict')
const {
  hasRescuerAccess,
  hasParkingAccess,
  resolveLoginTarget,
  shouldStartGps,
  matchHangtag
} = require('./workspace.js')

test('rescuer permission via prefix or module', () => {
  assert.equal(hasRescuerAccess(['rescuer:task']), true)
  assert.equal(hasRescuerAccess(['mobile:rescuer']), true)
  assert.equal(hasRescuerAccess(['detain:query']), false)
})

test('parking permission is detain:query', () => {
  assert.equal(hasParkingAccess(['detain:query']), true)
  assert.equal(hasParkingAccess(['parking:query']), false)
})

test('resolveLoginTarget', () => {
  assert.equal(resolveLoginTarget([]), 'none')
  assert.equal(resolveLoginTarget(['rescuer:task']), 'rescuer')
  assert.equal(resolveLoginTarget(['detain:query']), 'parking')
  assert.equal(resolveLoginTarget(['rescuer:task', 'detain:query']), 'select')
})

test('gps only in rescuer workspace', () => {
  assert.equal(shouldStartGps('rescuer'), true)
  assert.equal(shouldStartGps('parking'), false)
  assert.equal(shouldStartGps(''), false)
})

test('matchHangtag exact detainNo', () => {
  const rows = [
    { id: 1, detainNo: 'DV202609070001', status: 'IN_YARD' },
    { id: 2, detainNo: 'DV202609070002', status: 'OUT' }
  ]
  assert.equal(matchHangtag(rows, '').kind, 'empty')
  assert.equal(matchHangtag(rows, 'RV:1').kind, 'bind-qr')
  assert.equal(matchHangtag(rows, 'DV202609070001').kind, 'in-yard')
  assert.equal(matchHangtag(rows, 'DV202609070002').kind, 'not-in-yard')
  assert.equal(matchHangtag(rows, 'NOPE').kind, 'not-found')
})
