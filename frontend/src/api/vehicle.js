import request from '../utils/request'

export function listVehicles(params) {
  return request.get('/vehicle/list', { params })
}

export function getVehicle(id) {
  return request.get(`/vehicle/${id}`)
}

export function createVehicle(data) {
  return request.post('/vehicle', data)
}

export function updateVehicle(id, data) {
  return request.put(`/vehicle/${id}`, data)
}

export function deleteVehicle(id) {
  return request.delete(`/vehicle/${id}`)
}

export function nearbyVehicles(params) {
  return request.get('/vehicle/nearby', { params })
}
