package com.example.backend.mapper;

import com.example.backend.entity.Role;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface RoleMapper {

    @Select("SELECT * FROM role WHERE id = #{id}")
    Role findById(Long id);

    @Select("SELECT * FROM role WHERE role_code = #{roleCode}")
    Role findByRoleCode(String roleCode);

    @Select("SELECT * FROM role")
    List<Role> findAll();

    @Select("SELECT * FROM role WHERE role_name LIKE CONCAT('%', #{keyword}, '%') OR role_code LIKE CONCAT('%', #{keyword}, '%')")
    List<Role> findByKeyword(String keyword);

    @Select("SELECT * FROM role WHERE status = #{status}")
    List<Role> findByStatus(Integer status);

    @Insert("INSERT INTO role (role_name, role_code, description, status) VALUES (#{roleName}, #{roleCode}, #{description}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Role role);

    @Update("UPDATE role SET role_name = #{roleName}, role_code = #{roleCode}, description = #{description}, status = #{status} WHERE id = #{id}")
    int update(Role role);

    @Delete("DELETE FROM role WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT p.* FROM permission p JOIN role_permission rp ON p.id = rp.permission_id WHERE rp.role_id = #{roleId}")
    List<com.example.backend.entity.Permission> findPermissionsByRoleId(Long roleId);

    @Select("SELECT COUNT(*) FROM user_role WHERE role_id = #{roleId}")
    int countUsersByRoleId(Long roleId);

    @Insert("INSERT INTO role_permission (role_id, permission_id) VALUES (#{roleId}, #{permissionId})")
    int addPermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    @Delete("DELETE FROM role_permission WHERE role_id = #{roleId}")
    int deleteAllPermissionsByRoleId(Long roleId);

    @Delete("DELETE FROM role_permission WHERE role_id = #{roleId} AND permission_id = #{permissionId}")
    int deletePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}