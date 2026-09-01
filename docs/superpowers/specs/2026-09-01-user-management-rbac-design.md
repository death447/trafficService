# 用户管理RBAC系统设计文档

## 概述

为道路交通事故救援派单系统设计一个完整的用户管理功能模块，包含用户管理、角色管理、权限管理三大核心功能。系统支持4类用户：交警、调度员、拖车施救员、停车场管理员，采用RBAC（基于角色的访问控制）模型，支持一个用户拥有多个角色，权限可动态分配。

## 需求概述

### 用户类型
- **交警**（TRAFFIC_POLICE）：负责事故处理
- **调度员**（DISPATCHER）：负责派单管理、资源调度、任务分配
- **拖车施救员**（TOW_DRIVER）：负责执行救援任务
- **停车场管理员**（PARKING_ADMIN）：负责停车场管理

### 功能需求
- 用户管理：用户CRUD、角色分配、状态管理
- 角色管理：角色CRUD、权限分配、动态配置
- 权限管理：权限CRUD、树形结构、多级分类
- 认证授权：JWT令牌认证、动态权限验证

### 非功能需求
- 安全性：基于Spring Security的安全框架
- 性能：权限缓存、Token刷新机制
- 可扩展性：支持新增角色和权限类型
- 可维护性：清晰的分层架构和代码规范

## 技术方案

### 总体架构
采用传统RBAC模型（用户-角色-权限三层），整合Spring Security进行安全验证，JWT实现无状态认证。

**核心组件：**
- **Spring Security**：负责认证和授权
- **JWT Token**：无状态认证，支持前后端分离
- **RBAC模型**：用户(User) ←→ 角色(Role) ←→ 权限(Permission) 的多对多关系
- **动态权限加载**：支持运行时动态调整角色权限

**技术架构层次：**
- **表现层**：RESTful API + JWT验证
- **业务层**：用户管理、角色管理、权限管理服务
- **数据层**：MyBatis + MySQL
- **安全层**：Spring Security过滤器链 + 自定义权限处理器

**数据流向：**
1. 用户登录 → 验证凭证 → 生成JWT Token
2. 前端请求携带Token → 后端拦截器验证 → 解析用户权限
3. 方法调用 → 权限注解检查 → 执行业务逻辑

### 技术栈
- **后端**：Spring Boot 3.2.0 + Spring Security + JWT + MyBatis
- **前端**：Vue 3 + Pinia + Vue Router + Axios
- **数据库**：MySQL 8.0+
- **安全**：Spring Security + JWT + BCrypt密码加密

## 数据库设计

### 表结构设计

#### 1. user表（用户表）
在现有基础上扩展：

```sql
CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `email` VARCHAR(100) NOT NULL COMMENT '邮箱',
  `password` VARCHAR(255) NOT NULL COMMENT '密码',
  `phone` VARCHAR(20) COMMENT '手机号',
  `real_name` VARCHAR(50) COMMENT '真实姓名',
  `status` TINYINT DEFAULT 1 COMMENT '状态：1-启用，0-停用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
```

#### 2. role表（角色表）
```sql
CREATE TABLE `role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_name` VARCHAR(50) NOT NULL COMMENT '角色名称',
  `role_code` VARCHAR(50) NOT NULL COMMENT '角色编码',
  `description` VARCHAR(200) COMMENT '角色描述',
  `status` TINYINT DEFAULT 1 COMMENT '状态：1-启用，0-停用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';
```

#### 3. permission表（权限表）
```sql
CREATE TABLE `permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `permission_name` VARCHAR(100) NOT NULL COMMENT '权限名称',
  `permission_code` VARCHAR(100) NOT NULL COMMENT '权限编码',
  `permission_type` VARCHAR(20) NOT NULL COMMENT '权限类型：MODULE-模块，BUTTON-按钮，API-接口',
  `parent_id` BIGINT DEFAULT 0 COMMENT '父权限ID',
  `description` VARCHAR(200) COMMENT '权限描述',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';
```

#### 4. user_role表（用户角色关联表）
```sql
CREATE TABLE `user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';
```

#### 5. role_permission表（角色权限关联表）
```sql
CREATE TABLE `role_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `permission_id` BIGINT NOT NULL COMMENT '权限ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';
```

### 表关系特点
- **多对多关系**：一个用户可以有多个角色，一个角色可以有多个权限
- **支持动态分配**：通过关联表的增删改实现权限的动态调整
- **软删除支持**：通过status字段控制启用/停用

### 初始数据
```sql
-- 插入初始角色
INSERT INTO `role` (`role_name`, `role_code`, `description`) VALUES
('交警', 'TRAFFIC_POLICE', '负责事故处理、派单管理'),
('调度员', 'DISPATCHER', '负责资源调度、任务分配'),
('拖车施救员', 'TOW_DRIVER', '负责执行救援任务'),
('停车场管理员', 'PARKING_ADMIN', '负责停车场管理');

-- 插入初始权限（示例）
INSERT INTO `permission` (`permission_name`, `permission_code`, `permission_type`, `parent_id`) VALUES
('用户管理', 'user:manage', 'MODULE', 0),
('用户查询', 'user:query', 'BUTTON', 1),
('用户新增', 'user:add', 'BUTTON', 1),
('用户编辑', 'user:edit', 'BUTTON', 1),
('用户删除', 'user:delete', 'BUTTON', 1),
('角色管理', 'role:manage', 'MODULE', 0),
('权限管理', 'permission:manage', 'MODULE', 0);
```

## 后端模块设计

### 分层架构

**Controller层：**
- UserController、RoleController、PermissionController
- 职责：接收HTTP请求，参数验证，调用Service，返回结果

**Service层：**
- **UserService**：用户业务逻辑，包括用户CRUD、密码加密、用户角色关联管理、用户状态控制
- **RoleService**：角色业务逻辑，包括角色CRUD、角色权限关联管理、权限缓存更新
- **PermissionService**：权限业务逻辑，包括权限CRUD、权限树构建、权限验证逻辑
- **AuthService**：认证业务逻辑，包括登录验证、JWT令牌生成、权限校验

**Mapper层：**
- UserMapper、RoleMapper、PermissionMapper
- 职责：数据库操作，SQL映射

**Entity层：**
- User、Role、Permission、UserRole、RolePermission
- 职责：数据实体映射

**DTO层：**
- LoginRequest、UserDTO、RoleDTO、PermissionDTO
- 职责：数据传输对象，封装请求和响应数据

### 核心模块

#### 1. 用户管理模块（UserManagement）
- **UserController**：用户CRUD、用户角色分配
- **UserService**：用户业务逻辑、密码加密
- **UserMapper**：用户数据访问
- **UserDetailsService实现**：Spring Security认证

#### 2. 角色管理模块（RoleManagement）
- **RoleController**：角色CRUD、角色权限分配
- **RoleService**：角色业务逻辑、权限缓存管理
- **RoleMapper**：角色数据访问

#### 3. 权限管理模块（PermissionManagement）
- **PermissionController**：权限CRUD、权限树结构
- **PermissionService**：权限业务逻辑、权限验证
- **PermissionMapper**：权限数据访问

#### 4. 安全认证模块（Security）
- **JwtAuthenticationFilter**：JWT令牌验证拦截器
- **JwtTokenProvider**：JWT令牌生成和解析
- **CustomUserDetails**：用户详情封装
- **SecurityConfig**：Spring Security配置

### 主要功能接口

#### 认证接口
- `POST /api/auth/login` - 用户登录获取Token
- `POST /api/auth/logout` - 用户登出
- `POST /api/auth/refresh` - 刷新Token

#### 用户管理接口
- `GET /api/users` - 分页查询用户列表
- `GET /api/users/{id}` - 查询用户详情
- `POST /api/users` - 新增用户
- `PUT /api/users/{id}` - 更新用户
- `DELETE /api/users/{id}` - 删除用户
- `PUT /api/users/{id}/status` - 更新用户状态
- `PUT /api/users/{id}/roles` - 分配用户角色

#### 角色管理接口
- `GET /api/roles` - 查询角色列表
- `GET /api/roles/{id}` - 查询角色详情
- `POST /api/roles` - 新增角色
- `PUT /api/roles/{id}` - 更新角色
- `DELETE /api/roles/{id}` - 删除角色
- `PUT /api/roles/{id}/status` - 更新角色状态
- `PUT /api/roles/{id}/permissions` - 分配角色权限

#### 权限管理接口
- `GET /api/permissions` - 查询权限树
- `GET /api/permissions/{id}` - 查询权限详情
- `POST /api/permissions` - 新增权限
- `PUT /api/permissions/{id}` - 更新权限
- `DELETE /api/permissions/{id}` - 删除权限

## 前端页面设计

### 主要页面组件

#### 1. 登录页面
- 用户名/手机号、密码输入
- 记住我功能
- JWT令牌存储（localStorage）
- 路由守卫保护

#### 2. 用户管理页面
- 用户列表（表格展示，支持分页）
- 新增/编辑用户（表单，包含基本信息和角色分配）
- 删除用户（二次确认）
- 用户状态切换（启用/停用）
- 角色分配（多选框或下拉树）

#### 3. 角色管理页面
- 角色列表（表格展示）
- 新增/编辑角色（基本信息、权限分配）
- 权限分配（树形勾选，支持模块、按钮、API权限）
- 角色状态管理

#### 4. 权限管理页面
- 权限树展示（树形结构）
- 新增/编辑权限（基本信息、父节点选择、权限类型）
- 权限类型标识（不同图标区分模块、按钮、API）

### 公共组件

#### 1. PermissionTree
- 权限树选择组件
- 支持多选、单选模式
- 支持权限类型过滤

#### 2. RoleSelect
- 角色选择组件
- 支持多选、单选模式
- 支持角色状态过滤

#### 3. AuthButton
- 权限控制按钮组件
- 自动根据权限显示/隐藏
- 支持多权限验证

### 状态管理

#### UserStore（Pinia）
```javascript
{
  user: Object,        // 当前用户信息
  roles: Array,        // 用户角色列表
  permissions: Array,  // 用户权限列表
  token: String,       // JWT令牌
  // Actions
  login(), logout(), refreshPermissions()
}
```

#### usePermission（组合式API）
- `hasPermission(permission)` - 检查是否有指定权限
- `hasAllPermissions(permissions)` - 检查是否拥有所有权限
- `hasAnyPermission(permissions)` - 检查是否拥有任一权限
- `hasRole(role)` - 检查是否有指定角色

## 权限控制策略

### 后端权限控制

#### 注解驱动
```java
@PreAuthorize("hasAuthority('user:query')")
@PreAuthorize("hasAnyAuthority('user:query', 'user:edit')")
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("@permissionService.hasPermission('user:edit')")
```

#### 动态权限检查
- 自定义PermissionEvaluator
- 支持复杂权限逻辑（如只能操作自己的数据）
- 权限缓存优化（Redis）

#### API接口保护
- 除了登录接口，所有API都需要JWT认证
- 权限不足返回403状态码
- 异常统一处理

### 前端权限控制

#### 路由守卫
```javascript
{
  path: '/users',
  component: UserManagement,
  meta: { requiresAuth: true, permissions: ['user:manage'] }
}
```
- 路由元信息配置权限要求
- 未授权访问跳转到403页面
- 登录状态检查

#### 指令控制
```javascript
v-auth="'user:edit'"  // 按钮权限控制
v-auth:all="['user:edit', 'user:delete']"  // 多权限
v-auth:any="['user:edit', 'user:query']"  // 任一权限
```

#### 组件级控制
- 根据权限动态渲染菜单
- 根据权限显示/隐藏功能模块
- 权限验证失败时的UI反馈

### 安全特性
- JWT过期时间控制（默认2小时）
- Token刷新机制（有效期内可刷新）
- 异常登录检测（同用户多设备登录）
- 权限变更实时生效（登出后重新登录）

## 错误处理策略

### 后端异常处理
- **GlobalExceptionHandler**：全局异常捕获
- **BusinessException**：业务异常（用户名已存在等）
- **AuthenticationException**：认证异常（登录失败）
- **AccessDeniedException**：权限异常（权限不足）
- 统一错误响应格式和错误码

### 前端错误处理
- **Axios响应拦截器**：统一处理HTTP错误
- **401**：跳转登录页，清除Token
- **403**：权限不足提示，显示友好错误信息
- **500**：服务器错误提示，记录日志
- **网络错误**：重试机制，离线提示

### 错误码规范
```
200 - 成功
400 - 请求参数错误
401 - 未认证
403 - 权限不足
404 - 资源不存在
500 - 服务器错误
1001 - 用户名已存在
1002 - 密码错误
1003 - Token过期
1004 - 权限不足
```

## 测试策略

### 单元测试
- Service层业务逻辑测试
- Mapper层数据访问测试
- 工具类测试（JWT生成、密码加密）

### 集成测试
- Controller层API测试
- 权限验证测试
- JWT令牌测试
- 数据库事务测试

### 功能测试
- 用户管理CRUD测试
- 角色权限分配测试
- 权限动态分配测试
- 登录登出流程测试

### 安全测试
- SQL注入防护测试
- XSS攻击防护测试
- Token泄露防护测试
- 权限绕过测试
- 暴力破解防护测试

### 性能测试
- 并发登录测试
- 大量用户权限查询测试
- 权限缓存效果测试
- API响应时间测试

## 实施计划

### 第一阶段：基础框架搭建
1. 数据库表创建和初始化数据
2. Spring Security配置
3. JWT令牌生成和验证
4. 基础实体类和Mapper创建

### 第二阶段：核心功能开发
1. 用户管理模块（CRUD、角色分配）
2. 角色管理模块（CRUD、权限分配）
3. 权限管理模块（CRUD、树形结构）
4. 认证授权逻辑完善

### 第三阶段：前端页面开发
1. 登录页面和认证逻辑
2. 用户管理页面
3. 角色管理页面
4. 权限管理页面
5. 公共组件开发

### 第四阶段：集成测试和优化
1. 功能测试和Bug修复
2. 性能优化
3. 安全测试
4. 文档完善

## 技术要点

### Spring Security配置
- 密码加密：BCrypt算法
- 无状态认证：JWT + Stateless Session
- CSRF防护：禁用（前后端分离）
- CORS配置：允许前端跨域访问

### JWT实现
- 签名算法：HS256
- 令牌结构：Header + Payload + Signature
- Payload内容：用户ID、用户名、角色列表、权限列表
- 过期时间：2小时

### 权限缓存
- 使用Redis缓存用户权限
- 缓存键：user:permissions:{userId}
- 缓存失效：用户权限变更时清除
- 缓存时间：30分钟

### 前端状态管理
- 使用Pinia进行状态管理
- 用户信息持久化到localStorage
- 权限信息按需加载
- 自动Token刷新机制

## 扩展性考虑

### 新增角色类型
- 在role表插入新角色记录
- 分配相应权限
- 无需修改代码

### 新增权限类型
- 在permission表插入新权限记录
- 设置permission_type和parent_id
- 自动在权限树中显示

### 权限粒度扩展
- 支持从模块级到按钮级到API级
- 支持数据级权限控制（如只能操作自己的数据）
- 支持时间级权限控制（如特定时间段才能访问）

## 安全性考虑

### 密码安全
- 使用BCrypt加密存储
- 强制密码复杂度要求
- 密码错误次数限制

### 令牌安全
- JWT签名验证
- 令牌过期机制
- 令牌刷新机制
- 令牌黑名单（登出时）

### 访问控制
- API接口权限验证
- 敏感操作二次验证
- 操作日志记录
- 异常行为检测

### 数据安全
- 敏感信息脱敏
- SQL注入防护
- XSS攻击防护
- CSRF防护

## 维护性考虑

### 代码规范
- 遵循阿里巴巴Java开发规范
- 统一的命名规范
- 完善的注释文档
- 清晰的包结构

### 日志管理
- 统一日志格式
- 关键操作日志记录
- 异常日志记录
- 日志分级和归档

### 监控告警
- 系统性能监控
- 异常告警机制
- 用户行为监控
- 安全事件监控

### 备份恢复
- 数据库定期备份
- 配置文件版本控制
- 关键数据备份
- 灾难恢复预案

## 总结

本设计文档提供了一个完整的用户管理RBAC系统解决方案，涵盖了数据库设计、后端架构、前端实现、权限控制、错误处理、测试策略等各个方面。系统采用成熟的技术栈和设计模式，确保了安全性、可扩展性和可维护性，能够满足道路交通事故救援派单系统的用户管理需求。