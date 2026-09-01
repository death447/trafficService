import request from '../utils/request'

export function getRoleList(params) {
  return request.get('/role/list', { params })
}

export function getRoleById(id) {
  return request.get(`/role/${id}`)
}

export function createRole(data) {
  return request.post('/role', data)
}

export function updateRole(id, data) {
  return request.put(`/role/${id}`, data)
}

export function deleteRole(id) {
  return request.delete(`/role/${id}`)
}

export function getRolePermissions(id) {
  return request.get(`/role/${id}/permissions`)
}

export function assignRolePermissions(id, permissionIds) {
  return request.post(`/role/${id}/assign-permissions`, permissionIds)
}
