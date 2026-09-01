import request from '../utils/request'

export function getUserList(params) {
  return request.get('/user/list', { params })
}

export function getUserById(id) {
  return request.get(`/user/${id}`)
}

export function createUser(data) {
  return request.post('/user', data)
}

export function updateUser(id, data) {
  return request.put(`/user/${id}`, data)
}

export function deleteUser(id) {
  return request.delete(`/user/${id}`)
}

export function assignUserRoles(userId, roleIds) {
  return request.post(`/user/${userId}/assign-roles`, roleIds)
}

export function getUserRoles(id) {
  return request.get(`/user/${id}/roles`)
}
