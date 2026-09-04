package com.example.backend.util;

import com.example.backend.dto.LngLat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public final class GeoUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GeoUtils() {
    }

    public static List<LngLat> parseFence(String fenceJson) {
        try {
            JsonNode node = MAPPER.readTree(fenceJson);
            if (node == null || !node.isArray()) {
                throw new RuntimeException("围栏格式无效");
            }
            return MAPPER.convertValue(node, new TypeReference<List<LngLat>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("围栏格式无效");
        } catch (RuntimeException e) {
            if ("围栏格式无效".equals(e.getMessage())) {
                throw e;
            }
            throw new RuntimeException("围栏格式无效");
        }
    }

    public static List<LngLat> normalizeFence(List<LngLat> points) {
        if (points == null || points.size() < 3) {
            throw new RuntimeException("围栏至少需要3个顶点");
        }
        List<LngLat> normalized = new ArrayList<>(points);
        if (normalized.size() > 1) {
            LngLat first = normalized.get(0);
            LngLat last = normalized.get(normalized.size() - 1);
            if (first.getLng() == last.getLng() && first.getLat() == last.getLat()) {
                normalized.remove(normalized.size() - 1);
            }
        }
        if (normalized.size() < 3) {
            throw new RuntimeException("围栏至少需要3个顶点");
        }
        return normalized;
    }

    public static boolean contains(List<LngLat> polygon, double lng, double lat) {
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            LngLat a = polygon.get(i);
            LngLat b = polygon.get((i + 1) % n);
            if (onSegment(a, b, lng, lat)) {
                return true;
            }
        }

        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = polygon.get(i).getLng();
            double yi = polygon.get(i).getLat();
            double xj = polygon.get(j).getLng();
            double yj = polygon.get(j).getLat();

            if (((yi > lat) != (yj > lat))
                    && (lng < (xj - xi) * (lat - yi) / (yj - yi) + xi)) {
                inside = !inside;
            }
        }
        return inside;
    }

    public static String toFenceJson(List<LngLat> points) {
        try {
            return MAPPER.writeValueAsString(points);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("围栏格式无效");
        }
    }

    private static boolean onSegment(LngLat a, LngLat b, double lng, double lat) {
        double cross = (lng - a.getLng()) * (b.getLat() - a.getLat())
                - (lat - a.getLat()) * (b.getLng() - a.getLng());
        if (Math.abs(cross) > 1e-10) {
            return false;
        }
        double dot = (lng - a.getLng()) * (lng - b.getLng())
                + (lat - a.getLat()) * (lat - b.getLat());
        return dot <= 1e-10;
    }
}
