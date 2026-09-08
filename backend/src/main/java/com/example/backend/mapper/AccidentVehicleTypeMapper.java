package com.example.backend.mapper;

import com.example.backend.entity.AccidentVehicleType;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AccidentVehicleTypeMapper {

    @Select("SELECT * FROM accident_vehicle_type")
    List<AccidentVehicleType> findAll();

    @Select("SELECT * FROM accident_vehicle_type WHERE id = #{id}")
    AccidentVehicleType findById(Long id);

    @Select("SELECT * FROM accident_vehicle_type WHERE name = #{name}")
    AccidentVehicleType findByName(String name);

    @Select("<script>" +
            "SELECT COUNT(*) FROM accident_vehicle_type WHERE 1=1" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "</script>")
    long count(@Param("status") String status);

    @Select("<script>" +
            "SELECT * FROM accident_vehicle_type WHERE 1=1" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            " ORDER BY sort_order ASC, id ASC LIMIT #{offset}, #{size}" +
            "</script>")
    List<AccidentVehicleType> selectPage(@Param("status") String status,
                                         @Param("offset") int offset,
                                         @Param("size") int size);

    @Insert("INSERT INTO accident_vehicle_type (name, sort_order, status, remark) " +
            "VALUES (#{name}, #{sortOrder}, #{status}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AccidentVehicleType row);

    @Update("UPDATE accident_vehicle_type SET name=#{name}, sort_order=#{sortOrder}, " +
            "status=#{status}, remark=#{remark} WHERE id=#{id}")
    int update(AccidentVehicleType row);
}
