import { request } from '../utils/request'

export function listParkings(params) {
  return request({ url: '/parking/list', data: params })
}
