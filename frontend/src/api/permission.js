import request from '../utils/request'

export function getPermissionList(params) {
  return request.get('/permission/list', { params })
}

export function getPermissionTree() {
  return request.get('/permission/tree')
}

export function getPermissionById(id) {
  return request.get(`/permission/${id}`)
}

export function createPermission(data) {
  return request.post('/permission', data)
}

export function updatePermission(id, data) {
  return request.put(`/permission/${id}`, data)
}

export function deletePermission(id) {
  return request.delete(`/permission/${id}`)
}
