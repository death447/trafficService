import { request } from '../utils/request'

export function loginApi(data) {
  return request({
    url: '/auth/login',
    method: 'POST',
    data,
    showError: false
  })
}
