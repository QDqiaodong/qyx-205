# 冷链物流中转仓临时置物架货位编码配对管理系统

## 项目简介

本系统用于冷链物流中转仓的置物货架与货位编码配对管理，核心功能包括：

- 货架基础建档（编号、承重、所属库区）
- 货位编码初始配对绑定
- 货位编码解绑与重分配，留存变更记录
- 货位编码反向检索货架
- 批量导出全仓编码配对对照表
- **库区交接班（白班/夜班换人）**：按库区必检三项（外观、保温门帘、铅封号）逐项勾选，
  填接班人姓名与交班说明后才能交班；漏勾、铅封号空着、接班人跟当班人同名一律交不出去；
  交班后整班冻结；同一库区上一班未交完时禁止开新班

> 交接班子系统只负责库区点检与交班台账，不修改货位编码如何配对，也不修改托盘承重/能否上架。

## 技术栈

- **前端**: Vue 3 + Vite + Element Plus + TypeScript
- **后端**: Spring Boot 3.3 + JDK 17 + Maven + JPA
- **数据库**: MySQL 8.0
- **缓存**: Redis 7
- **容器化**: Docker + Docker Compose

## 端口配置

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端 | 8125 | 前端页面访问 |
| 后端 | 8135 | REST API |
| MySQL | 3351 | 数据库 |
| Redis | 6424 | 缓存 |

## 快速启动

### 方式一：使用启动脚本

```bash
chmod +x start.sh
./start.sh
```

### 方式二：手动启动

```bash
docker compose up -d --build
```

## 访问地址

- **前端页面**: http://localhost:8125
- **后端API**: http://localhost:8135/api/shelf

## 项目结构

```
qyx-205/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/coldchain/
│   │   ├── controller/         # REST API控制层
│   │   ├── service/impl/       # 业务逻辑层
│   │   ├── repository/         # 数据访问层
│   │   ├── entity/             # 实体类
│   │   └── dto/                # 请求/响应DTO
│   ├── Dockerfile              # 后端Docker镜像（分层缓存）
│   ├── pom.xml                 # Maven配置
│   └── settings.xml            # Maven镜像配置
├── frontend/                   # Vue3 前端
│   ├── src/                    # 源代码
│   │   ├── api/                # API接口
│   │   └── components/         # 组件
│   ├── Dockerfile              # 前端Docker镜像（分层缓存）
│   ├── nginx.conf              # Nginx配置
│   └── package.json            # 依赖配置
├── docker-compose.yml          # 容器编排
├── .env                        # 环境变量配置
├── start.sh                    # 启动脚本
├── .gitignore                  # Git忽略配置
└── README.md                   # 项目说明
```

## 开发模式

### 后端开发

```bash
cd backend
mvn spring-boot:run
```

### 前端开发

```bash
cd frontend
npm install
npm run dev
```

## API接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/shelf` | POST | 创建货架 |
| `/api/shelf` | GET | 查询所有货架 |
| `/api/shelf/{id}` | GET | 查询单个货架 |
| `/api/shelf/{id}` | DELETE | 删除货架 |
| `/api/shelf/bind-code` | POST | 绑定货位编码 |
| `/api/shelf/unbind-code/{shelfId}` | POST | 解绑货位编码 |
| `/api/shelf/reassign-code` | POST | 重分配货位编码 |
| `/api/shelf/search/by-code` | GET | 按编码反向检索 |
| `/api/shelf/export` | GET | 导出配对对照表 |

### 库区交接班 `/api/shift`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/shift/zones` | GET | 库区总览（按库区反查当前当班人/进行中班ID/上一班接班人） |
| `/api/shift/open` | POST | 开班（库区、白班/夜班、当班人）；同库区上一班未交班返回 409 |
| `/api/shift/{id}` | GET | 班次详情（含三项必检勾选明细、铅封号、交班说明） |
| `/api/shift/zone/{zone}` | GET | 某库区全部班次 |
| `/api/shift/logs` | GET | 交班流水（仅已交班班次） |
| `/api/shift/check` | POST | 勾选/取消必检项；已交班班次拒绝 |
| `/api/shift/seal` | POST | 登记/修改铅封号；进行中不许清空，已交班拒绝 |
| `/api/shift/handover` | POST | 交班；勾齐+铅封号非空+接班人非空且不同于当班人，否则拒绝 |

## 交接班业务规则

1. **交班前置**：外观、保温门帘、铅封三项必检必须全部勾选，铅封号已登记，接班人姓名非空，
   且接班人不能与当班人是同一个人；交班说明把口头交代落到纸面。
2. **三处对齐同一班**：交班成功后，点检勾选（班次详情）、交班流水（`/logs`）、
   库区总览反查当班人（`/zones`）三处以同一 `shiftId` 对齐；数据全部落库，关闭页面重开仍一致。
3. **交班后冻结**：已交班的班不能再改任何勾选，铅封号既不能清空也不能改成别的号。
4. **同库区串行开班**：同一库区有进行中的班时开新会被拦下，先开的班原样不动；
   由 `shift_zone_lock` 行级悲观锁串行化，并由“进行中库区唯一”部分唯一索引在数据库层兜底。
   不同库区互不影响，可各自开班。
5. **职责边界**：交接班只写 `shift_*` 三张表，不提供任何修改货位编码配对、托盘承重/上架的入口。
