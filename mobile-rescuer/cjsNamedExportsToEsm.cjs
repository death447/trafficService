'use strict'

function rewriteCjsNamedExports(code) {
  const match = code.match(/module\.exports\s*=\s*\{([^}]+)\}/)
  if (!match) return code
  const names = match[1]
    .split(',')
    .map((part) => part.trim().split(':')[0].trim())
    .filter(Boolean)
  if (!names.length) return code
  return code.replace(
    /module\.exports\s*=\s*\{([^}]+)\}/,
    `export { ${names.join(', ')} }`
  )
}

module.exports = { rewriteCjsNamedExports }
