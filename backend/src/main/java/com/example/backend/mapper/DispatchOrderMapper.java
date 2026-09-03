package com.example.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DispatchOrderMapper {

    @Select("SELECT COUNT(*) FROM dispatch_order WHERE vehicle_id = #{vehicleId} AND status IN ('PENDING','DISPATCHED')")
    int countActiveByVehicleId(Long vehicleId);
}
