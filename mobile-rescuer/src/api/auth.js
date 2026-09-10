import { request } from '../utils/request'

export function loginApi(data) {
  return request({
    url: '/auth/login',
    method: 'POST',
    data,
    showError: false
  })
}

export function getMe() {
  return request({ url: '/auth/me' })
}

export function updateMe(data) {
  return request({ url: '/auth/me', method: 'PUT', data })
}
