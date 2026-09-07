package com.example.backend.mapper;

import com.example.backend.entity.ParkingLot;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ParkingLotMapper {

    @Select("SELECT * FROM parking_lot")
    List<ParkingLot> findAll();

    @Select("SELECT * FROM parking_lot WHERE id = #{id}")
    ParkingLot findById(Long id);

    @Select("SELECT * FROM parking_lot WHERE code = #{code}")
    ParkingLot findByCode(String code);

    @Insert("INSERT INTO parking_lot (name, code, address, contact_name, contact_phone, status, remark) " +
            "VALUES (#{name}, #{code}, #{address}, #{contactName}, #{contactPhone}, #{status}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ParkingLot lot);

    @Update("UPDATE parking_lot SET name=#{name}, code=#{code}, address=#{address}, " +
            "contact_name=#{contactName}, contact_phone=#{contactPhone}, status=#{status}, remark=#{remark} WHERE id=#{id}")
    int update(ParkingLot lot);

    @Delete("DELETE FROM parking_lot WHERE id = #{id}")
    int deleteById(Long id);
}
