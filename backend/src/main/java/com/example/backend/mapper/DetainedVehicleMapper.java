package com.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DetainedVehicleMapper {

    @Select("SELECT COUNT(*) FROM detained_vehicle WHERE parking_lot_id = #{parkingLotId} AND status = 'IN_YARD'")
    int countInYardByParkingLotId(Long parkingLotId);
}
