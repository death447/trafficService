package com.example.backend.mapper;

import com.example.backend.entity.RescueVehicle;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RescueVehicleMapper {

    @Select("SELECT * FROM rescue_vehicle WHERE id = #{id}")
    RescueVehicle findById(Long id);

    @Select("SELECT * FROM rescue_vehicle")
    List<RescueVehicle> findAll();

    @Select("SELECT * FROM rescue_vehicle WHERE status = #{status}")
    List<RescueVehicle> findByStatus(String status);

    @Select("SELECT * FROM rescue_vehicle WHERE plate_no = #{plateNo}")
    RescueVehicle findByPlateNo(String plateNo);

    @Insert("INSERT INTO rescue_vehicle (plate_no, vehicle_type, color, equipment, longitude, latitude, status, district_id, driver_user_id, remark) " +
            "VALUES (#{plateNo}, #{vehicleType}, #{color}, #{equipment}, #{longitude}, #{latitude}, #{status}, #{districtId}, #{driverUserId}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RescueVehicle vehicle);

    @Update("UPDATE rescue_vehicle SET plate_no = #{plateNo}, vehicle_type = #{vehicleType}, color = #{color}, equipment = #{equipment}, " +
            "longitude = #{longitude}, latitude = #{latitude}, status = #{status}, district_id = #{districtId}, " +
            "driver_user_id = #{driverUserId}, remark = #{remark} WHERE id = #{id}")
    int update(RescueVehicle vehicle);

    @Update("UPDATE rescue_vehicle SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Delete("DELETE FROM rescue_vehicle WHERE id = #{id}")
    int deleteById(Long id);
}
