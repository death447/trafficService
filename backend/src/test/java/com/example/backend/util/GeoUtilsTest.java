package com.example.backend.util;

import com.example.backend.dto.LngLat;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeoUtilsTest {
    static final List<LngLat> SQUARE = List.of(
            new LngLat(114.04, 22.53), new LngLat(114.08, 22.53),
            new LngLat(114.08, 22.56), new LngLat(114.04, 22.56));

    @Test
    void containsInside() {
        assertTrue(GeoUtils.contains(SQUARE, 114.057868, 22.543099));
    }

    @Test
    void containsOutside() {
        assertFalse(GeoUtils.contains(SQUARE, 114.10, 22.60));
    }

    @Test
    void containsOnEdge() {
        // 边上视为内 — left edge of SQUARE at lng=114.04, lat=22.545
        assertTrue(GeoUtils.contains(SQUARE, 114.04, 22.545));
    }

    @Test
    void normalizeRejectsTwoPoints() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> GeoUtils.normalizeFence(List.of(new LngLat(1, 1), new LngLat(2, 2))));
        assertTrue(ex.getMessage().contains("围栏至少需要3个顶点"));
    }

    @Test
    void parseAndRoundTrip() {
        String json = GeoUtils.toFenceJson(SQUARE);
        List<LngLat> parsed = GeoUtils.normalizeFence(GeoUtils.parseFence(json));
        assertEquals(4, parsed.size());
    }

    @Test
    void parseRejectsInvalidJson() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> GeoUtils.parseFence("not json"));
        assertEquals("围栏格式无效", ex.getMessage());
    }

    @Test
    void parseRejectsMalformedElements() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> GeoUtils.parseFence("[\"bad\"]"));
        assertEquals("围栏格式无效", ex.getMessage());
    }

    @Test
    void distanceMetersSamePointIsNearZero() {
        BigDecimal lng = new BigDecimal("121.4737000");
        BigDecimal lat = new BigDecimal("31.2304000");
        double d = GeoUtils.distanceMeters(lng, lat, lng, lat);
        assertTrue(d < 1.0, "same point should be ~0m, got " + d);
    }

    @Test
    void distanceMetersKnownPointsAbout500m() {
        // ~0.0045 deg lat ≈ 500m
        BigDecimal lng1 = new BigDecimal("121.0000000");
        BigDecimal lat1 = new BigDecimal("31.0000000");
        BigDecimal lng2 = new BigDecimal("121.0000000");
        BigDecimal lat2 = new BigDecimal("31.0045000");
        double d = GeoUtils.distanceMeters(lng1, lat1, lng2, lat2);
        assertTrue(Math.abs(d - 500.0) <= 20.0, "expected ~500m ±20m, got " + d);
    }
}
