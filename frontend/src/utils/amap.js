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

/** 多边形围栏编辑：点击追加顶点；无 Key 时由页面用 textarea 编辑 JSON */
export async function createPolygonEditor(container, { path = [], onChange } = {}) {
  const AMap = await loadAmap()
  const map = new AMap.Map(container, { zoom: 12, center: [114.057868, 22.543099] })
  let polygon
  const apply = (ring) => {
    const pathLL = ring.map((p) => [p.lng, p.lat])
    if (!polygon) {
      polygon = new AMap.Polygon({
        path: pathLL,
        map,
        strokeWeight: 2,
        strokeColor: '#1f6fa8',
        fillColor: '#3aa0df',
        fillOpacity: 0.25
      })
    } else {
      polygon.setPath(pathLL)
    }
    if (pathLL.length) {
      map.setFitView([polygon])
    }
    onChange?.(ring)
  }
  if (path.length) apply(path)
  map.on('click', (e) => {
    const current = polygon
      ? polygon.getPath().map((ll) => ({ lng: ll.lng, lat: ll.lat }))
      : []
    apply([...current, { lng: e.lnglat.lng, lat: e.lnglat.lat }])
  })
  return {
    map,
    setPath: apply,
    undo() {
      if (!polygon) return
      const current = polygon.getPath().map((ll) => ({ lng: ll.lng, lat: ll.lat }))
      if (!current.length) return
      const next = current.slice(0, -1)
      if (!next.length) {
        map.remove(polygon)
        polygon = null
        onChange?.([])
        return
      }
      apply(next)
    },
    clear() {
      if (polygon) {
        map.remove(polygon)
        polygon = null
      }
      onChange?.([])
    },
    destroy() {
      if (map && typeof map.destroy === 'function') map.destroy()
    }
  }
}
