package com.example.backend.common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Normalizes list pagination: page from 1, size in {10,20,50,100}.
 */
public final class PageParams {

    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 20, 50, 100);

    private final int page;
    private final int size;
    private final int offset;

    private PageParams(int page, int size) {
        this.page = page;
        this.size = size;
        this.offset = (page - 1) * size;
    }

    public static PageParams normalize(int page, int size) {
        int p = page < 1 ? 1 : page;
        int s = ALLOWED_SIZES.contains(size) ? size : 10;
        return new PageParams(p, s);
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public int getOffset() {
        return offset;
    }

    public Map<String, Object> toResult(List<?> list, long total) {
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }
}
