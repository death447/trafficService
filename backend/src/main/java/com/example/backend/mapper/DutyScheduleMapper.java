package com.example.backend.mapper;

import com.example.backend.entity.DutySchedule;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DutyScheduleMapper {

    @Select("<script>" +
            "SELECT * FROM duty_schedule WHERE 1=1" +
            "<if test='from != null'> AND duty_date &gt;= #{from}</if>" +
            "<if test='to != null'> AND duty_date &lt;= #{to}</if>" +
            "<if test='roleType != null and roleType != \"\"'> AND role_type = #{roleType}</if>" +
            "<if test='districtId != null'> AND district_id = #{districtId}</if>" +
            "<if test='userId != null'> AND user_id = #{userId}</if>" +
            " ORDER BY start_time" +
            "</script>")
    List<DutySchedule> findList(@Param("from") LocalDate from,
                                @Param("to") LocalDate to,
                                @Param("roleType") String roleType,
                                @Param("districtId") Long districtId,
                                @Param("userId") Long userId);

    @Select("SELECT * FROM duty_schedule WHERE id = #{id}")
    DutySchedule findById(Long id);

    @Insert("INSERT INTO duty_schedule (duty_date, start_time, end_time, user_id, role_type, district_id, vehicle_id, remark) " +
            "VALUES (#{dutyDate}, #{startTime}, #{endTime}, #{userId}, #{roleType}, #{districtId}, #{vehicleId}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DutySchedule schedule);

    @Update("UPDATE duty_schedule SET duty_date = #{dutyDate}, start_time = #{startTime}, end_time = #{endTime}, " +
            "user_id = #{userId}, role_type = #{roleType}, district_id = #{districtId}, vehicle_id = #{vehicleId}, " +
            "remark = #{remark} WHERE id = #{id}")
    int update(DutySchedule schedule);

    @Delete("DELETE FROM duty_schedule WHERE id = #{id}")
    int deleteById(Long id);

    @Select("<script>" +
            "SELECT * FROM duty_schedule WHERE user_id = #{userId} " +
            "AND start_time &lt; #{end} AND end_time &gt; #{start} " +
            "<if test='excludeId != null'> AND id &lt;&gt; #{excludeId}</if>" +
            "</script>")
    List<DutySchedule> findOverlappingByUser(@Param("userId") Long userId,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end,
                                             @Param("excludeId") Long excludeId);

    @Select("<script>" +
            "SELECT * FROM duty_schedule WHERE vehicle_id = #{vehicleId} " +
            "AND start_time &lt; #{end} AND end_time &gt; #{start} " +
            "<if test='excludeId != null'> AND id &lt;&gt; #{excludeId}</if>" +
            "</script>")
    List<DutySchedule> findOverlappingByVehicle(@Param("vehicleId") Long vehicleId,
                                                @Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end,
                                                @Param("excludeId") Long excludeId);
}
