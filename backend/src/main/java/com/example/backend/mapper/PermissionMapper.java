package com.example.backend.mapper;

import com.example.backend.entity.Permission;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface PermissionMapper {

    @Select("SELECT * FROM permission WHERE id = #{id}")
    Permission findById(Long id);

    @Select("SELECT * FROM permission")
    List<Permission> findAll();

    @Select("SELECT * FROM permission WHERE permission_code = #{permissionCode}")
    Permission findByPermissionCode(String permissionCode);

    @Select("SELECT * FROM permission WHERE parent_id = #{parentId} ORDER BY sort_order")
    List<Permission> findByParentId(Long parentId);

    @Select("SELECT * FROM permission WHERE permission_type = #{permissionType}")
    List<Permission> findByPermissionType(String permissionType);

    @Select("SELECT * FROM permission WHERE permission_type = #{permissionType} AND parent_id = #{parentId} ORDER BY sort_order")
    List<Permission> findByPermissionTypeAndParentId(@Param("permissionType") String permissionType, @Param("parentId") Long parentId);

    @Select("SELECT * FROM permission WHERE permission_name LIKE CONCAT('%', #{keyword}, '%') OR permission_code LIKE CONCAT('%', #{keyword}, '%')")
    List<Permission> findByKeyword(String keyword);

    @Select("<script>" +
            "SELECT COUNT(*) FROM permission WHERE 1=1" +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (permission_name LIKE CONCAT('%', #{keyword}, '%') OR permission_code LIKE CONCAT('%', #{keyword}, '%'))" +
            "</if>" +
            "<if test='permissionType != null and permissionType != \"\"'> AND permission_type = #{permissionType}</if>" +
            "</script>")
    long count(@Param("keyword") String keyword, @Param("permissionType") String permissionType);

    @Select("<script>" +
            "SELECT * FROM permission WHERE 1=1" +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (permission_name LIKE CONCAT('%', #{keyword}, '%') OR permission_code LIKE CONCAT('%', #{keyword}, '%'))" +
            "</if>" +
            "<if test='permissionType != null and permissionType != \"\"'> AND permission_type = #{permissionType}</if>" +
            " ORDER BY id DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<Permission> selectPage(@Param("keyword") String keyword,
                                @Param("permissionType") String permissionType,
                                @Param("offset") int offset,
                                @Param("size") int size);

    @Insert("INSERT INTO permission (permission_name, permission_code, permission_type, parent_id, description, sort_order) " +
            "VALUES (#{permissionName}, #{permissionCode}, #{permissionType}, #{parentId}, #{description}, #{sortOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Permission permission);

    @Update("UPDATE permission SET permission_name = #{permissionName}, permission_code = #{permissionCode}, permission_type = #{permissionType}, " +
            "parent_id = #{parentId}, description = #{description}, sort_order = #{sortOrder} WHERE id = #{id}")
    int update(Permission permission);

    @Delete("DELETE FROM permission WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT COUNT(*) FROM permission WHERE parent_id = #{id}")
    int countChildrenById(Long id);
}
