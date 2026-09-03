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
    script.onload = () => resolve(window.AMap)
    script.onerror = () => {
      loading = undefined
      reject(new Error('高德地图加载失败'))
    }
    document.head.appendChild(script)
  })
  return loading
}

/** 在 container 元素上创建地图；点击设标记并逆地理（若 Geocoder 可用） */
export async function createPickerMap(container, { lng, lat, onPicked }) {
  const AMap = await loadAmap()
  const center = lng && lat ? [Number(lng), Number(lat)] : [114.057868, 22.543099]
  const map = new AMap.Map(container, { zoom: 13, center })
  let marker
  map.on('click', (e) => {
    const { lng: x, lat: y } = e.lnglat
    if (!marker) marker = new AMap.Marker({ position: [x, y], map })
    else marker.setPosition([x, y])
    const done = (address) => onPicked?.({ lng: x, lat: y, address: address || '' })
    if (AMap.plugin) {
      AMap.plugin('AMap.Geocoder', () => {
        const geocoder = new AMap.Geocoder()
        geocoder.getAddress([x, y], (status, result) => {
          if (status === 'complete' && result.regeocode) {
            done(result.regeocode.formattedAddress)
          } else done('')
        })
      })
    } else done('')
  })
  return map
}
