import test from 'node:test'
import assert from 'node:assert/strict'
import { computeOverlappingMarkerOffsets } from './markerOffset.js'

const DEFAULT = [-22, -18]

test('distinct coordinates keep the default pixel offset', () => {
  const offsets = computeOverlappingMarkerOffsets([
    { id: 1, longitude: 120.75, latitude: 30.74 },
    { id: 2, longitude: 120.76, latitude: 30.75 }
  ])
  assert.deepEqual(offsets.get(1), DEFAULT)
  assert.deepEqual(offsets.get(2), DEFAULT)
})

test('identical coordinates get distinct pixel offsets so both markers show', () => {
  const offsets = computeOverlappingMarkerOffsets([
    { id: 1, longitude: 120.7555, latitude: 30.7463 },
    { id: 3, longitude: 120.7555, latitude: 30.7463 }
  ])
  assert.equal(offsets.size, 2)
  assert.notDeepEqual(offsets.get(1), offsets.get(3))
  const dx = offsets.get(1)[0] - offsets.get(3)[0]
  const dy = offsets.get(1)[1] - offsets.get(3)[1]
  assert.ok(Math.hypot(dx, dy) >= 40, 'spread should separate two 44px truck icons')
})

test('separate overlap groups are offset independently', () => {
  const offsets = computeOverlappingMarkerOffsets([
    { id: 1, longitude: 120.1, latitude: 30.1 },
    { id: 2, longitude: 120.1, latitude: 30.1 },
    { id: 3, longitude: 120.9, latitude: 30.9 }
  ])
  assert.notDeepEqual(offsets.get(1), offsets.get(2))
  assert.deepEqual(offsets.get(3), DEFAULT)
})
