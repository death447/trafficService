package com.example.backend.mapper;

import com.example.backend.entity.ParkingArea;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ParkingAreaMapper {

    @Select("<script>" +
            "SELECT * FROM parking_area WHERE parking_lot_id = #{parkingLotId}" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            " ORDER BY id ASC" +
            "</script>")
    List<ParkingArea> findByParkingLotId(@Param("parkingLotId") Long parkingLotId,
                                         @Param("status") String status);

    @Select("SELECT * FROM parking_area WHERE id = #{id}")
    ParkingArea findById(Long id);

    @Select("SELECT * FROM parking_area WHERE parking_lot_id = #{parkingLotId} AND name = #{name}")
    ParkingArea findByLotIdAndName(@Param("parkingLotId") Long parkingLotId, @Param("name") String name);

    @Insert("INSERT INTO parking_area (parking_lot_id, name, status) VALUES (#{parkingLotId}, #{name}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ParkingArea area);

    @Update("UPDATE parking_area SET name=#{name}, status=#{status} WHERE id=#{id}")
    int update(ParkingArea area);

    @Delete("DELETE FROM parking_area WHERE id = #{id}")
    int deleteById(Long id);
}
