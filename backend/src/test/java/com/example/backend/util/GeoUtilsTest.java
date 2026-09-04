package com.example.backend.util;

import com.example.backend.dto.LngLat;
import org.junit.jupiter.api.Test;

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
    void normalizeRejectsTwoPoints() {
        assertThrows(RuntimeException.class,
                () -> GeoUtils.normalizeFence(List.of(new LngLat(1, 1), new LngLat(2, 2))));
    }

    @Test
    void parseAndRoundTrip() {
        String json = GeoUtils.toFenceJson(SQUARE);
        List<LngLat> parsed = GeoUtils.normalizeFence(GeoUtils.parseFence(json));
        assertEquals(4, parsed.size());
    }
}
