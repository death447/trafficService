import { request, uploadFile } from '../utils/request'

export function listDetains(params) {
  return request({ url: '/detain/list', data: params })
}

export function getDetain(id) {
  return request({ url: `/detain/${id}` })
}

export function checkInDetain(body) {
  return request({ url: '/detain', method: 'POST', data: body })
}

export function listActiveOrders(plateNo) {
  return request({ url: '/detain/active-orders', data: { plateNo } })
}

export function checkOutDetain(id) {
  return request({ url: `/detain/${id}/out`, method: 'POST' })
}

export function listDetainMedia(id) {
  return request({ url: `/detain/${id}/media` })
}

export function uploadDetainMedia(id, filePath, bizType) {
  return uploadFile({
    url: `/detain/${id}/media`,
    filePath,
    name: 'file',
    formData: { bizType }
  })
}
