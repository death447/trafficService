import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  LOCATION_STALE_SECONDS,
  AUTO_CHECKIN_RADIUS_METERS,
  distanceMeters,
  isLocationFresh,
  canAutoCheckin
} from './geo.js'

describe('geo', () => {
  it('exports stale and radius constants', () => {
    assert.equal(LOCATION_STALE_SECONDS, 180)
    assert.equal(AUTO_CHECKIN_RADIUS_METERS, 500)
  })

  it('distanceMeters same point near zero', () => {
    const d = distanceMeters(114.05, 22.54, 114.05, 22.54)
    assert.ok(d < 1)
  })

  it('distanceMeters known ~500m separation', () => {
    // ~0.0045 deg lat ≈ 500m
    const d = distanceMeters(114.05, 22.54, 114.05, 22.54 + 0.0045)
    assert.ok(d > 450 && d < 550)
  })

  it('distanceMeters invalid returns NaN', () => {
    assert.ok(Number.isNaN(distanceMeters(null, 22, 114, 22)))
  })

  it('isLocationFresh within 180s', () => {
    const now = Date.parse('2026-09-09T12:00:00Z')
    assert.equal(isLocationFresh('2026-09-09T11:57:01Z', now), true)
    assert.equal(isLocationFresh('2026-09-09T11:56:59Z', now), false)
    assert.equal(isLocationFresh(null, now), false)
  })

  it('canAutoCheckin true when accepted fresh within 500m', () => {
    const now = Date.parse('2026-09-09T12:00:00Z')
    const order = {
      status: 'ACCEPTED',
      checkedInAt: null,
      longitude: 114.05,
      latitude: 22.54
    }
    const vehicle = {
      longitude: 114.05,
      latitude: 22.54,
      locationUpdatedAt: '2026-09-09T11:59:00Z'
    }
    assert.equal(canAutoCheckin(order, vehicle, now), true)
  })

  it('canAutoCheckin false when stale or too far or wrong status', () => {
    const now = Date.parse('2026-09-09T12:00:00Z')
    const baseOrder = {
      status: 'ACCEPTED',
      checkedInAt: null,
      longitude: 114.05,
      latitude: 22.54
    }
    assert.equal(
      canAutoCheckin(baseOrder, {
        longitude: 114.05,
        latitude: 22.54,
        locationUpdatedAt: '2026-09-09T11:50:00Z'
      }, now),
      false
    )
    assert.equal(
      canAutoCheckin(baseOrder, {
        longitude: 114.05,
        latitude: 22.55,
        locationUpdatedAt: '2026-09-09T11:59:00Z'
      }, now),
      false
    )
    assert.equal(
      canAutoCheckin({ ...baseOrder, status: 'DISPATCHED' }, {
        longitude: 114.05,
        latitude: 22.54,
        locationUpdatedAt: '2026-09-09T11:59:00Z'
      }, now),
      false
    )
    assert.equal(
      canAutoCheckin({ ...baseOrder, checkedInAt: '2026-09-09T11:58:00Z' }, {
        longitude: 114.05,
        latitude: 22.54,
        locationUpdatedAt: '2026-09-09T11:59:00Z'
      }, now),
      false
    )
  })
})
