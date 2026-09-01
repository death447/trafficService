package com.example.backend.mapper;

import com.example.backend.entity.User;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM user WHERE id = #{id}")
    User findById(Long id);

    @Select("SELECT * FROM user WHERE username = #{username}")
    User findByUsername(String username);

    @Select("SELECT * FROM user")
    List<User> findAll();

    @Select("SELECT * FROM user WHERE username LIKE CONCAT('%', #{keyword}, '%') OR email LIKE CONCAT('%', #{keyword}, '%') OR real_name LIKE CONCAT('%', #{keyword}, '%')")
    List<User> findByKeyword(String keyword);

    @Select("SELECT * FROM user WHERE status = #{status}")
    List<User> findByStatus(Integer status);

    @Insert("INSERT INTO user (username, email, password, phone, real_name, status) VALUES (#{username}, #{email}, #{password}, #{phone}, #{realName}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("UPDATE user SET username = #{username}, email = #{email}, password = #{password}, phone = #{phone}, real_name = #{realName}, status = #{status} WHERE id = #{id}")
    int update(User user);

    @Delete("DELETE FROM user WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT r.* FROM role r JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<com.example.backend.entity.Role> findRolesByUserId(Long userId);

    @Select("SELECT p.* FROM permission p " +
            "JOIN role_permission rp ON p.id = rp.permission_id " +
            "JOIN user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<com.example.backend.entity.Permission> findPermissionsByUserId(Long userId);
}
