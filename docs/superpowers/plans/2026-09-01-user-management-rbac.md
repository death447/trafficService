# 用户管理 RBAC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成道路交通事故救援派单系统的用户/角色/权限管理模块，落地 JWT + Spring Security 的 RBAC（用户可多角色、权限可动态分配）。

**Architecture:** 在现有 Vue3 + SpringBoot3 + MyBatis 骨架上补齐：数据库五表（user/role/permission/user_role/role_permission）→ JWT 认证 → 用户/角色/权限 CRUD API → 前端管理页与权限指令。后端以 Spring Security 过滤器链替换现有 `X-User-Id` 拦截器。

**Tech Stack:** Spring Boot 3.2、Spring Security、JJWT、MyBatis、MySQL、Vue 3、Pinia、Vue Router、Axios

**Spec:** `docs/superpowers/specs/2026-09-01-user-management-rbac-design.md`

## Global Constraints

- 四类角色编码固定：`TRAFFIC_POLICE`、`DISPATCHER`、`TOW_DRIVER`、`PARKING_ADMIN`
- 派单管理权限归属调度员（`DISPATCHER`），交警仅事故处理相关权限
- 用户可拥有多个角色；角色权限可动态分配
- 权限粒度到功能模块级（`MODULE` / `BUTTON` / `API`）
- 密码使用 BCrypt；JWT 过期默认 2 小时
- 统一响应使用现有 `com.example.backend.common.Result`
- 不引入 Redis（首版权限查库即可，YAGNI）
- 前端暂不强制 UI 组件库；可用原生 HTML + 简单样式，或按项目后续引入的组件库适配

## 现有代码盘点（执行前必读）

**已有（可复用/需改造）：**
- Entity：`User`（含 phone/realName/status）、`Role`、`Permission`
- Service：`UserService`、`RoleService`、`PermissionService`
- Controller：`UserController`、`RoleController`、`PermissionController`
- Mapper：`UserMapper`、`RoleMapper`、`PermissionMapper`、`UserRoleMapper`
- 简易权限：`@RequiresPermission` + `PermissionInterceptor`（基于请求头 `X-User-Id`，**将被 JWT 替换**）

**缺失：**
- `database/init.sql` 仍是旧三字段用户表，无 role/permission 关联表
- `pom.xml` 无 Spring Security / JWT
- 无登录接口、无 JWT 工具、无 SecurityConfig
- 前端无登录页、用户/角色/权限管理页

## File Structure

### Backend（新建）
- `backend/src/main/java/com/example/backend/security/JwtTokenProvider.java` — JWT 生成/解析
- `backend/src/main/java/com/example/backend/security/JwtAuthenticationFilter.java` — 请求认证过滤器
- `backend/src/main/java/com/example/backend/security/CustomUserDetails.java` — UserDetails 实现
- `backend/src/main/java/com/example/backend/security/CustomUserDetailsService.java` — 加载用户+权限
- `backend/src/main/java/com/example/backend/config/SecurityConfig.java` — Security 配置
- `backend/src/main/java/com/example/backend/controller/AuthController.java` — 登录/登出/刷新
- `backend/src/main/java/com/example/backend/service/AuthService.java` — 认证业务
- `backend/src/main/java/com/example/backend/dto/LoginRequest.java`
- `backend/src/main/java/com/example/backend/dto/LoginResponse.java`
- `backend/src/main/java/com/example/backend/mapper/RolePermissionMapper.java`
- `backend/src/test/java/com/example/backend/security/JwtTokenProviderTest.java`
- `backend/src/test/java/com/example/backend/service/UserServiceTest.java`

### Backend（修改）
- `backend/pom.xml` — 增加 security、jjwt
- `backend/src/main/resources/application.yml` — JWT 配置
- `database/init.sql` — 完整 RBAC 表与初始数据
- `UserService` — 密码 BCrypt 加密
- Controllers — 加 `@PreAuthorize`；登录接口放行
- 删除或停用：`PermissionInterceptor`、`WebConfig` 中的拦截器注册、`@RequiresPermission`（改用 `@PreAuthorize`）

### Frontend（新建）
- `frontend/src/api/auth.js`、`user.js`、`role.js`、`permission.js`
- `frontend/src/stores/user.js`
- `frontend/src/utils/request.js`（改造或新建 Axios 封装）
- `frontend/src/directives/auth.js`
- `frontend/src/views/Login.vue`
- `frontend/src/views/user/UserList.vue`
- `frontend/src/views/role/RoleList.vue`
- `frontend/src/views/permission/PermissionList.vue`
- `frontend/src/router/index.js`（改造）

---

### Task 1: 数据库 RBAC 表与初始数据

**Files:**
- Modify: `database/init.sql`
- Test: 在 MySQL 客户端执行脚本并 `SHOW TABLES`

**Interfaces:**
- Produces: 表 `user`、`role`、`permission`、`user_role`、`role_permission`；初始角色 4 个；admin 用户（密码明文占位，Task 3 起改为 BCrypt）

- [ ] **Step 1: 重写 `database/init.sql`**

将文件替换为：

```sql
CREATE DATABASE IF NOT EXISTS vue_springboot_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE vue_springboot_system;

DROP TABLE IF EXISTS `role_permission`;
DROP TABLE IF EXISTS `user_role`;
DROP TABLE IF EXISTS `permission`;
DROP TABLE IF EXISTS `role`;
DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `email` VARCHAR(100) NOT NULL COMMENT '邮箱',
  `password` VARCHAR(255) NOT NULL COMMENT '密码',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
  `status` TINYINT DEFAULT 1 COMMENT '状态：1-启用，0-停用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE `role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `role_name` VARCHAR(50) NOT NULL,
  `role_code` VARCHAR(50) NOT NULL,
  `description` VARCHAR(200) DEFAULT NULL,
  `status` TINYINT DEFAULT 1,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

CREATE TABLE `permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `permission_name` VARCHAR(100) NOT NULL,
  `permission_code` VARCHAR(100) NOT NULL,
  `permission_type` VARCHAR(20) NOT NULL COMMENT 'MODULE/BUTTON/API',
  `parent_id` BIGINT DEFAULT 0,
  `description` VARCHAR(200) DEFAULT NULL,
  `sort_order` INT DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

CREATE TABLE `user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

CREATE TABLE `role_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `role_id` BIGINT NOT NULL,
  `permission_id` BIGINT NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

INSERT INTO `role` (`role_name`, `role_code`, `description`) VALUES
('交警', 'TRAFFIC_POLICE', '负责事故处理'),
('调度员', 'DISPATCHER', '负责派单管理、资源调度、任务分配'),
('拖车施救员', 'TOW_DRIVER', '负责执行救援任务'),
('停车场管理员', 'PARKING_ADMIN', '负责停车场管理'),
('系统管理员', 'ADMIN', '系统管理，含用户角色权限管理');

INSERT INTO `permission` (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`) VALUES
(1, '用户管理', 'user:manage', 'MODULE', 0, 1),
(2, '用户查询', 'user:query', 'BUTTON', 1, 1),
(3, '用户新增', 'user:add', 'BUTTON', 1, 2),
(4, '用户编辑', 'user:edit', 'BUTTON', 1, 3),
(5, '用户删除', 'user:delete', 'BUTTON', 1, 4),
(6, '角色管理', 'role:manage', 'MODULE', 0, 2),
(7, '角色查询', 'role:query', 'BUTTON', 6, 1),
(8, '角色新增', 'role:add', 'BUTTON', 6, 2),
(9, '角色编辑', 'role:edit', 'BUTTON', 6, 3),
(10, '角色删除', 'role:delete', 'BUTTON', 6, 4),
(11, '权限管理', 'permission:manage', 'MODULE', 0, 3),
(12, '权限查询', 'permission:query', 'BUTTON', 11, 1),
(13, '权限新增', 'permission:add', 'BUTTON', 11, 2),
(14, '权限编辑', 'permission:edit', 'BUTTON', 11, 3),
(15, '权限删除', 'permission:delete', 'BUTTON', 11, 4),
(16, '派单管理', 'dispatch:manage', 'MODULE', 0, 4),
(17, '事故处理', 'accident:manage', 'MODULE', 0, 5),
(18, '救援执行', 'rescue:manage', 'MODULE', 0, 6),
(19, '停车场管理', 'parking:manage', 'MODULE', 0, 7);

-- ADMIN 拥有全部管理权限 1-15
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, id FROM `permission` WHERE id BETWEEN 1 AND 15;

-- DISPATCHER 拥有派单管理
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 16);

-- TRAFFIC_POLICE 拥有事故处理
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 17);

-- TOW_DRIVER 拥有救援执行
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (3, 18);

-- PARKING_ADMIN 拥有停车场管理
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (4, 19);

-- admin 用户密码占位；Task 3 用 BCrypt 替换。临时明文仅用于确认表结构，不可用于生产
INSERT INTO `user` (`username`, `email`, `password`, `phone`, `real_name`, `status`) VALUES
('admin', 'admin@example.com', '{bcrypt-placeholder}', '13800000000', '系统管理员', 1);

INSERT INTO `user_role` (`user_id`, `role_id`) VALUES (1, 5);
```

- [ ] **Step 2: 执行脚本并验证**

Run（按本机 MySQL 路径调整；Windows 若未配置 PATH，使用完整路径如 `C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe`）:

```bash
mysql -u root -p < database/init.sql
mysql -u root -p -e "USE vue_springboot_system; SHOW TABLES; SELECT role_code FROM role; SELECT permission_code FROM permission LIMIT 5;"
```

Expected: 5 张表；角色含 `TRAFFIC_POLICE`/`DISPATCHER`/`TOW_DRIVER`/`PARKING_ADMIN`/`ADMIN`

- [ ] **Step 3: Commit**

```bash
git add database/init.sql
git commit -m "feat(db): add RBAC tables and seed roles/permissions"
```

---

### Task 2: 引入 Spring Security 与 JWT 依赖及配置

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Test: `mvn -q -f backend/pom.xml dependency:resolve`

**Interfaces:**
- Produces: 依赖 `spring-boot-starter-security`、`jjwt-api/impl/jackson`；配置项 `app.jwt.secret`、`app.jwt.expiration-ms`

- [ ] **Step 1: 在 `pom.xml` 的 `<dependencies>` 中增加**

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>0.12.5</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>0.12.5</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>0.12.5</version>
            <scope>runtime</scope>
        </dependency>
```

- [ ] **Step 2: 在 `application.yml` 末尾追加**

```yaml
app:
  jwt:
    # 生产环境请用环境变量覆盖；长度建议 >= 32 字节
    secret: "vue-springboot-rbac-jwt-secret-change-me-32bytes"
    expiration-ms: 7200000
```

- [ ] **Step 3: 解析依赖**

Run: `mvn -q -f backend/pom.xml dependency:resolve`

Expected: BUILD SUCCESS，无缺失 artifact

- [ ] **Step 4: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.yml
git commit -m "chore(backend): add Spring Security and JJWT dependencies"
```

---

### Task 3: JWT 工具与 UserDetails

**Files:**
- Create: `backend/src/main/java/com/example/backend/security/JwtTokenProvider.java`
- Create: `backend/src/main/java/com/example/backend/security/CustomUserDetails.java`
- Create: `backend/src/main/java/com/example/backend/security/CustomUserDetailsService.java`
- Create: `backend/src/test/java/com/example/backend/security/JwtTokenProviderTest.java`
- Modify: `database/init.sql` 中 admin 密码（生成 BCrypt 后回填）或用启动脚本/手动 UPDATE

**Interfaces:**
- Consumes: `UserMapper.findByUsername`；`UserMapper.findRolesByUserId`；`UserMapper.findPermissionsByUserId`
- Produces:
  - `JwtTokenProvider.generateToken(CustomUserDetails user): String`
  - `JwtTokenProvider.getUsername(String token): String`
  - `JwtTokenProvider.validateToken(String token): boolean`
  - `CustomUserDetails` implements `UserDetails`，含 `Long getId()`、`Collection<? extends GrantedAuthority> getAuthorities()`（权限码作 authority）
  - `CustomUserDetailsService.loadUserByUsername(String username): UserDetails`

- [ ] **Step 1: 写失败测试 `JwtTokenProviderTest.java`**

```java
package com.example.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", "vue-springboot-rbac-jwt-secret-change-me-32bytes");
        ReflectionTestUtils.setField(provider, "expirationMs", 7200000L);
    }

    @Test
    void generateAndParseToken() {
        CustomUserDetails user = new CustomUserDetails(
                1L, "admin", "encoded", true, Collections.emptyList());
        String token = provider.generateToken(user);
        assertNotNull(token);
        assertTrue(provider.validateToken(token));
        assertEquals("admin", provider.getUsername(token));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -f backend/pom.xml -Dtest=JwtTokenProviderTest test`

Expected: FAIL（类不存在或编译错误）

- [ ] **Step 3: 实现 `CustomUserDetails.java`**

```java
package com.example.backend.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

@Getter
public class CustomUserDetails implements UserDetails {
    private final Long id;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(Long id, String username, String password, boolean enabled,
                             Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.authorities = authorities;
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
```

- [ ] **Step 4: 实现 `JwtTokenProvider.java`**

```java
package com.example.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(CustomUserDetails user) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .issuedAt(now)
                .expiration(exp)
                .signWith(key())
                .compact();
    }

    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public Long getUserId(String token) {
        return parseClaims(token).get("uid", Long.class);
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(token).getPayload();
    }
}
```

- [ ] **Step 5: 实现 `CustomUserDetailsService.java`**

```java
package com.example.backend.security;

import com.example.backend.entity.Permission;
import com.example.backend.entity.User;
import com.example.backend.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        List<Permission> permissions = userMapper.findPermissionsByUserId(user.getId());
        List<SimpleGrantedAuthority> authorities = permissions.stream()
                .map(p -> new SimpleGrantedAuthority(p.getPermissionCode()))
                .collect(Collectors.toList());
        boolean enabled = user.getStatus() == null || user.getStatus() == 1;
        return new CustomUserDetails(user.getId(), user.getUsername(), user.getPassword(), enabled, authorities);
    }
}
```

- [ ] **Step 6: 跑通 JWT 单元测试**

Run: `mvn -f backend/pom.xml -Dtest=JwtTokenProviderTest test`

Expected: PASS

- [ ] **Step 7: 生成 admin 的 BCrypt 密码并更新库**

在任意临时 Java main 或测试中：

```java
System.out.println(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("admin123"));
```

然后执行：

```sql
UPDATE `user` SET password = '<生成的BCrypt串>' WHERE username = 'admin';
```

同时把 `init.sql` 里 admin 的 password 换成该 BCrypt 串，避免下次重建库失败。

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/example/backend/security backend/src/test/java/com/example/backend/security database/init.sql
git commit -m "feat(security): add JWT provider and CustomUserDetails"
```

---

### Task 4: SecurityConfig、JWT Filter、登录 API

**Files:**
- Create: `backend/src/main/java/com/example/backend/security/JwtAuthenticationFilter.java`
- Create: `backend/src/main/java/com/example/backend/config/SecurityConfig.java`
- Create: `backend/src/main/java/com/example/backend/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/example/backend/dto/LoginResponse.java`
- Create: `backend/src/main/java/com/example/backend/service/AuthService.java`
- Create: `backend/src/main/java/com/example/backend/controller/AuthController.java`
- Modify: `backend/src/main/java/com/example/backend/config/WebConfig.java` — 移除 PermissionInterceptor 注册
- Delete or leave unused: `PermissionInterceptor.java`、`RequiresPermission.java`（本任务结束后不再使用）

**Interfaces:**
- Consumes: `JwtTokenProvider`、`CustomUserDetailsService`、`AuthenticationManager`
- Produces:
  - `POST /api/auth/login` body `{username, password}` → `{token, userId, username, permissions}`
  - `POST /api/auth/logout` → success（客户端清 token 即可）
  - Filter: `Authorization: Bearer <token>`

- [ ] **Step 1: DTO**

`LoginRequest.java`:

```java
package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
}
```

`LoginResponse.java`:

```java
package com.example.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
    private List<String> permissions;
    private List<String> roles;
}
```

- [ ] **Step 2: `JwtAuthenticationFilter.java`**

```java
package com.example.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenProvider tokenProvider;
    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (tokenProvider.validateToken(token)) {
                String username = tokenProvider.getUsername(token);
                CustomUserDetails userDetails = (CustomUserDetails) userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 3: `SecurityConfig.java`**

```java
package com.example.backend.config;

import com.example.backend.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/hello").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

- [ ] **Step 4: `AuthService` + `AuthController`**

`AuthService.java`:

```java
package com.example.backend.service;

import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.LoginResponse;
import com.example.backend.entity.Role;
import com.example.backend.mapper.UserMapper;
import com.example.backend.security.CustomUserDetails;
import com.example.backend.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthService {

    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @Autowired
    private UserMapper userMapper;

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();
        String token = tokenProvider.generateToken(user);
        List<String> permissions = user.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(Collectors.toList());
        List<String> roles = userMapper.findRolesByUserId(user.getId()).stream()
                .map(Role::getRoleCode).collect(Collectors.toList());
        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .permissions(permissions)
                .roles(roles)
                .build();
    }
}
```

`AuthController.java`:

```java
package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.LoginResponse;
import com.example.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success(null);
    }
}
```

- [ ] **Step 5: 停用旧拦截器**

清空 `WebConfig.addInterceptors` 的注册（可保留空实现类），或删除 `PermissionInterceptor` / `RequiresPermission`。

- [ ] **Step 6: 手动验证登录**

启动：`mvn -f backend/pom.xml spring-boot:run`

```bash
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
```

Expected: `code=200`，返回 `token`

无 token 访问：

```bash
curl http://localhost:8080/api/user/list
```

Expected: 401

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/example/backend
git commit -m "feat(auth): add JWT login and Spring Security filter chain"
```

---

### Task 5: 用户创建/更新密码加密 + 方法级权限

**Files:**
- Modify: `backend/src/main/java/com/example/backend/service/UserService.java`
- Modify: `backend/src/main/java/com/example/backend/controller/UserController.java`
- Modify: `backend/src/main/java/com/example/backend/controller/RoleController.java`
- Modify: `backend/src/main/java/com/example/backend/controller/PermissionController.java`
- Create: `backend/src/test/java/com/example/backend/service/UserServiceTest.java`（可用 Mockito，或集成测试）

**Interfaces:**
- Consumes: `PasswordEncoder`
- Produces: `createUser`/`updateUser` 存 BCrypt；Controller 方法使用 `@PreAuthorize("hasAuthority('user:query')")` 等

- [ ] **Step 1: `UserService` 注入 `PasswordEncoder`，在 create/update 时加密**

在 `createUser` 开头：

```java
user.setPassword(passwordEncoder.encode(user.getPassword()));
```

在 `updateUser` 中仅当 password 非空时：

```java
if (user.getPassword() != null && !user.getPassword().isBlank()) {
    user.setPassword(passwordEncoder.encode(user.getPassword()));
} else {
    User existing = userMapper.findById(user.getId());
    user.setPassword(existing.getPassword());
}
```

- [ ] **Step 2: Controller 加权限注解（示例）**

```java
@GetMapping("/list")
@PreAuthorize("hasAuthority('user:query')")
public Result<Map<String, Object>> getUserList(...) { ... }

@PostMapping
@PreAuthorize("hasAuthority('user:add')")
public Result<User> createUser(...) { ... }

@PutMapping("/{id}")
@PreAuthorize("hasAuthority('user:edit')")
public Result<User> updateUser(...) { ... }

@DeleteMapping("/{id}")
@PreAuthorize("hasAuthority('user:delete')")
public Result<Void> deleteUser(...) { ... }
```

对 Role/Permission Controller 同理使用 `role:*`、`permission:*`。

- [ ] **Step 3: 用 admin token 验证有权限接口；用仅有 `dispatch:manage` 的用户验证 403**

Expected: admin 200；无权限角色 403

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/example/backend
git commit -m "feat(user): bcrypt passwords and PreAuthorize on APIs"
```

---

### Task 6: 补齐 RolePermissionMapper 与角色分配权限接口

**Files:**
- Create: `backend/src/main/java/com/example/backend/mapper/RolePermissionMapper.java`
- Modify: `backend/src/main/java/com/example/backend/service/RoleService.java`
- Modify: `backend/src/main/java/com/example/backend/controller/RoleController.java`（确保 `PUT/POST .../permissions` 可用）

**Interfaces:**
- Produces:
  - `RolePermissionMapper.insert(roleId, permissionId)`
  - `RolePermissionMapper.deleteAllByRoleId(roleId)`
  - `RoleService.assignPermissions(Long roleId, List<Long> permissionIds): boolean`

- [ ] **Step 1: 创建 Mapper**

```java
package com.example.backend.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface RolePermissionMapper {
    @Insert("INSERT INTO role_permission (role_id, permission_id) VALUES (#{roleId}, #{permissionId})")
    int insert(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    @Delete("DELETE FROM role_permission WHERE role_id = #{roleId}")
    int deleteAllByRoleId(Long roleId);
}
```

- [ ] **Step 2: `RoleService.assignPermissions` 事务内先删后插**

```java
@Transactional
public boolean assignPermissions(Long roleId, List<Long> permissionIds) {
    rolePermissionMapper.deleteAllByRoleId(roleId);
    if (permissionIds != null) {
        for (Long pid : permissionIds) {
            rolePermissionMapper.insert(roleId, pid);
        }
    }
    return true;
}
```

- [ ] **Step 3: Controller 暴露并加 `@PreAuthorize("hasAuthority('role:edit')")`**

- [ ] **Step 4: 手动 curl 验证分配后登录用户权限列表变化（需重新登录以刷新 JWT 内/Security 加载的权限）**

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/backend
git commit -m "feat(role): support dynamic role-permission assignment"
```

---

### Task 7: 前端 Axios、UserStore、登录页与路由守卫

**Files:**
- Create: `frontend/src/utils/request.js`
- Create: `frontend/src/api/auth.js`
- Create: `frontend/src/stores/user.js`
- Create: `frontend/src/views/Login.vue`
- Modify: `frontend/src/router/index.js`（若无则创建）
- Modify: `frontend/src/main.js`、`frontend/src/App.vue`

**Interfaces:**
- Produces: `useUserStore().login(username, password)`；请求头自动带 `Authorization: Bearer`；未登录跳转 `/login`

- [ ] **Step 1: `request.js`**

```javascript
import axios from 'axios'
import { useUserStore } from '../stores/user'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const store = useUserStore()
  if (store.token) {
    config.headers.Authorization = `Bearer ${store.token}`
  }
  return config
})

request.interceptors.response.use(
  (res) => {
    const data = res.data
    if (data.code !== 200) {
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return data
  },
  (err) => {
    if (err.response?.status === 401) {
      const store = useUserStore()
      store.logout()
      router.push('/login')
    }
    return Promise.reject(err)
  }
)

export default request
```

- [ ] **Step 2: `stores/user.js` + `api/auth.js`**

```javascript
// api/auth.js
import request from '../utils/request'
export function loginApi(data) {
  return request.post('/auth/login', data)
}
```

```javascript
// stores/user.js
import { defineStore } from 'pinia'
import { loginApi } from '../api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userId: null,
    username: '',
    permissions: JSON.parse(localStorage.getItem('permissions') || '[]'),
    roles: JSON.parse(localStorage.getItem('roles') || '[]')
  }),
  actions: {
    async login(username, password) {
      const res = await loginApi({ username, password })
      this.token = res.data.token
      this.userId = res.data.userId
      this.username = res.data.username
      this.permissions = res.data.permissions || []
      this.roles = res.data.roles || []
      localStorage.setItem('token', this.token)
      localStorage.setItem('permissions', JSON.stringify(this.permissions))
      localStorage.setItem('roles', JSON.stringify(this.roles))
    },
    logout() {
      this.token = ''
      this.permissions = []
      this.roles = []
      localStorage.removeItem('token')
      localStorage.removeItem('permissions')
      localStorage.removeItem('roles')
    },
    hasPermission(code) {
      return this.permissions.includes(code)
    }
  }
})
```

- [ ] **Step 3: `Login.vue` 简单表单（用户名/密码 → 登录成功跳转 `/`）**

- [ ] **Step 4: 路由守卫**

```javascript
router.beforeEach((to, from, next) => {
  const store = useUserStore()
  if (to.path !== '/login' && !store.token) {
    next('/login')
  } else if (to.meta.permissions) {
    const ok = to.meta.permissions.every((p) => store.hasPermission(p))
    ok ? next() : next('/403')
  } else {
    next()
  }
})
```

- [ ] **Step 5: `npm run dev`，用 admin/admin123 登录验证**

Expected: 登录成功进入首页，localStorage 有 token

- [ ] **Step 6: Commit**

```bash
git add frontend
git commit -m "feat(frontend): add login, token store, and route guard"
```

---

### Task 8: 前端用户/角色/权限管理页面

**Files:**
- Create: `frontend/src/api/user.js`、`role.js`、`permission.js`
- Create: `frontend/src/views/user/UserList.vue`
- Create: `frontend/src/views/role/RoleList.vue`
- Create: `frontend/src/views/permission/PermissionList.vue`
- Create: `frontend/src/directives/auth.js`
- Modify: `frontend/src/router/index.js`、`App.vue`（导航菜单）

**Interfaces:**
- Consumes: 后端 `/api/user`、`/api/roles`（以现有 Controller 路径为准，实现时对照实际 `@RequestMapping`）、`/api/permissions`
- Produces: 列表+表单 CRUD；角色页勾选权限；`v-auth` 指令

- [ ] **Step 1: 封装 API，路径与现有 Controller 对齐**

先读 `UserController`/`RoleController`/`PermissionController` 的 `@RequestMapping`，例如用户是 `/api/user` 而非设计文档的 `/api/users`——**以代码为准**。

- [ ] **Step 2: `UserList.vue`**

- 表格：用户名、邮箱、手机号、真实姓名、状态、角色
- 操作：新增/编辑（含 roleIds 多选）、删除、状态
- 按钮使用 `v-auth="'user:add'"` 等

- [ ] **Step 3: `RoleList.vue`**

- 角色列表 + 分配权限（勾选权限树/列表）
- 调用角色权限分配接口

- [ ] **Step 4: `PermissionList.vue`**

- 树形或分组列表展示 MODULE/BUTTON
- 增删改权限

- [ ] **Step 5: `directives/auth.js`**

```javascript
export default {
  mounted(el, binding) {
    const store = useUserStore()
    const code = binding.value
    if (!store.hasPermission(code)) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}
```

在 `main.js`：`app.directive('auth', authDirective)`

- [ ] **Step 6: 路由注册**

```javascript
{ path: '/users', component: () => import('../views/user/UserList.vue'), meta: { permissions: ['user:manage'] } },
{ path: '/roles', component: () => import('../views/role/RoleList.vue'), meta: { permissions: ['role:manage'] } },
{ path: '/permissions', component: () => import('../views/permission/PermissionList.vue'), meta: { permissions: ['permission:manage'] } }
```

- [ ] **Step 7: 端到端验证**

1. admin 登录可进三页并 CRUD
2. 给某角色去掉 `user:delete` 后重新登录，删除按钮消失且 API 403
3. 调度员角色仅能访问派单相关（本阶段若尚无派单页，至少验证 permissions 数组含 `dispatch:manage` 不含 `user:manage`）

- [ ] **Step 8: Commit**

```bash
git add frontend
git commit -m "feat(frontend): add user/role/permission management pages"
```

---

### Task 9: 全局异常与 README 更新

**Files:**
- Create: `backend/src/main/java/com/example/backend/common/GlobalExceptionHandler.java`
- Modify: `README.md`（登录方式、初始账号、RBAC 说明）

- [ ] **Step 1: GlobalExceptionHandler**

```java
package com.example.backend.common;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> badCredentials(BadCredentialsException e) {
        return Result.error("用户名或密码错误");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> accessDenied(AccessDeniedException e) {
        return Result.error("权限不足");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> other(Exception e) {
        return Result.error(e.getMessage() != null ? e.getMessage() : "服务器错误");
    }
}
```

- [ ] **Step 2: README 补充：执行 `database/init.sql`、默认账号 `admin/admin123`、JWT Header 格式**

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/example/backend/common/GlobalExceptionHandler.java README.md
git commit -m "docs: document RBAC login and add global exception handler"
```

---

## Spec Coverage Checklist

| Spec 要求 | Task |
|-----------|------|
| user 表含 phone/real_name/status | Task 1 |
| 四角色 + 派单归调度员 | Task 1 |
| 用户多角色、动态权限 | Task 1, 5, 6 |
| Spring Security + JWT | Task 2–4 |
| 用户/角色/权限 API | 已有 + Task 5–6 |
| 前端登录与管理页 | Task 7–8 |
| 错误处理 | Task 9 |
| BCrypt | Task 3, 5 |
| Redis 缓存 | **故意不做**（YAGNI，spec 扩展项） |

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-01-user-management-rbac.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — 每个 Task 派一个新子代理，任务间人工复核，迭代快  
2. **Inline Execution** — 本会话用 executing-plans 连续执行，按检查点暂停

选哪种方式开始实现？
