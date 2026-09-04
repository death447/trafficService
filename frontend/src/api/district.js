import request from '../utils/request'

export function listDistricts(params) {
  return request.get('/district/list', { params })
}

export function getDistrict(id) {
  return request.get(`/district/${id}`)
}

export function createDistrict(data) {
  return request.post('/district', data)
}

export function updateDistrict(id, data) {
  return request.put(`/district/${id}`, data)
}

export function deleteDistrict(id) {
  return request.delete(`/district/${id}`)
}

export function resolveDistrict(params) {
  return request.get('/district/resolve', { params })
}
