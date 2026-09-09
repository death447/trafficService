const KEY = import.meta.env.VITE_AMAP_KEY

export function hasAmapKey() {
  return Boolean(KEY)
}

let loading
export function loadAmap() {
  if (!KEY) return Promise.reject(new Error('缺少 VITE_AMAP_KEY'))
  if (window.AMap) return Promise.resolve(window.AMap)
  if (loading) return loading
  if (import.meta.env.VITE_AMAP_SECURITY_CODE) {
    window._AMapSecurityConfig = {
      securityJsCode: import.meta.env.VITE_AMAP_SECURITY_CODE
    }
  }
  loading = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${KEY}`
    script.onload = () => {
      // AMap 2.0 may attach slightly after onload
      const tryResolve = (left) => {
        if (window.AMap) {
          resolve(window.AMap)
          return
        }
        if (left <= 0) {
          loading = undefined
          reject(new Error('高德地图对象未就绪'))
          return
        }
        setTimeout(() => tryResolve(left - 1), 50)
      }
      tryResolve(40)
    }
    script.onerror = () => {
      loading = undefined
      reject(new Error('高德地图加载失败'))
    }
    document.head.appendChild(script)
  })
  return loading
}

/** Inline SVG yellow mini truck (no circle) — data URL for AMap.Icon */
const VEHICLE_ICON_SVG = encodeURIComponent(
  `<svg xmlns="http://www.w3.org/2000/svg" width="48" height="40" viewBox="0 0 48 40">
    <g transform="translate(2 2)">
      <!-- cargo bed -->
      <rect x="1" y="10" width="22" height="14" rx="1.5" fill="#f5c518" stroke="#c9a000" stroke-width="1"/>
      <rect x="3" y="12" width="18" height="10" rx="1" fill="#ffe066"/>
      <!-- cab -->
      <path d="M24 14h8.5L38 20.5V24H24V14z" fill="#f0b400" stroke="#c9a000" stroke-width="1"/>
      <path d="M26 15.5h5.5L35 20H26V15.5z" fill="#fff3bf"/>
      <!-- bumper -->
      <rect x="37.5" y="21" width="4" height="5" rx="1" fill="#d4a017"/>
      <!-- wheels -->
      <circle cx="10" cy="27" r="5" fill="#333"/>
      <circle cx="10" cy="27" r="2.2" fill="#ccc"/>
      <circle cx="30" cy="27" r="5" fill="#333"/>
      <circle cx="30" cy="27" r="2.2" fill="#ccc"/>
    </g>
  </svg>`.replace(/\s+/g, ' ')
)

const VEHICLE_ICON_URL = `data:image/svg+xml,${VEHICLE_ICON_SVG}`

/**
 * AMap icon for rescue vehicle markers.
 * @param {typeof window.AMap} AMap
 */
export function createVehicleMapIcon(AMap) {
  return new AMap.Icon({
    size: new AMap.Size(44, 36),
    image: VEHICLE_ICON_URL,
    imageSize: new AMap.Size(44, 36)
  })
}
