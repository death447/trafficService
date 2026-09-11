const test = require('node:test')
const assert = require('node:assert/strict')
const {
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
} = require('./workspace.js')
const { httpErrorMessage } = require('./httpError.js')

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
  assert.equal(resolveLoginTarget(['accident:query']), 'police')
  assert.equal(resolveLoginTarget(['accident:manage']), 'police')
  assert.equal(resolveLoginTarget(['rescuer:task', 'detain:query']), 'select')
  assert.equal(resolveLoginTarget(['rescuer:task', 'accident:query']), 'select')
  assert.equal(resolveLoginTarget(['detain:query', 'accident:rate']), 'select')
  assert.equal(resolveLoginTarget(['rescuer:task', 'detain:query', 'accident:query']), 'select')
})

test('police permission via module or prefix', () => {
  assert.equal(hasPoliceAccess(['accident:manage']), true)
  assert.equal(hasPoliceAccess(['accident:query']), true)
  assert.equal(hasPoliceAccess(['rescuer:task']), false)
})

test('gps only in rescuer workspace', () => {
  assert.equal(shouldStartGps('rescuer'), true)
  assert.equal(shouldStartGps('parking'), false)
  assert.equal(shouldStartGps('police'), false)
  assert.equal(shouldStartGps(''), false)
})

test('workspaceMatchesKind', () => {
  assert.equal(workspaceMatchesKind('police', 'police'), true)
  assert.equal(workspaceMatchesKind('police', 'rescuer'), false)
  assert.equal(workspaceMatchesKind('rescuer', 'police'), false)
  assert.equal(workspaceMatchesKind('shared', 'police'), true)
})

test('canRatePoliceTask', () => {
  assert.equal(canRatePoliceTask('COMPLETED', false), true)
  assert.equal(canRatePoliceTask('COMPLETED', true), false)
  assert.equal(canRatePoliceTask('ACCEPTED', false), false)
  assert.equal(canRatePoliceTask('PENDING', false), false)
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
  assert.equal(matchHangtag(rows, 'ＤＶ202609070001').kind, 'in-yard')
  assert.equal(matchHangtag(rows, '\u200bDV202609070001').kind, 'in-yard')
})

test('confirmInputValue prefers confirm event over stale v-model', () => {
  assert.equal(confirmInputValue({ detail: { value: '1231231231' } }, ''), '1231231231')
  assert.equal(confirmInputValue({ detail: { value: '1231231231' } }, 'old'), '1231231231')
  assert.equal(confirmInputValue(undefined, 'typed'), 'typed')
  assert.equal(confirmInputValue({ detail: {} }, 'typed'), 'typed')
})

test('normalizeHangtagText maps fullwidth digits and strips zwsp', () => {
  assert.equal(normalizeHangtagText('１２３１２３１２３１'), '1231231231')
  assert.equal(normalizeHangtagText('\u200bDV202609070001\u200b'), 'DV202609070001')
})

test('httpErrorMessage uses body.message or 无权限 for 403', () => {
  assert.equal(httpErrorMessage(403, { message: '禁止访问扣车' }), '禁止访问扣车')
  assert.equal(httpErrorMessage(403, {}), '无权限')
  assert.equal(httpErrorMessage(403, null), '无权限')
  assert.equal(httpErrorMessage(500, { message: '服务器错误' }), '服务器错误')
  assert.equal(httpErrorMessage(500, 'oops'), 'HTTP 500')
})
