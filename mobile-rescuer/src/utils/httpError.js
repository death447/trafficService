'use strict'

function httpErrorMessage(statusCode, body) {
  if (body && typeof body === 'object' && body.message) {
    return String(body.message)
  }
  return statusCode === 403 ? '无权限' : `HTTP ${statusCode}`
}

module.exports = { httpErrorMessage }
