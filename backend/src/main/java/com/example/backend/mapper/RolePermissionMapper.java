package com.example.backend.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface RolePermissionMapper {

    @Insert("INSERT INTO role_permission (role_id, permission_id) VALUES (#{roleId}, #{permissionId})")
    int insert(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    @Delete("DELETE FROM role_permission WHERE role_id = #{roleId}")
    int deleteAllByRoleId(Long roleId);
}
