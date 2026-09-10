const test = require('node:test')
const assert = require('node:assert/strict')
const { rewriteCjsNamedExports } = require('./cjsNamedExportsToEsm.cjs')

test('rewrites httpError module.exports to named ESM export', () => {
  const input = `'use strict'\nfunction httpErrorMessage() {}\nmodule.exports = { httpErrorMessage }\n`
  const out = rewriteCjsNamedExports(input)
  assert.match(out, /export \{ httpErrorMessage \}/)
  assert.equal(out.includes('module.exports'), false)
})

test('rewrites workspace-style multi export', () => {
  const input = 'module.exports = {\n  hasRescuerAccess,\n  hasParkingAccess\n}\n'
  const out = rewriteCjsNamedExports(input)
  assert.match(out, /export \{ hasRescuerAccess, hasParkingAccess \}/)
})
