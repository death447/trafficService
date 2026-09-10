import request from '../utils/request'

export function listParkings(params) {
  return request.get('/parking/list', { params })
}

export function getParking(id) {
  return request.get(`/parking/${id}`)
}

export function createParking(data) {
  return request.post('/parking', data)
}

export function updateParking(id, data) {
  return request.put(`/parking/${id}`, data)
}

export function deleteParking(id) {
  return request.delete(`/parking/${id}`)
}

export function listParkingAreas(lotId, params) {
  return request.get(`/parking/${lotId}/areas`, { params })
}

export function createParkingArea(lotId, data) {
  return request.post(`/parking/${lotId}/areas`, data)
}

export function updateParkingArea(id, data) {
  return request.put(`/parking/areas/${id}`, data)
}

export function deleteParkingArea(id) {
  return request.delete(`/parking/areas/${id}`)
}
