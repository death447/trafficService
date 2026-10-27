import request from '../utils/request'

export function listDictItems(params) {
  return request.get('/dict/list', { params })
}

export function listEnabledDict(type) {
  return request.get('/dict/enabled', { params: { type } })
}

export function createDictItem(data) {
  return request.post('/dict', data)
}

export function updateDictItem(id, data) {
  return request.put(`/dict/${id}`, data)
}

export function deleteDictItem(id) {
  return request.delete(`/dict/${id}`)
}
