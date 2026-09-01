# Vue3 + SpringBoot3 前后端分离系统

## 项目简介

这是一个基于 Vue3 + SpringBoot3 + MyBatis + MySQL 的前后端分离系统框架。

## 技术栈

### 前端
- Vue 3.x
- Vite 5.x
- Vue Router 4.x
- Pinia 2.x
- Axios 1.x

### 后端
- Spring Boot 3.2.0
- Java 17
- MyBatis 3.0.3
- MySQL 8.0+
- Lombok

## 项目结构

```
d:\cursorworkspace\
├── frontend/           # 前端项目 (Vue3)
├── backend/            # 后端项目 (SpringBoot3)
├── database/           # 数据库脚本
│   └── init.sql       # 数据库初始化脚本
└── README.md          # 项目说明文档
```

## 快速开始

### 环境要求

#### 前端
- Node.js 16+
- npm 或 yarn

#### 后端
- JDK 17+
- Maven 3.6+
- MySQL 8.0+

### 数据库初始化

1. 登录MySQL:

```bash
mysql -u root -p
```

2. 执行初始化脚本:

```bash
mysql -u root -p < database/init.sql
```

### 后端启动

1. 进入后端目录:

```bash
cd backend
```

2. 修改 `src/main/resources/application.yml` 中的数据库连接信息

3. 启动项目:

```bash
mvn spring-boot:run
```

后端服务地址: http://localhost:8080

### 前端启动

1. 进入前端目录:

```bash
cd frontend
```

2. 安装依赖:

```bash
npm install
```

3. 启动开发服务器:

```bash
npm run dev
```

前端服务地址: http://localhost:5173

## 登录与 RBAC

### 默认账号

执行 `database/init.sql` 后会创建默认管理员账号：

- 用户名: `admin`
- 密码: `admin123`

### 登录接口

```bash
POST /api/auth/login
Content-Type: application/json

{"username": "admin", "password": "admin123"}
```

响应中的 `data.token` 为 JWT，后续请求需在 Header 中携带：

```
Authorization: Bearer <token>
```

### 角色与权限

系统预置四类业务角色及系统管理员角色：

| 角色代码 | 说明 |
|---------|------|
| `TRAFFIC_POLICE` | 交警，负责事故处理 |
| `DISPATCHER` | 调度员，负责派单与资源调度 |
| `TOW_DRIVER` | 拖车施救员，执行救援任务 |
| `PARKING_ADMIN` | 停车场管理员 |
| `ADMIN` | 系统管理员，拥有用户/角色/权限管理权限 |

权限基于 RBAC：用户可拥有多个角色，角色关联权限（模块/按钮/API）。JWT 登录时加载当前权限列表；**修改角色或权限后需重新登录** 才能刷新 token 中的权限。

## 项目说明

### 前端项目
- 使用 Vue 3 Composition API
- 配置了 Vue Router 路由管理
- 配置了 Pinia 状态管理
- 封装了 Axios 请求工具
- 配置了开发环境代理

### 后端项目
- Spring Boot 3.2.0
- MyBatis 持久层框架
- 统一的返回结果封装
- 示例 Controller、Entity、Mapper
- 配置了跨域支持

## 开发指南

详细的开发指南请查看各子项目的 README 文件：

- [前端开发指南](./frontend/README.md)
- [后端开发指南](./backend/README.md)

## 常见问题

### 1. 数据库连接失败
请检查 `backend/src/main/resources/application.yml` 中的数据库连接信息是否正确。

### 2. 前端跨域问题
前端已配置代理，开发环境下 `/api` 请求会自动代理到 `http://localhost:8080`。

### 3. Maven 依赖下载慢
可以配置国内镜像源（如阿里云镜像）。

## 联系方式

如有问题，请通过以下方式联系：
- 提交 Issue
- 发送邮件

## 许可证

MIT License