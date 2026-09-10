package com.example.backend.mapper;

import com.example.backend.entity.DetainedVehicle;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DetainedVehicleMapper {

    @Select("SELECT COUNT(*) FROM detained_vehicle WHERE parking_lot_id = #{parkingLotId} AND status = 'IN_YARD'")
    int countInYardByParkingLotId(Long parkingLotId);

    @Select("SELECT * FROM detained_vehicle")
    List<DetainedVehicle> findAll();

    @Select("<script>" +
            "SELECT COUNT(*) FROM detained_vehicle WHERE 1=1" +
            "<if test='plateNo != null and plateNo != \"\"'> AND plate_no LIKE CONCAT('%', #{plateNo}, '%')</if>" +
            "<if test='detainNo != null and detainNo != \"\"'> AND detain_no LIKE CONCAT('%', #{detainNo}, '%')</if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "<if test='parkingLotId != null'> AND parking_lot_id = #{parkingLotId}</if>" +
            "<if test='detainDept != null and detainDept != \"\"'> AND detain_dept LIKE CONCAT('%', #{detainDept}, '%')</if>" +
            "</script>")
    long count(@Param("plateNo") String plateNo,
               @Param("detainNo") String detainNo,
               @Param("status") String status,
               @Param("parkingLotId") Long parkingLotId,
               @Param("detainDept") String detainDept);

    @Select("<script>" +
            "SELECT * FROM detained_vehicle WHERE 1=1" +
            "<if test='plateNo != null and plateNo != \"\"'> AND plate_no LIKE CONCAT('%', #{plateNo}, '%')</if>" +
            "<if test='detainNo != null and detainNo != \"\"'> AND detain_no LIKE CONCAT('%', #{detainNo}, '%')</if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "<if test='parkingLotId != null'> AND parking_lot_id = #{parkingLotId}</if>" +
            "<if test='detainDept != null and detainDept != \"\"'> AND detain_dept LIKE CONCAT('%', #{detainDept}, '%')</if>" +
            " ORDER BY id DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<DetainedVehicle> selectPage(@Param("plateNo") String plateNo,
                                     @Param("detainNo") String detainNo,
                                     @Param("status") String status,
                                     @Param("parkingLotId") Long parkingLotId,
                                     @Param("detainDept") String detainDept,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    @Select("SELECT * FROM detained_vehicle WHERE id = #{id}")
    DetainedVehicle findById(Long id);

    @Select("SELECT COUNT(*) FROM detained_vehicle WHERE plate_no = #{plateNo} AND status = 'IN_YARD'")
    int countInYardByPlateNo(String plateNo);

    @Select("SELECT COUNT(*) FROM detained_vehicle WHERE plate_no = #{plateNo} AND status = 'IN_YARD' AND id <> #{excludeId}")
    int countInYardByPlateNoExcludingId(@Param("plateNo") String plateNo, @Param("excludeId") Long excludeId);

    @Select("SELECT COUNT(*) FROM detained_vehicle WHERE parking_area_id = #{parkingAreaId}")
    int countByParkingAreaId(Long parkingAreaId);

    @Select("SELECT * FROM detained_vehicle WHERE detain_no = #{detainNo}")
    DetainedVehicle findByDetainNo(String detainNo);

    @Select("SELECT COUNT(*) FROM detained_vehicle WHERE entry_no LIKE CONCAT(#{prefix}, '%')")
    int countByEntryNoPrefix(String prefix);

    @Insert("INSERT INTO detained_vehicle (detain_no, entry_no, plate_no, vehicle_type, brand_model, vehicle_color, " +
            "mileage, important_equipment, has_key, parking_lot_id, parking_area_id, stall_no, dispatch_order_id, " +
            "detain_dept, rescuer_name, rescue_reason, rescue_method, rescue_time, rescue_address, status, in_time, " +
            "operator_in_id, remark) VALUES (#{detainNo}, #{entryNo}, #{plateNo}, #{vehicleType}, #{brandModel}, " +
            "#{vehicleColor}, #{mileage}, #{importantEquipment}, #{hasKey}, #{parkingLotId}, #{parkingAreaId}, " +
            "#{stallNo}, #{dispatchOrderId}, #{detainDept}, #{rescuerName}, #{rescueReason}, #{rescueMethod}, " +
            "#{rescueTime}, #{rescueAddress}, #{status}, #{inTime}, #{operatorInId}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DetainedVehicle v);

    @Update("UPDATE detained_vehicle SET plate_no=#{plateNo}, vehicle_type=#{vehicleType}, brand_model=#{brandModel}, " +
            "vehicle_color=#{vehicleColor}, mileage=#{mileage}, important_equipment=#{importantEquipment}, " +
            "has_key=#{hasKey}, parking_lot_id=#{parkingLotId}, parking_area_id=#{parkingAreaId}, stall_no=#{stallNo}, " +
            "dispatch_order_id=#{dispatchOrderId}, detain_dept=#{detainDept}, rescuer_name=#{rescuerName}, " +
            "rescue_reason=#{rescueReason}, rescue_method=#{rescueMethod}, rescue_time=#{rescueTime}, " +
            "rescue_address=#{rescueAddress}, status=#{status}, in_time=#{inTime}, out_time=#{outTime}, " +
            "cleared_at=#{clearedAt}, operator_in_id=#{operatorInId}, operator_out_id=#{operatorOutId}, " +
            "remark=#{remark} WHERE id=#{id}")
    int update(DetainedVehicle v);
}
