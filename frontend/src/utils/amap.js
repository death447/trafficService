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

/**
 * 选点地图：定位 / 搜索 / 点击与拖拽标记 + 逆地理。
 * @returns {{ map, setMarker(lng, lat, address?), destroy() }}
 */
export async function createPickerMap(container, { lng, lat, onPicked, searchInput } = {}) {
  const AMap = await loadAmap()
  const map = new AMap.Map(container, { zoom: 5, center: [105, 35] })

  let marker = null
  let geocoder = null

  const emitPicked = (x, y, address) => {
    onPicked?.({ lng: Number(x), lat: Number(y), address: address || '' })
  }

  const reverseGeocode = (x, y) => {
    if (!geocoder) {
      emitPicked(x, y, '')
      return
    }
    geocoder.getAddress([Number(x), Number(y)], (status, result) => {
      if (status === 'complete' && result.regeocode) {
        emitPicked(x, y, result.regeocode.formattedAddress)
      } else {
        emitPicked(x, y, '')
      }
    })
  }

  const setMarker = (x, y, address) => {
    const position = [Number(x), Number(y)]
    if (!marker) {
      marker = new AMap.Marker({ position, map, draggable: true })
      marker.on('dragend', () => {
        const pos = marker.getPosition()
        reverseGeocode(pos.lng, pos.lat)
      })
    } else {
      marker.setPosition(position)
    }
    if (address != null && address !== undefined) {
      emitPicked(x, y, address)
    }
  }

  await new Promise((resolve) => {
    AMap.plugin(
      ['AMap.Geolocation', 'AMap.Geocoder', 'AMap.AutoComplete', 'AMap.PlaceSearch'],
      () => resolve()
    )
  })

  geocoder = new AMap.Geocoder()

  const hasInitial = lng != null && lat != null && lng !== '' && lat !== ''
  if (hasInitial) {
    map.setZoomAndCenter(13, [Number(lng), Number(lat)])
    setMarker(lng, lat)
  } else {
    const geolocation = new AMap.Geolocation({
      enableHighAccuracy: true,
      timeout: 10000
    })
    geolocation.getCurrentPosition((status, result) => {
      if (status === 'complete' && result.position) {
        const { lng: x, lat: y } = result.position
        map.setZoomAndCenter(15, [x, y])
        // 仅定位视图，不自动 onPicked
      } else {
        map.setZoomAndCenter(5, [105, 35])
      }
    })
  }

  map.on('click', (e) => {
    const { lng: x, lat: y } = e.lnglat
    setMarker(x, y)
    reverseGeocode(x, y)
  })

  if (searchInput) {
    const inputEl =
      typeof searchInput === 'string' ? document.querySelector(searchInput) : searchInput
    if (inputEl) {
      const autoComplete = new AMap.AutoComplete({ input: inputEl, city: '全国' })
      autoComplete.on('select', (e) => {
        const poi = e.poi
        if (!poi?.location) return
        const x = poi.location.lng
        const y = poi.location.lat
        const name = poi.name || poi.address || ''
        map.setZoomAndCenter(15, [x, y])
        setMarker(x, y, name)
      })
    }
  }

  return {
    map,
    setMarker(x, y, address) {
      map.setZoomAndCenter(13, [Number(x), Number(y)])
      setMarker(x, y, address)
      if (address == null) reverseGeocode(x, y)
    },
    destroy() {
      if (map && typeof map.destroy === 'function') map.destroy()
    }
  }
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
