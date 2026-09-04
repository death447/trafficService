package com.example.backend.dto;

import com.example.backend.entity.District;
import lombok.Data;

@Data
public class MatchedDistrictVO {
    private Long id;
    private String name;
    private String code;

    public static MatchedDistrictVO from(District d) {
        if (d == null) return null;
        MatchedDistrictVO vo = new MatchedDistrictVO();
        vo.setId(d.getId());
        vo.setName(d.getName());
        vo.setCode(d.getCode());
        return vo;
    }
}
