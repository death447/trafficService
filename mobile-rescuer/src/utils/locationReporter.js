import { getBoundVehicle, reportLocation } from '../api/rescuer'
import { shouldStartGps } from './workspace.js'

const INTERVAL_MS = 30000
let timer = null
let running = false
let unboundNotified = false
let lastFailLogAt = 0

function hasToken() {
  return Boolean(uni.getStorageSync('token'))
}

function logFail(msg, detail) {
  const now = Date.now()
  // throttle console noise to once / 15s
  if (now - lastFailLogAt < 15000) return
  lastFailLogAt = now
  console.warn('[locationReporter]', msg, detail || '')
}

function isH5() {
  return typeof window !== 'undefined' && typeof document !== 'undefined'
}

/**
 * H5 must not call uni.getLocation: without manifest map key it falls back to
 * IP locate, waits on Google/DCloud, then the runtime toasts「链接服务器超时」.
 * Native App still uses uni.getLocation.
 */
function getLocationOnce() {
  if (isH5()) {
    if (!navigator.geolocation) {
      return Promise.reject(new Error('h5-no-geolocation'))
    }
    return new Promise((resolve, reject) => {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          resolve({
            longitude: pos.coords.longitude,
            latitude: pos.coords.latitude,
            accuracy: pos.coords.accuracy
          })
        },
        (err) => reject(err),
        { enableHighAccuracy: false, timeout: 8000, maximumAge: 60000 }
      )
    })
  }
  return new Promise((resolve, reject) => {
    uni.getLocation({
      type: 'gcj02',
      isHighAccuracy: true,
      highAccuracyExpireTime: 5000,
      success: (res) => resolve(res),
      fail: (err) => reject(err)
    })
  })
}

async function tick() {
  if (!hasToken()) {
    stopLocationReporter()
    return
  }
  try {
    const bound = await getBoundVehicle({ showError: false })
    if (!bound?.data?.id) {
      if (!unboundNotified) {
        unboundNotified = true
        logFail('未绑定车辆，跳过上报')
      }
      return
    }
    unboundNotified = false
    const loc = await getLocationOnce()
    if (loc?.longitude == null || loc?.latitude == null) {
      logFail('定位结果无经纬度')
      return
    }
    await reportLocation(loc.longitude, loc.latitude, loc.accuracy)
  } catch (e) {
    const msg = e?.message || e?.errMsg || e?.code || String(e)
    if (typeof msg === 'string' && msg.includes('绑定')) {
      stopLocationReporter()
      return
    }
    logFail('本轮上报失败（定位或接口）', msg)
  }
}

export function startLocationReporter() {
  if (!hasToken()) return
  if (!shouldStartGps(uni.getStorageSync('workspace') || '')) {
    stopLocationReporter()
    return
  }
  // Restart-safe: ensure interval is active
  if (running && timer) {
    return
  }
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
