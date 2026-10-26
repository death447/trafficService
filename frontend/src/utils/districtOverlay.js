import { listDistricts } from '../api/district'

function parseFenceJson(raw) {
  if (!raw) return []
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!Array.isArray(parsed)) return []
    return parsed
      .map((p) => ({ lng: Number(p.lng), lat: Number(p.lat) }))
      .filter((p) => Number.isFinite(p.lng) && Number.isFinite(p.lat))
  } catch {
    return []
  }
}

/** 拉取启用片区围栏，供地图参考叠加（失败返回 []） */
export async function loadEnabledDistrictOverlayItems() {
  try {
    const res = await listDistricts({ status: 'ENABLED', page: 1, size: 100 })
    const list = res.data?.list || []
    return list
      .map((d) => ({
        id: d.id,
        name: d.name || d.code || String(d.id),
        path: parseFenceJson(d.fenceJson)
      }))
      .filter((d) => d.path.length >= 3)
  } catch {
    return []
  }
}
