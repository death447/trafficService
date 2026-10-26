package com.example.backend.mapper;

import com.example.backend.entity.DispatchAccidentVehicle;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DispatchAccidentVehicleMapper {

    @Select("SELECT * FROM dispatch_accident_vehicle WHERE dispatch_order_id = #{orderId} ORDER BY sort_order, id")
    List<DispatchAccidentVehicle> findByOrderId(Long orderId);

    @Delete("DELETE FROM dispatch_accident_vehicle WHERE dispatch_order_id = #{orderId}")
    int deleteByOrderId(Long orderId);

    @Insert("INSERT INTO dispatch_accident_vehicle (dispatch_order_id, sort_order, plate_no, vehicle_type_id, " +
            "vehicle_type_name, party_name, party_phone) VALUES (#{dispatchOrderId}, #{sortOrder}, #{plateNo}, " +
            "#{vehicleTypeId}, #{vehicleTypeName}, #{partyName}, #{partyPhone})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DispatchAccidentVehicle vehicle);

    @Select("SELECT a.* FROM dispatch_accident_vehicle a "
            + "INNER JOIN dispatch_order o ON o.id = a.dispatch_order_id "
            + "WHERE o.status IN ('PENDING','DISPATCHED','ACCEPTED')")
    List<DispatchAccidentVehicle> findAllOnActiveOrders();
}
