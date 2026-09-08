package com.example.backend.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PageParamsTest {

    @Test
    void normalizeDefaultsAndAllowedSizes() {
        PageParams d = PageParams.normalize(1, 10);
        assertEquals(1, d.getPage());
        assertEquals(10, d.getSize());
        assertEquals(0, d.getOffset());

        assertEquals(20, PageParams.normalize(1, 20).getSize());
        assertEquals(50, PageParams.normalize(1, 50).getSize());
        assertEquals(100, PageParams.normalize(1, 100).getSize());
    }

    @Test
    void normalizeClampsPageBelowOne() {
        PageParams p = PageParams.normalize(0, 20);
        assertEquals(1, p.getPage());
        assertEquals(20, p.getSize());
        assertEquals(0, p.getOffset());

        assertEquals(1, PageParams.normalize(-5, 10).getPage());
    }

    @Test
    void normalizeFallsBackInvalidSize() {
        assertEquals(10, PageParams.normalize(1, 15).getSize());
        assertEquals(10, PageParams.normalize(1, 500).getSize());
        assertEquals(10, PageParams.normalize(1, 0).getSize());
        assertEquals(10, PageParams.normalize(1, -1).getSize());
    }

    @Test
    void normalizeComputesOffset() {
        assertEquals(0, PageParams.normalize(1, 10).getOffset());
        assertEquals(10, PageParams.normalize(2, 10).getOffset());
        assertEquals(40, PageParams.normalize(3, 20).getOffset());
        assertEquals(200, PageParams.normalize(3, 100).getOffset());
    }

    @Test
    void toResultUsesNormalizedPageSize() {
        PageParams pp = PageParams.normalize(2, 50);
        Map<String, Object> result = pp.toResult(List.of("a"), 99L);
        assertEquals(List.of("a"), result.get("list"));
        assertEquals(99L, result.get("total"));
        assertEquals(2, result.get("page"));
        assertEquals(50, result.get("size"));
    }
}
