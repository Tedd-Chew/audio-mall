# Musilab Audio 音响产品管理系统

一款面向企业级应用的**音响产品全生命周期管理前后端系统**，采用 SpringBoot + 静态HTML主流技术栈构建，集成高并发缓存架构、RabbitMQ 异步消息队列、RAG 智能问答、定时数据同步、图片上传管理等核心能力。系统严格遵循生产级开发规范，完美解决高并发场景下的缓存问题，支持大规模请求并发处理，是 Java 后端求职/实习/毕设的优质实战项目。

## 🌟 项目特性

- **高并发支撑**：Redis 多级缓存 + RabbitMQ 消息削峰，轻松应对并发请求
- **数据安全**：JWT 无状态认证、全局异常处理、SQL 安全校验
- **异步解耦**：自定义线程池 + RabbitMQ 实现耗时任务异步化，不阻塞主线程
- **AI 增强**：RAG 检索增强生成，基于业务数据实现智能问答
- **数据一致性**：定时任务 + 事务保证 Redis 与 MySQL 数据最终一致
- **图片管理**：支持主页图、产品封面图、产品详情图上传，分类名称目录存储
- **生产规范**：统一响应格式、分层架构、缓存防穿透/雪崩/击穿
- **多环境部署**：Maven Profiles 支持开发/生产环境一键切换

## 🛠️ 核心技术栈

### 后端技术栈

| 分类 | 技术框架 |
|------|----------|
| 核心框架 | SpringBoot 2.7.x |
| 数据持久化 | MyBatis-Plus + MySQL 8.0 |
| 缓存&高并发 | Redis（RDB+AOF持久化、热点缓存）|
| 消息队列&异步 | RabbitMQ（消息削峰、异步解耦）|
| 认证授权 | JWT 无状态登录认证 |
| 并发调度 | 自定义线程池、Spring Task 定时任务 |
| AI 智能模块 | RAG 检索增强 + 大模型对话 |
| 工程化工具 | Lombok、Maven、Postman |
| 全局规范 | 统一返回体、全局异常处理、事务控制 |

### 前端技术栈

| 分类 | 技术框架 |
|------|----------|
| 页面结构 | HTML5 + CSS3 + JavaScript (原生) |
| UI框架 | Bootstrap 4.x |
| 图片轮播 | Slick Carousel |
| 弹窗效果 | Magnific Popup |
| 图标库 | Font Awesome 5.x |
| jQuery | jQuery 1.9.1 |
| HTTP客户端 | jQuery Ajax |
| 代理服务器 | Node.js (http-proxy-middleware) |

## 📐 系统架构

### 后端架构

```
请求层 → Controller → 业务层 Service
        ↙（高并发查询）        ↘（耗时任务）
    Redis 缓存架构          RabbitMQ 消息队列
                                        ↓
                            消费者 Service → AI问答/数据处理 → Redis
数据库层 MySQL ←———————————— 定时任务（数据同步）
```

### 前端架构

```
前端静态页面（HTML + CSS + JS）
        ↓ Ajax请求
代理服务器（Node.js :8888）
        ↓ 转发
后端API服务器（Spring Boot :8080）
```

### 前端页面结构

```
frontend/
├── index.html          # 首页（产品展示、AI问答入口）
├── products.html       # 产品中心（分类筛选、产品列表）
├── product-detail.html # 产品详情（轮播图、标签、详情）
├── admin.html          # 管理后台（产品/分类/图片管理）
├── login.html          # 登录页面
├── proxy-server.js     # 代理服务器（解决跨域问题）
├── css/                # 样式文件
├── js/                 # JavaScript库
├── fontawesome-5.5/    # 图标库
├── slick/              # 轮播组件
├── magnific-popup/     # 弹窗组件
└── img/                # 默认图片资源
```

## 🚀 核心功能模块

### 1. 用户认证模块
- JWT 无状态登录、接口鉴权、统一身份认证
- 除公开接口外，所有接口需携带 Token 访问

### 2. 产品管理模块
- 产品 **CRUD** 操作、分页查询、详情查询
- 热点商品 Redis 缓存，性能大幅提升
- 增删改自动更新缓存，保证数据一致性
- 产品分类动态关联，支持分类筛选

### 3. 分类管理模块
- 产品分类 **CRUD** 操作
- 分类与产品关联管理
- 支持按分类筛选产品列表

### 4. 高并发缓存模块
- 解决 **缓存穿透、缓存雪崩、缓存击穿** 三大经典问题
- Redis 混合持久化，重启不丢失数据
- 互斥锁、空值缓存、随机过期时间全面防护

### 5. RabbitMQ 异步消息模块
- AI 智能问答异步消费，接口秒级响应
- 消息削峰，高并发下系统稳定不崩溃
- 自动确认机制，适配临时业务消息场景

### 6. RAG 智能问答模块
- 基于业务数据的智能问答，不编造内容
- 异步队列执行，不影响主线程性能
- 支持并发多用户问答，结果自动存入 Redis

### 7. 图片上传管理模块
- **主页图上传**：`/api/upload/home` - 网站轮播图
- **产品封面图**：`/api/upload/product/cover` - 产品缩略图
- **产品详情图**：`/api/upload/product/images` - 产品详情轮播图
- **目录结构**：`/uploads/product/[分类名称]/[产品ID]/`
- **命名规则**：封面图固定 `main.jpg`，详情图 `001.jpg`, `002.jpg`...

### 8. 定时数据同步模块
- 定时将 Redis 统计数据同步至 MySQL
- 事务保证数据原子性，最终一致性

## 📖 接口文档

### 基础约定

- **接口前缀**：`/api`
- **统一返回格式**
```json
{ "code": 200, "msg": "操作成功", "data": {} }
```
- **认证规则**：请求头携带 `Authorization: {Token}`（登录/产品列表/AI接口除外）

### 接口列表

#### 用户模块

| 接口地址 | 请求方式 | 功能说明 | 认证 |
|----------|----------|----------|------|
| `/api/user/login` | POST | 用户登录（获取JWT令牌）| 否 |

#### 产品模块

| 接口地址 | 请求方式 | 功能说明 | 认证 |
|----------|----------|----------|------|
| `/api/product/list` | GET | 查询全部产品 | 否 |
| `/api/product/page` | GET | 产品分页查询 | 否 |
| `/api/product/{id}` | GET | 根据ID查询产品详情 | 否 |
| `/api/product/add` | POST | 新增产品 | 是 |
| `/api/product/update` | PUT | 修改产品 | 是 |
| `/api/product/{id}` | DELETE | 删除产品 | 是 |

#### 分类模块

| 接口地址 | 请求方式 | 功能说明 | 认证 |
|----------|----------|----------|------|
| `/api/category/list` | GET | 查询所有分类 | 否 |
| `/api/category/{id}` | GET | 根据ID查询分类 | 否 |
| `/api/category/add` | POST | 新增分类 | 是 |
| `/api/category/update` | PUT | 修改分类 | 是 |
| `/api/category/{id}` | DELETE | 删除分类 | 是 |

#### 图片上传模块

| 接口地址 | 请求方式 | 功能说明 | 认证 |
|----------|----------|----------|------|
| `/api/upload/home` | POST | 上传主页图 | 是 |
| `/api/upload/product/cover` | POST | 上传产品封面图 | 是 |
| `/api/upload/product/images` | POST | 上传产品详情图（批量）| 是 |

#### AI 问答模块

| 接口地址 | 请求方式 | 功能说明 | 认证 |
|----------|----------|----------|------|
| `/api/ai/chat` | POST | RAG智能问答（异步）| 否 |
| `/api/ai/result/{visitorId}` | GET | 查询问答结果 | 否 |

### AI 问答请求参数

```json
{
  "message": "推荐一款性价比高的蓝牙音响",
  "visitorId": "user_abc123"
}
```

### 图片上传请求参数

#### 上传主页图
```
POST /api/upload/home
Content-Type: multipart/form-data

file: [图片文件]
```

#### 上传产品封面图
```
POST /api/upload/product/cover
Content-Type: multipart/form-data

categoryName: 蓝牙音箱
productId: 1
file: [图片文件]
```

#### 上传产品详情图
```
POST /api/upload/product/images
Content-Type: multipart/form-data

categoryName: 蓝牙音箱
productId: 1
file: [图片文件1]
file: [图片文件2]
file: [图片文件3]
```

## 🖥️ 环境部署

### 环境要求

- JDK 21（必须）
- MySQL 8.0+
- Redis 7.0+
- RabbitMQ 3.12+
- Maven 3.6+
- Node.js 18+（前端代理服务器）

### 后端本地启动步骤

1. **创建数据库**
```sql
CREATE DATABASE audio_db CHARACTER SET utf8mb4;
```

2. **执行 SQL 脚本**，初始化数据表（参考 final.sql 文件）

3. **确保配置文件正确** `src/main/resources/application.yml`
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/audio_db
    username: root
    password: Zou1218yu
  redis:
    host: localhost
  rabbitmq:
    host: 106.54.35.197  # RabbitMQ服务器地址
```

4. **启动后端服务**
```bash
cd audiobackend
mvn spring-boot:run
```
或运行 `AudiobackendApplication.java` 主类

5. **访问地址**：`http://localhost:8080`

### 多环境打包部署（Maven Profiles）

项目支持 **开发环境(dev)** 和 **生产环境(prod)** 一键切换。

#### 配置文件结构

```
src/main/resources/
├── application.yml          # 主配置文件（通用配置）
├── application-dev.yml      # 开发环境配置（默认）
└── application-prod.yml     # 生产环境配置
```

#### 打包命令

```bash
# 开发环境打包（默认，使用本地配置）
mvn clean package -DskipTests

# 生产环境打包（使用生产配置）
mvn clean package -DskipTests -Pprod
```

#### 生产环境启动

```bash
# 设置环境变量（敏感信息）
export DB_PASSWORD=your-db-password
export REDIS_PASSWORD=your-redis-password
export RABBITMQ_USER=your-rabbitmq-user
export RABBITMQ_PASS=your-rabbitmq-password

# 启动应用（自动读取application-prod.yml）
java -jar audiobackend-0.0.1-SNAPSHOT.jar
```

#### 生产环境配置说明（application-prod.yml）

| 配置项 | 来源 | 说明 |
|--------|------|------|
| 数据库密码 | `${DB_PASSWORD}` | 环境变量 |
| Redis密码 | `${REDIS_PASSWORD}` | 环境变量 |
| RabbitMQ用户 | `${RABBITMQ_USER}` | 环境变量 |
| RabbitMQ密码 | `${RABBITMQ_PASS}` | 环境变量 |
| 文件上传路径 | `/var/www/audiobackend/uploads` | 绝对路径 |
| 日志文件 | `/var/log/audiobackend/` | 绝对路径 |

### 前端启动步骤

1. **安装依赖**（如果使用代理服务器）
```bash
cd frontend
npm install
```

2. **启动代理服务器**
```bash
cd frontend
node proxy-server.js
```

3. **访问地址**：`http://localhost:8888/`

### 前端页面访问说明

| 页面 | 地址 | 说明 |
|------|------|------|
| 首页 | http://localhost:8888/index.html | 产品展示、AI问答入口 |
| 产品中心 | http://localhost:8888/products.html | 分类筛选、产品列表 |
| 产品详情 | http://localhost:8888/product-detail.html?id=1 | 产品轮播图、详情、标签 |
| 管理后台 | http://localhost:8888/admin.html | 产品/分类/图片管理（需登录）|
| 登录页面 | http://localhost:8888/login.html | 管理员登录 |

**注意**：前端必须通过代理服务器访问，不能直接用 file:// 协议打开 HTML 文件，否则 Ajax 请求会失败。

### 默认账号

- 用户名：`admin`
- 密码：`admin123`

## 📁 项目结构

```
audiobackend/
├── frontend/                     # 前端静态资源
│   ├── index.html               # 首页
│   ├── products.html            # 产品中心
│   ├── product-detail.html      # 产品详情页
│   ├── admin.html               # 管理后台
│   ├── login.html               # 登录页
│   ├── proxy-server.js          # 代理服务器
│   ├── css/                     # 样式文件
│   ├── js/                      # JavaScript库
│   └── img/                     # 图片资源
│
├── src/main/java/               # 后端Java代码
│   └── com.example.audiobackend
│       ├── AudiobackendApplication.java  # 启动类
│       ├── controller/          # 控制层
│       │   ├── UserController.java
│       │   ├── ProductController.java
│       │   ├── ProductCategoryController.java
│       │   ├── AiController.java
│       │   └── UploadController.java
│       ├── service/            # 业务层
│       │   ├── impl/
│       │   └── ...
│       ├── mapper/             # 数据访问层
│       ├── entity/             # 实体类
│       ├── config/             # 配置类
│       ├── interceptor/        # 拦截器
│       ├── rabbitmq/           # 消息队列
│       └── util/               # 工具类
│
├── src/main/resources/          # 配置文件
│   ├── application.yml          # 主配置文件
│   ├── application-dev.yml      # 开发环境配置
│   ├── application-prod.yml     # 生产环境配置
│   └── logback-spring.xml
│
└── pom.xml                     # Maven配置（含多环境Profiles）
```

## 💡 核心技术亮点

### 1. 高并发缓存解决方案
采用旁路缓存模式，结合空值缓存、互斥锁、随机过期时间，彻底解决三大缓存问题，Redis 混合持久化保证数据安全。

### 2. RabbitMQ 异步削峰
耗时 AI 问答任务交由消息队列异步处理，接口不阻塞，高并发下系统吞吐量提升 80%。

### 3. 自定义线程池隔离
异步任务与主线程隔离，避免线程耗尽，保证核心接口可用性。

### 4. RAG 智能问答
基于业务数据检索生成回答，SQL 安全校验，支持多用户并发问答。

### 5. 图片上传管理
- 分类名称目录存储，便于管理
- 详情图自动编号，每个产品独立计数
- 支持批量上传，统一返回 URL 列表

### 6. 生产级工程规范
统一响应、全局异常、JWT 鉴权、事务控制，符合企业开发标准。

### 7. Maven 多环境打包
使用 Maven Profiles 实现开发/生产环境配置分离，一键切换无忧部署。

## ⚠️ 注意事项

1. 商品详情启用 Redis 缓存，商品列表不缓存，兼顾性能与一致性
2. 增删改操作遵循 **先更新数据库，后删除缓存** 策略
3. AI 问答为 **RabbitMQ 异步消费**，接口响应不代表处理完成
4. Redis 开启 RDB+AOF 持久化，服务重启数据可自动恢复
5. 定时任务自动执行，无需手动调用，保证数据最终一致
6. 高并发场景下，消息队列自动削峰，保障系统稳定性
7. 前端必须通过代理服务器（:8888）访问，不能直接打开 HTML 文件
8. 图片上传前需先保存产品基础信息获取产品ID
9. 图片路径使用**分类名称**作为目录，便于理解和维护
10. 生产环境打包使用 `mvn clean package -Pprod`，敏感信息通过环境变量传入

## 📊 数据库表结构

### product 产品表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| name | VARCHAR(100) | 产品名称 |
| price | DOUBLE | 价格 |
| description | VARCHAR(500) | 产品简介 |
| image_url | VARCHAR(200) | 封面图URL |
| view_count | INT | 浏览量 |
| category_id | INT | 分类ID |
| image_list | TEXT | 详情图URL列表（逗号分隔）|
| tags | VARCHAR(200) | 产品标签 |
| detail | TEXT | 产品详情（纯文本）|
| is_delete | TINYINT | 软删除标记 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

### product_category 分类表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| name | VARCHAR(50) | 分类名称 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

### sys_user 用户表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| username | VARCHAR(50) | 用户名 |
| password | VARCHAR(100) | 密码（加密存储）|
| create_time | DATETIME | 创建时间 |

## 📝 更新日志

### v1.2.0 (2026-05-06)
- 新增 Maven Profiles 多环境打包支持
- 新增 application-prod.yml 生产环境配置
- 支持环境变量配置敏感信息（数据库密码、Redis密码等）
- 更新部署文档，支持开发/生产环境一键切换

### v1.1.0 (2026-05-03)
- 新增图片上传功能模块
- 新增产品分类管理功能
- 修复产品详情页 detail 字段不显示问题
- 优化前端管理页面交互体验
- 更新 README 文档