package com.example.backend.mapper;

import com.example.backend.entity.DetainMedia;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DetainMediaMapper {

    @Insert("INSERT INTO detain_media (detain_id, biz_type, file_path, sort_order, uploaded_by) " +
            "VALUES (#{detainId}, #{bizType}, #{filePath}, #{sortOrder}, #{uploadedBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DetainMedia media);

    @Select("SELECT * FROM detain_media WHERE detain_id = #{detainId} ORDER BY sort_order, id")
    List<DetainMedia> findByDetainId(Long detainId);

    @Select("SELECT * FROM detain_media WHERE id = #{id}")
    DetainMedia findById(Long id);

    @Delete("DELETE FROM detain_media WHERE id = #{id}")
    int deleteById(Long id);
}
