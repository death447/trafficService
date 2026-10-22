package com.example.backend.mapper;

import com.example.backend.entity.DispatchFieldRecord;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DispatchFieldRecordMapper {

    @Select("SELECT * FROM dispatch_field_record WHERE dispatch_order_id = #{dispatchOrderId} LIMIT 1")
    DispatchFieldRecord findByOrderId(Long dispatchOrderId);

    @Select("SELECT * FROM dispatch_field_record WHERE assignment_id = #{assignmentId} LIMIT 1")
    DispatchFieldRecord findByAssignmentId(Long assignmentId);

    @Insert("INSERT INTO dispatch_field_record (dispatch_order_id, assignment_id, plate_no, vehicle_type, damage_desc, scene_remark, " +
            "park_address, park_remark, scene_submitted_at, park_submitted_at) VALUES (#{dispatchOrderId}, #{assignmentId}, #{plateNo}, " +
            "#{vehicleType}, #{damageDesc}, #{sceneRemark}, #{parkAddress}, #{parkRemark}, #{sceneSubmittedAt}, #{parkSubmittedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DispatchFieldRecord record);

    @Update("UPDATE dispatch_field_record SET assignment_id=#{assignmentId}, plate_no=#{plateNo}, vehicle_type=#{vehicleType}, damage_desc=#{damageDesc}, " +
            "scene_remark=#{sceneRemark}, park_address=#{parkAddress}, park_remark=#{parkRemark}, " +
            "scene_submitted_at=#{sceneSubmittedAt}, park_submitted_at=#{parkSubmittedAt} WHERE id=#{id}")
    int update(DispatchFieldRecord record);
}
