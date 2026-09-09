import { request, uploadFile } from '../utils/request'

const PREFIX = '/mobile/rescuer'

export function getProfile() {
  return request({ url: `${PREFIX}/profile` })
}

export function updateProfile(data) {
  return request({ url: `${PREFIX}/profile`, method: 'PUT', data })
}

export function bindVehicle(qrPayload) {
  return request({ url: `${PREFIX}/bind-vehicle`, method: 'POST', data: { qrPayload } })
}

export function getBoundVehicle({ showError = true } = {}) {
  return request({ url: `${PREFIX}/vehicle`, showError })
}

export function reportLocation(lng, lat, accuracy) {
  const data = { lng, lat }
  if (accuracy != null) data.accuracy = accuracy
  return request({ url: `${PREFIX}/location`, method: 'POST', data, showError: false })
}

export function listTasks(tab = 'todo') {
  return request({ url: `${PREFIX}/tasks`, data: { tab } })
}

export function getTask(id) {
  return request({ url: `${PREFIX}/tasks/${id}` })
}

export function acceptTask(id) {
  return request({ url: `${PREFIX}/tasks/${id}/accept`, method: 'POST' })
}

export function rejectTask(id, reason) {
  return request({ url: `${PREFIX}/tasks/${id}/reject`, method: 'POST', data: { reason } })
}

export function checkinTask(id, data, { showError = true } = {}) {
  return request({ url: `${PREFIX}/tasks/${id}/checkin`, method: 'POST', data, showError })
}

export function saveScene(id, data) {
  return request({ url: `${PREFIX}/tasks/${id}/scene`, method: 'POST', data })
}

export function savePark(id, data) {
  return request({ url: `${PREFIX}/tasks/${id}/park`, method: 'POST', data })
}

export function listMedia(id) {
  return request({ url: `${PREFIX}/tasks/${id}/media` })
}

export function uploadMedia(id, filePath, bizType) {
  return uploadFile({
    url: `${PREFIX}/tasks/${id}/media`,
    filePath,
    name: 'file',
    formData: { bizType }
  })
}

export function completeTask(id) {
  return request({ url: `${PREFIX}/tasks/${id}/complete`, method: 'POST' })
}
