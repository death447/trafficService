import { request } from '../utils/request'

export function listDetains(params) {
  return request({ url: '/detain/list', data: params })
}

export function getDetain(id) {
  return request({ url: `/detain/${id}` })
}

export function checkInDetain(body) {
  return request({ url: '/detain', method: 'POST', data: body })
}

export function checkOutDetain(id) {
  return request({ url: `/detain/${id}/out`, method: 'POST' })
}
