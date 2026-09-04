package com.example.backend.mapper;

import com.example.backend.entity.District;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DistrictMapper {

    @Select("SELECT * FROM district")
    List<District> findAll();

    @Select("SELECT * FROM district WHERE id = #{id}")
    District findById(Long id);

    @Select("SELECT * FROM district WHERE code = #{code}")
    District findByCode(String code);

    @Select("SELECT * FROM district WHERE status = #{status}")
    List<District> findByStatus(String status);

    @Insert("INSERT INTO district (name, code, fence_json, status, remark) " +
            "VALUES (#{name}, #{code}, #{fenceJson}, #{status}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(District district);

    @Update("UPDATE district SET name = #{name}, code = #{code}, fence_json = #{fenceJson}, " +
            "status = #{status}, remark = #{remark} WHERE id = #{id}")
    int update(District district);

    @Delete("DELETE FROM district WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT COUNT(*) FROM rescue_vehicle WHERE district_id = #{districtId}")
    int countVehiclesByDistrictId(Long districtId);

    @Select("SELECT COUNT(*) FROM duty_schedule WHERE district_id = #{districtId}")
    int countSchedulesByDistrictId(Long districtId);
}
