import request from '../utils/request'

export function listSchedules(params) {
  return request.get('/schedule/list', { params })
}

export function getSchedule(id) {
  return request.get(`/schedule/${id}`)
}

export function createSchedule(data) {
  return request.post('/schedule', data)
}

export function updateSchedule(id, data) {
  return request.put(`/schedule/${id}`, data)
}

export function deleteSchedule(id) {
  return request.delete(`/schedule/${id}`)
}
