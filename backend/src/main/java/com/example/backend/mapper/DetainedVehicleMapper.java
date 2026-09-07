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

    @Select("SELECT * FROM detained_vehicle WHERE id = #{id}")
    DetainedVehicle findById(Long id);

    @Select("SELECT COUNT(*) FROM detained_vehicle WHERE plate_no = #{plateNo} AND status = 'IN_YARD'")
    int countInYardByPlateNo(String plateNo);

    @Select("SELECT COUNT(*) FROM detained_vehicle WHERE plate_no = #{plateNo} AND status = 'IN_YARD' AND id <> #{excludeId}")
    int countInYardByPlateNoExcludingId(@Param("plateNo") String plateNo, @Param("excludeId") Long excludeId);

    @Select("SELECT COUNT(*) FROM detained_vehicle WHERE detain_no LIKE CONCAT(#{prefix}, '%')")
    int countByDetainNoPrefix(String prefix);

    @Insert("INSERT INTO detained_vehicle (detain_no, plate_no, vehicle_type, parking_lot_id, dispatch_order_id, " +
            "detain_dept, status, in_time, operator_in_id, remark) VALUES (#{detainNo}, #{plateNo}, #{vehicleType}, " +
            "#{parkingLotId}, #{dispatchOrderId}, #{detainDept}, #{status}, #{inTime}, #{operatorInId}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DetainedVehicle v);

    @Update("UPDATE detained_vehicle SET plate_no=#{plateNo}, vehicle_type=#{vehicleType}, parking_lot_id=#{parkingLotId}, " +
            "dispatch_order_id=#{dispatchOrderId}, detain_dept=#{detainDept}, status=#{status}, in_time=#{inTime}, " +
            "out_time=#{outTime}, cleared_at=#{clearedAt}, operator_in_id=#{operatorInId}, operator_out_id=#{operatorOutId}, " +
            "remark=#{remark} WHERE id=#{id}")
    int update(DetainedVehicle v);
}
