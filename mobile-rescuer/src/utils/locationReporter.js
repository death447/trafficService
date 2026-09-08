import { getUserState } from '../stores/user'
import { getBoundVehicle, reportLocation } from '../api/rescuer'

const INTERVAL_MS = 30000
let timer = null
let running = false
let unboundNotified = false

function getLocationOnce() {
  return new Promise((resolve, reject) => {
    uni.getLocation({
      type: 'gcj02',
      success: (res) => resolve(res),
      fail: (err) => reject(err)
    })
  })
}

async function tick() {
  const { token } = getUserState()
  if (!token) {
    stopLocationReporter()
    return
  }
  try {
    const bound = await getBoundVehicle({ showError: false })
    if (!bound?.data?.id) {
      if (!unboundNotified) {
        unboundNotified = true
        // 非阻塞：控制台即可，避免频繁 toast
        console.warn('[locationReporter] 未绑定车辆，跳过上报')
      }
      return
    }
    unboundNotified = false
    const loc = await getLocationOnce()
    await reportLocation(loc.longitude, loc.latitude, loc.accuracy)
  } catch (e) {
    const msg = e?.message || e?.errMsg || String(e)
    if (typeof msg === 'string' && msg.includes('绑定')) {
      stopLocationReporter()
    }
    // 定位失败静默
  }
}

export function startLocationReporter() {
  const { token } = getUserState()
  if (!token) return
  // Restart-safe: no-op when already running with an active timer;
  // if a prior early-return left running=false, start works; if running without timer, ensure one.
  if (running && timer) return
  running = true
  tick()
  if (!timer) {
    timer = setInterval(tick, INTERVAL_MS)
  }
}

export function stopLocationReporter() {
  running = false
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}
