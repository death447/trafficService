package com.example.backend.mapper;

import com.example.backend.entity.DispatchMedia;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DispatchMediaMapper {

    @Insert("INSERT INTO dispatch_media (dispatch_order_id, biz_type, file_path, sort_order, uploaded_by) " +
            "VALUES (#{dispatchOrderId}, #{bizType}, #{filePath}, #{sortOrder}, #{uploadedBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DispatchMedia media);

    @Select("SELECT * FROM dispatch_media WHERE dispatch_order_id = #{dispatchOrderId} ORDER BY sort_order, id")
    List<DispatchMedia> findByOrderId(Long dispatchOrderId);

    @Select("SELECT * FROM dispatch_media WHERE dispatch_order_id = #{dispatchOrderId} AND biz_type = #{bizType} ORDER BY sort_order, id")
    List<DispatchMedia> findByOrderIdAndBizType(@Param("dispatchOrderId") Long dispatchOrderId,
                                                 @Param("bizType") String bizType);
}
