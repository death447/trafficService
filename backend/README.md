# Backend - SpringBoot3 + MyBatis

## 项目简介

基于 SpringBoot3 + MyBatis + MySQL 的后端项目框架。

## 技术栈

- Spring Boot 3.2.0
- Java 17
- MyBatis 3.0.3
- MySQL 8.0+
- Lombok

## 目录结构

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/example/backend/
│   │   │   ├── controller/    # 控制器层
│   │   │   ├── service/       # 服务层
│   │   │   ├── mapper/        # MyBatis Mapper接口
│   │   │   ├── entity/        # 实体类
│   │   │   ├── common/        # 公共类
│   │   │   └── BackendApplication.java  # 启动类
│   │   └── resources/
│   │       ├── mapper/        # MyBatis XML映射文件
│   │       └── application.yml # 应用配置
└── pom.xml                   # Maven配置
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+

### 数据库配置

1. 创建数据库并执行SQL脚本:

```bash
mysql -u root -p < ../database/init.sql
```

2. 修改 `application.yml` 中的数据库连接信息:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/vue_springboot_system?...
    username: your_username
    password: your_password
```

### 运行项目

#### 使用Maven命令运行

```bash
mvn spring-boot:run
```

#### 使用IDE运行

直接运行 `BackendApplication.java` 中的 `main` 方法。

#### 打包运行

```bash
mvn clean package
java -jar target/backend-1.0.0.jar
```

服务访问地址: http://localhost:8080

## API接口

### 测试接口

- GET `http://localhost:8080/api/hello` - 测试接口

## 配置说明

### application.yml 配置项

```yaml
server:
  port: 8080                    # 服务端口

spring:
  application:
    name: backend
  datasource:                   # 数据源配置
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://...
    username: root
    password: root

mybatis:                        # MyBatis配置
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.example.backend.entity
  configuration:
    map-underscore-to-camel-case: true  # 下划线转驼峰
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # SQL日志
```

## 开发建议

1. 使用 Lombok 简化代码
2. 遵循 RESTful API 设计规范
3. 使用统一的返回结果包装类 `Result<T>`
4. Controller 只处理请求和响应，业务逻辑放在 Service 层
5. 使用 MyBatis 注解或 XML 文件编写 SQL

## 常用命令

- `mvn clean` - 清理编译文件
- `mvn compile` - 编译项目
- `mvn test` - 运行测试
- `mvn package` - 打包项目
- `mvn spring-boot:run` - 运行项目

## 项目结构说明

- **controller**: 处理HTTP请求，调用Service层
- **service**: 业务逻辑处理，调用Mapper层
- **mapper**: 数据访问层，使用MyBatis操作数据库
- **entity**: 数据库实体类
- **common**: 公共类，如统一返回结果类等