package com.example.backend.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface UserRoleMapper {

    @Insert("INSERT INTO user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insert(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Delete("DELETE FROM user_role WHERE user_id = #{userId}")
    int deleteAllByUserId(Long userId);

    @Delete("DELETE FROM user_role WHERE user_id = #{userId} AND role_id = #{roleId}")
    int delete(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Select("SELECT role_id FROM user_role WHERE user_id = #{userId}")
    java.util.List<Long> findRoleIdsByUserId(Long userId);

    @Select("SELECT COUNT(*) FROM user_role WHERE role_id = #{roleId}")
    int countUsersByRoleId(Long roleId);
}