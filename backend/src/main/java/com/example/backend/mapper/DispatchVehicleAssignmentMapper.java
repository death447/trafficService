package com.example.backend.mapper;

import com.example.backend.entity.DispatchVehicleAssignment;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DispatchVehicleAssignmentMapper {

    @Select("SELECT * FROM dispatch_vehicle_assignment WHERE id = #{id}")
    DispatchVehicleAssignment findById(Long id);

    @Select("SELECT * FROM dispatch_vehicle_assignment WHERE dispatch_order_id = #{orderId} ORDER BY id")
    List<DispatchVehicleAssignment> findByOrderId(Long orderId);

    @Select("SELECT * FROM dispatch_vehicle_assignment WHERE dispatch_order_id = #{orderId} AND rescuer_id = #{rescuerId} LIMIT 1")
    DispatchVehicleAssignment findByOrderIdAndRescuerId(@Param("orderId") Long orderId,
                                                        @Param("rescuerId") Long rescuerId);

    @Select("SELECT * FROM dispatch_vehicle_assignment WHERE vehicle_id = #{vehicleId} AND status IN ('DISPATCHED','ACCEPTED')")
    List<DispatchVehicleAssignment> findActiveByVehicleId(Long vehicleId);

    @Select("SELECT COUNT(1) FROM dispatch_vehicle_assignment WHERE vehicle_id = #{vehicleId} AND status IN ('DISPATCHED','ACCEPTED')")
    int countActiveByVehicleId(Long vehicleId);

    @Select("SELECT COUNT(1) FROM dispatch_vehicle_assignment WHERE dispatch_order_id = #{orderId} AND vehicle_id = #{vehicleId}")
    int countByOrderIdAndVehicleId(@Param("orderId") Long orderId, @Param("vehicleId") Long vehicleId);

    @Select("SELECT * FROM dispatch_vehicle_assignment WHERE rescuer_id = #{rescuerId} ORDER BY id DESC")
    List<DispatchVehicleAssignment> findByRescuerId(Long rescuerId);

    @Insert("INSERT INTO dispatch_vehicle_assignment (dispatch_order_id, vehicle_id, rescuer_id, status, " +
            "dispatched_at, accepted_at, checked_in_at, checkin_lng, checkin_lat, checkin_mode, checkin_remark, " +
            "reject_reason, completed_at) VALUES (#{dispatchOrderId}, #{vehicleId}, #{rescuerId}, #{status}, " +
            "#{dispatchedAt}, #{acceptedAt}, #{checkedInAt}, #{checkinLng}, #{checkinLat}, #{checkinMode}, " +
            "#{checkinRemark}, #{rejectReason}, #{completedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DispatchVehicleAssignment assignment);

    @Update("UPDATE dispatch_vehicle_assignment SET dispatch_order_id=#{dispatchOrderId}, vehicle_id=#{vehicleId}, " +
            "rescuer_id=#{rescuerId}, status=#{status}, dispatched_at=#{dispatchedAt}, accepted_at=#{acceptedAt}, " +
            "checked_in_at=#{checkedInAt}, checkin_lng=#{checkinLng}, checkin_lat=#{checkinLat}, " +
            "checkin_mode=#{checkinMode}, checkin_remark=#{checkinRemark}, reject_reason=#{rejectReason}, " +
            "completed_at=#{completedAt} WHERE id=#{id}")
    int update(DispatchVehicleAssignment assignment);
}
