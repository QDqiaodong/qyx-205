# 冷链物流中转仓临时置物架货位编码配对管理系统

## 项目简介

本系统用于冷链物流中转仓的置物货架与货位编码配对管理，核心功能包括：

- 货架基础建档（编号、承重、所属库区）
- 货位编码初始配对绑定
- 货位编码解绑与重分配，留存变更记录
- 货位编码反向检索货架
- 批量导出全仓编码配对对照表

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
