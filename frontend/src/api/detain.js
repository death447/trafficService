import request from '../utils/request'

export function listDetains(params) {
  return request.get('/detain/list', { params })
}

export function getDetain(id) {
  return request.get(`/detain/${id}`)
}

export function checkInDetain(data) {
  return request.post('/detain', data)
}

export function updateDetain(id, data) {
  return request.put(`/detain/${id}`, data)
}

export function checkOutDetain(id) {
  return request.post(`/detain/${id}/out`)
}

export function clearDetain(id) {
  return request.post(`/detain/${id}/clear`)
}

export function listDetainMedia(id) {
  return request.get(`/detain/${id}/media`)
}
