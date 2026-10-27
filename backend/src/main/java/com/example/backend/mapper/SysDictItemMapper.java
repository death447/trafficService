package com.example.backend.mapper;

import com.example.backend.entity.SysDictItem;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SysDictItemMapper {

    @Select("SELECT * FROM sys_dict_item WHERE dict_type = #{dictType}")
    List<SysDictItem> findByType(String dictType);

    @Select("SELECT * FROM sys_dict_item WHERE id = #{id}")
    SysDictItem findById(Long id);

    @Select("SELECT * FROM sys_dict_item WHERE dict_type = #{dictType} AND name = #{name}")
    SysDictItem findByTypeAndName(@Param("dictType") String dictType, @Param("name") String name);

    @Select("<script>" +
            "SELECT COUNT(*) FROM sys_dict_item WHERE dict_type = #{dictType}" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "</script>")
    long count(@Param("dictType") String dictType, @Param("status") String status);

    @Select("<script>" +
            "SELECT * FROM sys_dict_item WHERE dict_type = #{dictType}" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            " ORDER BY sort_order ASC, id ASC LIMIT #{offset}, #{size}" +
            "</script>")
    List<SysDictItem> selectPage(@Param("dictType") String dictType,
                                 @Param("status") String status,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    @Insert("INSERT INTO sys_dict_item (dict_type, name, sort_order, status, remark) " +
            "VALUES (#{dictType}, #{name}, #{sortOrder}, #{status}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysDictItem row);

    @Update("UPDATE sys_dict_item SET name=#{name}, sort_order=#{sortOrder}, " +
            "status=#{status}, remark=#{remark} WHERE id=#{id}")
    int update(SysDictItem row);

    @Delete("DELETE FROM sys_dict_item WHERE id = #{id}")
    int deleteById(Long id);
}
