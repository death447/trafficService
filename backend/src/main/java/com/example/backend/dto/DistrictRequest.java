package com.example.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class DistrictRequest {
    private String name;
    private String code;
    /** Raw fence JSON string; used when {@link #fence} is absent. */
    private String fenceJson;
    /** Polygon vertices; preferred over fenceJson when present. */
    private List<LngLat> fence;
    private String status;
    private String remark;
}
