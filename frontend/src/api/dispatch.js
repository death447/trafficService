import request from '../utils/request'

export function listDispatches(params) {
  return request.get('/dispatch/list', { params })
}

export function getDispatch(id) {
  return request.get(`/dispatch/${id}`)
}

export function createDispatch(data) {
  return request.post('/dispatch', data)
}

export function updateDispatch(id, data) {
  return request.put(`/dispatch/${id}`, data)
}

export function assignDispatch(id, data) {
  return request.post(`/dispatch/${id}/assign`, data)
}

export function completeDispatch(id) {
  return request.post(`/dispatch/${id}/complete`)
}

export function abortDispatch(id, data) {
  return request.post(`/dispatch/${id}/abort`, data)
}
