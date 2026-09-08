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

    @Select("<script>" +
            "SELECT COUNT(*) FROM dispatch_order WHERE 1=1" +
            "<if test='orderNo != null and orderNo != \"\"'> AND order_no LIKE CONCAT('%', #{orderNo}, '%')</if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "<if test='address != null and address != \"\"'> AND accident_address LIKE CONCAT('%', #{address}, '%')</if>" +
            "<if test='dispatcherId != null'> AND dispatcher_id = #{dispatcherId}</if>" +
            "</script>")
    long count(@Param("orderNo") String orderNo,
               @Param("status") String status,
               @Param("address") String address,
               @Param("dispatcherId") Long dispatcherId);

    @Select("<script>" +
            "SELECT * FROM dispatch_order WHERE 1=1" +
            "<if test='orderNo != null and orderNo != \"\"'> AND order_no LIKE CONCAT('%', #{orderNo}, '%')</if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "<if test='address != null and address != \"\"'> AND accident_address LIKE CONCAT('%', #{address}, '%')</if>" +
            "<if test='dispatcherId != null'> AND dispatcher_id = #{dispatcherId}</if>" +
            " ORDER BY id DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<DispatchOrder> selectPage(@Param("orderNo") String orderNo,
                                   @Param("status") String status,
                                   @Param("address") String address,
                                   @Param("dispatcherId") Long dispatcherId,
                                   @Param("offset") int offset,
                                   @Param("size") int size);

    @Insert("INSERT INTO dispatch_order (order_no, accident_address, longitude, latitude, rescue_reason, plate_no, vehicle_type_id, vehicle_type_name, status, " +
            "dispatcher_id, vehicle_id, rescuer_id, abort_reason, dispatched_at, completed_at, accepted_at, " +
            "checked_in_at, checkin_lng, checkin_lat, checkin_mode, checkin_remark, reject_reason) " +
            "VALUES (#{orderNo}, #{accidentAddress}, #{longitude}, #{latitude}, #{rescueReason}, #{plateNo}, #{vehicleTypeId}, #{vehicleTypeName}, #{status}, " +
            "#{dispatcherId}, #{vehicleId}, #{rescuerId}, #{abortReason}, #{dispatchedAt}, #{completedAt}, " +
            "#{acceptedAt}, #{checkedInAt}, #{checkinLng}, #{checkinLat}, #{checkinMode}, #{checkinRemark}, #{rejectReason})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DispatchOrder order);

    @Update("UPDATE dispatch_order SET order_no = #{orderNo}, accident_address = #{accidentAddress}, " +
            "longitude = #{longitude}, latitude = #{latitude}, rescue_reason = #{rescueReason}, " +
            "plate_no = #{plateNo}, vehicle_type_id = #{vehicleTypeId}, vehicle_type_name = #{vehicleTypeName}, " +
            "status = #{status}, " +
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
