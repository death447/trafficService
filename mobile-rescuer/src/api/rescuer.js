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

export function uploadMedia(id, filePath, bizType, accidentVehicleId) {
  const formData = { bizType }
  if (accidentVehicleId != null) formData.accidentVehicleId = accidentVehicleId
  return uploadFile({
    url: `${PREFIX}/tasks/${id}/media`,
    filePath,
    name: 'file',
    formData
  })
}

export function deleteMedia(id, mediaId) {
  return request({ url: `${PREFIX}/tasks/${id}/media/${mediaId}`, method: 'DELETE' })
}

export function completeTask(id) {
  return request({ url: `${PREFIX}/tasks/${id}/complete`, method: 'POST' })
}

export function listRescuerParkingLots() {
  return request({ url: `${PREFIX}/parking-lots` })
}

export function listRescuerParkingAreas(lotId) {
  return request({ url: `${PREFIX}/parking-lots/${lotId}/areas` })
}

export function detainInTask(id, data) {
  return request({ url: `${PREFIX}/tasks/${id}/detain-in`, method: 'POST', data })
}

export function getTaskDetain(id) {
  return request({ url: `${PREFIX}/tasks/${id}/detain` })
}

export function getTaskEntryNo(id) {
  return request({ url: `${PREFIX}/tasks/${id}/entry-no`, showError: false })
}

export function uploadDetainPhoto(detainId, filePath) {
  return uploadFile({
    url: `${PREFIX}/detain/${detainId}/media`,
    filePath,
    name: 'file',
    formData: { bizType: 'PARK' }
  })
}

export function listDictOptions(type) {
  return request({ url: '/dict/enabled', data: { type }, showError: false })
}
