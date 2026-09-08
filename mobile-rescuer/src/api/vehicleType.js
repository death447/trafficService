import { request } from '../utils/request'

export function listEnabledVehicleTypes() {
  return request({ url: '/vehicle-type/enabled' })
}
