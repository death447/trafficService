export const LOCATION_STALE_SECONDS = 180
export const AUTO_CHECKIN_RADIUS_METERS = 500

function toNum(v) {
  if (v == null || v === '') return NaN
  const n = Number(v)
  return Number.isFinite(n) ? n : NaN
}

/** Haversine distance in meters; NaN if any coordinate invalid */
export function distanceMeters(lng1, lat1, lng2, lat2) {
  const a = toNum(lng1)
  const b = toNum(lat1)
  const c = toNum(lng2)
  const d = toNum(lat2)
  if (![a, b, c, d].every(Number.isFinite)) return NaN
  const toRad = (x) => (x * Math.PI) / 180
  const dLat = toRad(d - b)
  const dLng = toRad(c - a)
  const lat1r = toRad(b)
  const lat2r = toRad(d)
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1r) * Math.cos(lat2r) * Math.sin(dLng / 2) ** 2
  return 6371000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
}

export function isLocationFresh(locationUpdatedAt, now = Date.now()) {
  if (locationUpdatedAt == null || locationUpdatedAt === '') return false
  const t = Date.parse(String(locationUpdatedAt).replace(' ', 'T'))
  if (!Number.isFinite(t)) return false
  return now - t <= LOCATION_STALE_SECONDS * 1000
}

export function canAutoCheckin(order, assignedVehicle, now = Date.now()) {
  if (!order || !assignedVehicle) return false
  if (order.status !== 'ACCEPTED') return false
  if (order.checkedInAt) return false
  if (!isLocationFresh(assignedVehicle.locationUpdatedAt, now)) return false
  const meters = distanceMeters(
    order.longitude,
    order.latitude,
    assignedVehicle.longitude,
    assignedVehicle.latitude
  )
  if (!Number.isFinite(meters)) return false
  return meters <= AUTO_CHECKIN_RADIUS_METERS
}
