import { request } from '../utils/request'

export function listDetains(params) {
  return request({ url: '/detain/list', data: params })
}

export function getDetain(id) {
  return request({ url: `/detain/${id}` })
}

export function checkOutDetain(id) {
  return request({ url: `/detain/${id}/out`, method: 'POST' })
}

export function listDetainMedia(id) {
  return request({ url: `/detain/${id}/media` })
}
