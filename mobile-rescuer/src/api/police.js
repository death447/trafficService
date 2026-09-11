import { request } from '../utils/request'

const PREFIX = '/mobile/police'

export function listPoliceTasks(params) {
  return request({ url: `${PREFIX}/tasks`, data: params })
}

export function getPoliceTask(id) {
  return request({ url: `${PREFIX}/tasks/${id}` })
}

export function ratePoliceTask(id, data) {
  return request({ url: `${PREFIX}/tasks/${id}/rate`, method: 'POST', data })
}
