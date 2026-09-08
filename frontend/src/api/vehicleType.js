import request from '../utils/request'

export function listVehicleTypes(params) {
  return request.get('/vehicle-type/list', { params })
}

export function listEnabledVehicleTypes() {
  return request.get('/vehicle-type/enabled')
}

export function createVehicleType(data) {
  return request.post('/vehicle-type', data)
}

export function updateVehicleType(id, data) {
  return request.put(`/vehicle-type/${id}`, data)
}
