import { request } from '../utils/request'

export function listParkings(params) {
  return request({ url: '/parking/list', data: params })
}

export function listParkingAreas(lotId, params) {
  return request({ url: `/parking/${lotId}/areas`, data: params })
}
