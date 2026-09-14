const DEFAULT_OFFSET = [-22, -18]
const DEFAULT_RADIUS = 28
const DEFAULT_PRECISION = 5

/**
 * Spread overlapping map markers so identical GPS points stay distinguishable.
 * @param {Array<{ id: number|string, longitude: number|string, latitude: number|string }>} items
 * @param {{ defaultOffset?: [number, number], radius?: number, precision?: number }} [options]
 * @returns {Map<number|string, [number, number]>}
 */
export function computeOverlappingMarkerOffsets(items, options = {}) {
  const defaultOffset = options.defaultOffset ?? DEFAULT_OFFSET
  const radius = options.radius ?? DEFAULT_RADIUS
  const precision = options.precision ?? DEFAULT_PRECISION
  const groups = new Map()

  for (const item of items || []) {
    const lng = Number(item.longitude)
    const lat = Number(item.latitude)
    const key = `${lng.toFixed(precision)},${lat.toFixed(precision)}`
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(item)
  }

  const result = new Map()
  for (const group of groups.values()) {
    if (group.length === 1) {
      result.set(group[0].id, [defaultOffset[0], defaultOffset[1]])
      continue
    }
    group.forEach((item, i) => {
      const angle = (2 * Math.PI * i) / group.length
      result.set(item.id, [
        defaultOffset[0] + Math.round(Math.cos(angle) * radius),
        defaultOffset[1] + Math.round(Math.sin(angle) * radius)
      ])
    })
  }
  return result
}
