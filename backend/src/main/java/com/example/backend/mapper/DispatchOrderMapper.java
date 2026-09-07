package com.example.backend.mapper;

import com.example.backend.entity.DispatchOrder;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DispatchOrderMapper {

    @Select("SELECT COUNT(*) FROM dispatch_order WHERE vehicle_id = #{vehicleId} AND status IN ('PENDING','DISPATCHED','ACCEPTED')")
    int countActiveByVehicleId(Long vehicleId);

    @Select("SELECT * FROM dispatch_order WHERE id = #{id}")
    DispatchOrder findById(Long id);

    @Select("SELECT * FROM dispatch_order")
    List<DispatchOrder> findAll();

    @Insert("INSERT INTO dispatch_order (order_no, accident_address, longitude, latitude, rescue_reason, status, " +
            "dispatcher_id, vehicle_id, rescuer_id, abort_reason, dispatched_at, completed_at, accepted_at, " +
            "checked_in_at, checkin_lng, checkin_lat, checkin_mode, checkin_remark, reject_reason) " +
            "VALUES (#{orderNo}, #{accidentAddress}, #{longitude}, #{latitude}, #{rescueReason}, #{status}, " +
            "#{dispatcherId}, #{vehicleId}, #{rescuerId}, #{abortReason}, #{dispatchedAt}, #{completedAt}, " +
            "#{acceptedAt}, #{checkedInAt}, #{checkinLng}, #{checkinLat}, #{checkinMode}, #{checkinRemark}, #{rejectReason})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DispatchOrder order);

    @Update("UPDATE dispatch_order SET order_no = #{orderNo}, accident_address = #{accidentAddress}, " +
            "longitude = #{longitude}, latitude = #{latitude}, rescue_reason = #{rescueReason}, status = #{status}, " +
            "dispatcher_id = #{dispatcherId}, vehicle_id = #{vehicleId}, rescuer_id = #{rescuerId}, " +
            "abort_reason = #{abortReason}, dispatched_at = #{dispatchedAt}, completed_at = #{completedAt}, " +
            "accepted_at = #{acceptedAt}, checked_in_at = #{checkedInAt}, checkin_lng = #{checkinLng}, " +
            "checkin_lat = #{checkinLat}, checkin_mode = #{checkinMode}, checkin_remark = #{checkinRemark}, " +
            "reject_reason = #{rejectReason} WHERE id = #{id}")
    int update(DispatchOrder order);

    @Select("SELECT COUNT(*) FROM dispatch_order WHERE order_no LIKE CONCAT(#{prefix}, '%')")
    int countByOrderNoPrefix(String prefix);

    @Select("SELECT COUNT(*) FROM dispatch_order WHERE vehicle_id = #{vehicleId} AND status IN ('DISPATCHED','ACCEPTED')")
    int countDispatchedByVehicleId(Long vehicleId);
}
