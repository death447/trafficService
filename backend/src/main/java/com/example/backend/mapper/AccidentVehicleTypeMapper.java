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

    @Insert("INSERT INTO accident_vehicle_type (name, sort_order, status, remark) " +
            "VALUES (#{name}, #{sortOrder}, #{status}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AccidentVehicleType row);

    @Update("UPDATE accident_vehicle_type SET name=#{name}, sort_order=#{sortOrder}, " +
            "status=#{status}, remark=#{remark} WHERE id=#{id}")
    int update(AccidentVehicleType row);
}
