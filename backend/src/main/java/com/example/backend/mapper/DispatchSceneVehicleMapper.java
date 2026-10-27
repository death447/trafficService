package com.example.backend.mapper;

import com.example.backend.entity.DispatchSceneVehicle;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DispatchSceneVehicleMapper {

    @Select("SELECT * FROM dispatch_scene_vehicle WHERE dispatch_order_id = #{dispatchOrderId} ORDER BY sort_order, id")
    List<DispatchSceneVehicle> findByOrderId(Long dispatchOrderId);

    @Delete("DELETE FROM dispatch_scene_vehicle WHERE dispatch_order_id = #{dispatchOrderId}")
    int deleteByOrderId(Long dispatchOrderId);

    @Insert("INSERT INTO dispatch_scene_vehicle (dispatch_order_id, assignment_id, accident_vehicle_id, plate_no, " +
            "vehicle_type, damage_desc, sort_order) VALUES (#{dispatchOrderId}, #{assignmentId}, #{accidentVehicleId}, " +
            "#{plateNo}, #{vehicleType}, #{damageDesc}, #{sortOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DispatchSceneVehicle row);
}
