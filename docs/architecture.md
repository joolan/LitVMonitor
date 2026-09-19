# 软件架构

## 项目结构

```
LitVMonitor/
├── litv-monitor-server/           # Spring Boot 后端
│   ├── src/main/java/com/litv/monitor/
│   │   ├── config/                # 配置类 (Security, MyBatis, DB, Async, Jackson, TypeHandler)
│   │   ├── security/              # JWT 认证 (Filter, Token, Entry Point, UserDetailsService)
│   │   ├── entity/                # 27 个实体类
│   │   ├── mapper/                # 27 个 MyBatis-Plus Mapper
│   │   ├── executor/              # 多协议执行器 (接口 + 注册表 + 抽象基类 + 15 种协议实现)
│   │   ├── service/               # 27 个业务服务 (含 SsrfProtectionService)
│   │   ├── service/script/        # 预请求脚本引擎 (GraalJS 沙箱 + ScriptRuntime 宿主对象) + 内置签名函数
│   │   ├── controller/            # 22 个 REST 控制器
│   │   ├── dto/                   # 18 个数据传输对象
│   │   ├── enums/                 # 7 个枚举
│   │   ├── util/                  # 工具类 (DateTimeUtil, IpUtils)
│   │   └── scheduler/             # 定时任务 (Cron 调度)
│   └── src/main/resources/
│       ├── application.yml
│       └── db/
│           ├── schema.sql         # 建表语句
│           └── migration.sql      # 迁移脚本（配合 schema_version 版本表）
│
├── litv-monitor-web/              # Vue3 前端
│   ├── src/
│   │   ├── api/index.js           # Axios 封装 + 全部 API 接口
│   │   ├── utils/format.js        # 时间格式化工具 (北京时间)
│   │   ├── router/index.js        # 路由 + 鉴权守卫
│   │   ├── stores/user.js         # Pinia 用户状态
│   │   ├── stores/theme.js        # Pinia 主题状态 (6 套皮肤 + localStorage)
│   │   ├── layout/MainLayout.vue  # 侧边栏 + 顶栏布局 (含换肤入口)
│   │   ├── styles/index.scss      # 全局样式 + 主题 CSS 变量
│   │   ├── components/            # 可复用组件
│   │   │   ├── JsonSchemaEditor.vue   # JSON Schema 可视化编辑器（双模式 + 拖拽排序）
│   │   │   └── SchemaFieldRow.vue     # Schema 字段行（递归、折叠、必填标记）
│   │   └── views/                 # 20 个页面视图
│   └── vite.config.js             # Vite 配置 + API 代理
│
├── README.md
├── backend.log
└── frontend.log
```

### 架构分层

```
前端 (Vue3 + Element Plus)
  ↕ Axios (JWT Bearer Token)
Spring Security Filter Chain
  → JwtAuthenticationFilter (JWT 校验)
  → Controller (参数校验 + DTO 转换)
  → Service (业务逻辑)
  → MyBatis-Plus Mapper
  → SQLite 数据库

OkHttpClient (全局单例，代理感知)
  ↕ 代理设置（可选 HTTP/SOCKS5 代理）
  → 目标 URL
```

---

## 数据库设计

### ER 图

```
sys_user ─────────────────────────────────────
                                               
monitor ──┐                                   
           ├── group_monitor ── monitor_group   
           │                                   
           ├── execution_log                   
           ├── alert_log                       
           │                                   
           └── domain_asset ── ssl_certificate  
                   │                           
                   └── domain_ip_history        
                                               
global_variable                                
group_variable (group_id nullable)            
alert_config (旧版，向后保留)                  
alert_template (告警模板，含限频配置)           
alert_rate_limit (告警限频计数)                 
alert_silence (告警静默/维护窗口)              
audit_log (操作审计日志)                       
proxy_config (代理设置)                        
api_schema (API Schema 定义)                   
api_schema_history (Schema 变更历史)           
inspection_config (巡检配置)                   
inspection_history (巡检历史)                  
inspection_detail (巡检详情)                   
user_memo (用户备忘录)                         
```

### 表结构概览

| 表名 | 用途 | 关键字段 |
|------|------|----------|
| `sys_user` | 用户账号 | username, password(BCrypt), role, enabled |
| `monitor` | 监控项 | name, url, method, headers, body, expectedStatus, expectedText, **expectedRegex**, jsonPath, jsonExpected, **maxResponseBodySize**, variableExtractConfig, **responseTimeThreshold**, **responseTimeConsecutiveCount**, **responseTimeAlertEnabled**, **responseTimeAlertConfigIds**, **signType**, **signConfig**, **signTarget**, **signFieldName**, **preRequestScript** |
| `monitor_group` | 监控任务 | name, cronExpression, scheduleType, alertConfigIds, **running** |
| `group_monitor` | 任务-监控关联 | groupId, monitorId, sortOrder, continueOnFail |
| `global_variable` | 全局变量 | name(UNIQUE), value, description, isSecret |
| `group_variable` | 任务变量 | groupId(可NULL), name, value, scope |
| `execution_log` | 执行日志 | **url**, **executionId**, monitorId, groupId, status, statusCode, responseTime, domain, ipAddress, variableReferences, variableSettings, **schemaCheckStatus** |
| `alert_config` | 告警渠道（旧版，向后保留） | name, type(EMAIL/WEBHOOK), config(JSON) |
| `alert_template` | 告警模板 | name, triggerType, content, cooldownMinutes, enabled, **rateLimitEnabled**, **rateLimitCount**, **recoveryNotify**, **recoveryConsecutiveCount** |
| `alert_rate_limit` | 告警限频计数 | **fingerprint**(UNIQUE), **currentCount**, **windowStart**, **lastAlertSuccess** |
| `alert_log` | 告警记录 | monitorId, **monitorName**, groupId, **groupName(来源)**, **executionId**, alertConfigId, **alertConfigName**, alertType, **triggerType**, **statusCode**, **responseTime**, **errorMessage**, alertContent, status |

**告警记录状态说明**：
| 状态 | 说明 |
|------|------|
| `SENT` | 已发送，告警通过渠道成功发出 |
| `FAILED` | 发送失败，渠道返回错误 |
| `NO_CHANNEL` | 无告警渠道，仅记录日志 |
| `SUPPRESSED` | 被限频抑制，未发送通知 |
| `SILENCED` | 被静默抑制，未发送通知 |
| `CONFIG_UNAVAILABLE` | 告警渠道配置不存在或已禁用 |
| `domain_asset` | 域名证书 | domain, ipAddress, port, isAlive, sslAlertEnabled, sslAlertDaysBefore, **sslAlertOnExecute** |
| `ssl_certificate` | SSL证书 | domain, issuer, notAfter, remainingDays, isValid, **status(VALID/EXPIRED/MISMATCH)** |
| `domain_ip_history` | IP历史 | domain, ipAddress, port, firstSeenAt, lastSeenAt |
| `alert_silence` | 告警静默 | name, silenceType(ONE_TIME/RECURRING), startTime, endTime, **scheduleConfig**(JSON), applyTo, applyIds, enabled |
| `audit_log` | 审计日志 | userId, username, action, targetType, targetId, targetName, detail, **requestBody**, ipAddress |
| `proxy_config` | 代理设置 | name, proxy_type(HTTP/SOCKS5), host, port, username, password, enabled, active |
| `api_schema` | API Schema 定义 | name, description, schema_json(JSON), enabled, last_check_status, last_check_message, last_checked_at |
| `api_schema_history` | Schema 变更历史 | schema_id, change_type(ADDED/REMOVED/MODIFIED/BREAKING), change_description, old_schema(TEXT), new_schema(TEXT), created_at |
| `inspection_config` | 巡检配置 | name, monitor_ids(JSON), schedule_cron, enabled |
| `inspection_history` | 巡检历史 | config_id, status(RUNNING/SUCCESS/PARTIAL/FAILED), total_count, success_count, failed_count, **schema_change_count**, started_at, finished_at |
| `inspection_detail` | 巡检详情 | history_id, monitor_id, monitor_name, status, status_code, response_time, error_message, **schema_change_info**, created_at |
| `user_memo` | 用户备忘录 | username(UNIQUE), content(TEXT) |

### 时间字段存储规范

SQLite 无原生日期类型，所有时间字段以 **TEXT** 存储，统一格式 `yyyy-MM-dd HH:mm:ss`（空格分隔、北京时间），与 SQLite `datetime()` 函数输出一致，确保字符串比较正确。

| 组件 | 职责 |
|------|------|
| `SQLiteDateTimeHandler` | MyBatis TypeHandler：写入时 `LocalDateTime` → 空格格式字符串；读取时兼容历史 T 分隔数据 |
| `DateTimeUtil` | `now()` / `format()` 工具方法，供 SQL 拼接使用 |
| `JacksonConfig` | 控制 API 响应的 `LocalDateTime` 序列化格式 |

**历史数据修复**：Migration 41 遍历 70+ 时间列执行 `REPLACE(col, 'T', ' ')`，清除存量 T 分隔符。

---

## API 接口

所有接口返回统一格式：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

### 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/login` | 登录，返回 JWT Token |
| GET | `/auth/me` | 获取当前用户信息 |
| POST | `/auth/logout` | 登出 |

### 监控项

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/monitor/list?page=1&size=10&keyword=` | 分页列表 |
| GET | `/monitor/{id}` | 详情 |
| POST | `/monitor` | 创建 |
| PUT | `/monitor/{id}` | 更新 |
| DELETE | `/monitor/{id}` | 删除 |
| POST | `/monitor/{id}/test` | 手动测试执行 |
| POST | `/monitor/{id}/copy` | 复制监控项 |
| PUT | `/monitor/batch/status` | 批量启用/禁用 |

### 监控任务

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/group/list?page=1&size=10` | 分页列表 |
| POST | `/group` | 创建（含关联监控项） |
| PUT | `/group/{id}` | 更新 |
| DELETE | `/group/{id}` | 删除 |
| GET | `/group/{id}/monitors` | 获取任务下的监控项 |
| POST | `/group/{id}/run` | 执行任务（异步） |

### 变量

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/variable/global/list` | 全局变量分页列表 |
| GET | `/variable/global/all` | 所有全局变量（下拉选择用） |
| POST | `/variable/global` | 创建全局变量 |
| PUT | `/variable/global/{id}` | 更新全局变量 |
| DELETE | `/variable/global/{id}` | 删除全局变量 |
| GET | `/variable/group/all` | 所有任务变量 |
| POST | `/variable/group` | 创建任务变量 |
| PUT | `/variable/group/{id}` | 更新任务变量 |
| DELETE | `/variable/group/{id}` | 删除任务变量 |

### 域名证书

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/domain/list` | 域名列表 |
| PUT | `/domain/{id}` | 更新 SSL 告警配置（含执行时告警 sslAlertOnExecute） |
| DELETE | `/domain/{id}` | 删除 |
| GET | `/domain/{id}/ssl` | SSL 证书详情 |
| GET | `/domain/ssl/expiring` | 即将到期的 SSL 证书 |
| GET | `/domain/{id}/ip-history` | IP 历史记录 |

### 告警

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/alert/config/list` | 告警配置列表（旧版） |
| POST | `/alert/config` | 创建告警配置（旧版） |
| PUT | `/alert/config/{id}` | 更新告警配置（旧版） |
| DELETE | `/alert/config/{id}` | 删除告警配置（旧版） |
| POST | `/alert/config/{id}/test` | 发送测试告警（旧版，同步） |
| GET | `/alert/template/list` | 告警模板列表 |
| GET | `/alert/template/{id}` | 告警模板详情 |
| POST | `/alert/template` | 创建告警模板（默认禁用） |
| PUT | `/alert/template/{id}` | 更新告警模板 |
| PUT | `/alert/template/{id}/enable` | 启用模板（自动禁用同类型其他模板） |
| PUT | `/alert/template/{id}/disable` | 禁用模板 |
| DELETE | `/alert/template/{id}` | 删除告警模板 |
| GET | `/alert/channel/list` | 告警渠道列表 |
| GET | `/alert/channel/{id}` | 告警渠道详情 |
| POST | `/alert/channel` | 创建告警渠道 |
| PUT | `/alert/channel/{id}` | 更新告警渠道 |
| DELETE | `/alert/channel/{id}` | 删除告警渠道 |
| POST | `/alert/channel/{id}/test` | 发送测试告警（新系统，同步） |
| GET | `/alert/log/list?page=1&size=10&triggerType=&alertConfigId=&status=&executionId=&startTime=&endTime=` | 告警记录分页列表（支持触发类型、告警渠道、发送状态、执行ID、时间范围筛选） |
| GET | `/alert/log/{id}` | 告警记录详情 |

### 告警静默

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/alert/silence/list?page=1&size=10` | 静默规则分页列表 |
| GET | `/alert/silence/{id}` | 静默规则详情 |
| POST | `/alert/silence` | 创建静默规则 |
| PUT | `/alert/silence/{id}` | 更新静默规则 |
| DELETE | `/alert/silence/{id}` | 删除静默规则 |
| PUT | `/alert/silence/{id}/enable` | 启用静默规则 |
| PUT | `/alert/silence/{id}/disable` | 禁用静默规则 |

### 执行日志

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/log/list?page=1&size=10&monitorId=&groupId=&domain=&ipAddress=&status=&schemaCheckStatus=&startTime=&endTime=` | 分页列表（支持监控/任务/域名/IP/状态/Schema 校验状态/时间范围筛选） |
| GET | `/log/{id}` | 详情 |
| DELETE | `/log/{id}` | 删除 |
| DELETE | `/log/cleanup?days=30` | 清理 N 天前的日志 |

### 仪表盘

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/dashboard/overview` | 总览统计数据（含响应时间趋势） |
| GET | `/dashboard/uptime?hours=24` | Uptime 统计（1h/6h/24h/7d/30d 可用率） |
| GET | `/dashboard/schema-changes?hours=24` | Schema 检测统计（总执行/已检查/检出变更/无变更/破坏性变更，单条 SQL 聚合） |

### 公开状态页（默认需认证，可配置公开）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/status` | 状态页数据。`status_page_public=true` 或已登录用户可访问；匿名且未开启时返回 403。仅返回 `show_on_status_page=1` 的启用监控项 |

**两层控制**：
- 系统级：`security_setting.status_page_public`（默认 `false`）
- 监控项级：`monitor.show_on_status_page`（默认 `1`，Migration 46）

### API Schema 变更检测

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api-schema/list` | Schema 列表（全部） |
| GET | `/api-schema/{id}` | Schema 详情 |
| POST | `/api-schema` | 创建 Schema |
| PUT | `/api-schema/{id}` | 更新 Schema（自动记录历史） |
| DELETE | `/api-schema/{id}` | 删除 Schema |
| POST | `/api-schema/{id}/validate` | 校验 Schema（对比响应） |
| POST | `/api-schema/{id}/check` | 检测变更并记录历史 |
| GET | `/api-schema/{id}/history` | Schema 变更历史列表 |
| GET | `/api-schema/history/{historyId}/compare` | 获取某次变更的结构化对比（oldSchema、newSchema、changes） |
| POST | `/api-schema/compare` | 自定义 Schema 对比（body: oldSchema + newSchema） |

### 巡检模式

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/inspection/config/list?page=1&size=10` | 巡检配置分页列表 |
| GET | `/inspection/config/{id}` | 巡检配置详情 |
| POST | `/inspection/config` | 创建巡检配置 |
| PUT | `/inspection/config/{id}` | 更新巡检配置 |
| DELETE | `/inspection/config/{id}` | 删除巡检配置 |
| POST | `/inspection/config/{id}/run` | 立即执行巡检 |
| GET | `/inspection/history?page=1&size=10` | 巡检历史分页列表 |
| GET | `/inspection/history/{id}` | 巡检历史详情（含明细） |

### 审计日志

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/audit/log?page=1&size=20&username=&action=` | 审计日志分页列表 |

### 数据备份

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/backup/create` | 创建数据库备份 | ADMIN |
| GET | `/backup/list` | 备份文件列表 | 所有角色 |
| GET | `/backup/download?path=` | 下载备份文件 | ADMIN |
| POST | `/backup/restore` | 恢复备份（需重启） | ADMIN |
| DELETE | `/backup/delete?path=` | 删除备份文件 | ADMIN |

### 用户备忘录

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/memo` | 获取当前用户的备忘录 | 已登录 |
| POST | `/memo` | 保存当前用户的备忘录（body: `{content}`) | 已登录 |

### 版本信息

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/version` | 获取后端/前端版本号 | 公开（无需认证） |

### 代理设置

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/proxy/list` | 代理列表 | 所有角色 |
| GET | `/proxy/{id}` | 代理详情 | 所有角色 |
| POST | `/proxy` | 创建代理 | ADMIN |
| PUT | `/proxy/{id}` | 更新代理 | ADMIN |
| DELETE | `/proxy/{id}` | 删除代理 | ADMIN |
| PUT | `/proxy/{id}/activate` | 启用代理（自动停用其他） | ADMIN |
| PUT | `/proxy/deactivate` | 停用代理 | ADMIN |
| POST | `/proxy/{id}/test` | 测试代理连通性 | ADMIN |

---

## 性能与并发

### 架构概述

| 组件 | 说明 |
|------|------|
| HTTP 客户端 | 全局单例 `OkHttpClient`（连接池 50 连接，5 分钟复用），避免每次请求重建 |
| 异步执行 | `@EnableAsync` + `ThreadPoolTaskExecutor`（核心 5 线程，最大 20，队列 100） |
| 定时调度 | `@Scheduled(fixedRate=60000)` 单线程检查任务调度 |
| 数据库 | SQLite 单文件数据库（WAL 模式），HikariCP 连接池（最大 20 连接，`busy_timeout=5000`） |

### 100+ 监控项的性能分析

**单个监控执行**：
- 创建 HTTP 连接 + 发送请求 + 等待响应，耗时取决于目标 URL 响应速度（通常 100ms~5s）
- 使用全局共享 OkHttpClient，连接复用，无创建开销

**任务执行**：
- 任务内的监控项**串行执行**（按 sortOrder 排序），保证变量传递顺序
- 不同任务之间**异步并行**（`@Async` + 线程池），互不阻塞
- 100 个监控项分为 10 个任务（每任务 10 个），全部执行完 = 10 × 串行时间

**单次执行的 DB 开销优化**：
| 环节 | 优化前 | 优化后 |
|------|--------|--------|
| 变量替换 | 每次替换全量加载 global+group（~12-14 次查询） | 执行开始时加载一次（2 次查询） |
| SSL 检查（HTTPS） | 每次执行 TLS 握手 | 按域名 TTL 缓存（默认 6h） |
| 域名资产记录 | 每次 DNS + 2-4 次写 | 按域名节流（默认 10 分钟） |
| 执行日志写入 | insert + update（2 次） | 合并为一次 insert |

**瓶颈分析**：
| 场景 | 耗时估算 | 瓶颈 |
|------|----------|------|
| 100 个独立监控项，串行执行 | 100 × 平均响应时间 | HTTP 请求 I/O |
| 100 个监控项，10 个任务并行 | 10 × (10 × 平均响应时间) | 线程池大小 + HTTP 并发 |
| 100 个监控项，100 个任务并行 | 受线程池限制（max=20） | 线程池队列排队 |

**容量上限**：
- **推荐**：同时运行的任务数 ≤ 20（线程池 maxPoolSize）
- **极限**：队列容量 100，超过后由 `CallerRunsPolicy` 在调度线程中执行（阻塞调度）
- **SQLite 写入**：WAL 模式下读写并发显著改善；`busy_timeout=5000` 降低锁冲突

### 优化建议（100+ 监控项时）

1. **调整线程池**：修改 `AsyncConfig` 中 `corePoolSize`/`maxPoolSize` 以匹配并发需求
2. **合理分组**：将无依赖的监控项放入不同任务，实现并行执行
3. **日志保留**：通过 `monitor.log-retention-days` 自动清理，或调整 `monitor.log-body-max-size` 控制入库体积
4. **监控超时**：合理设置每个监控项的 timeout，避免慢请求占用线程池
5. **SSL/域名节流**：通过 `monitor.ssl-check-interval-minutes`、`monitor.domain-asset-update-interval-minutes` 调整检查频率

---

## 安全机制

### JWT 密钥生成
- **自动密钥生成**：未设置 `JWT_SECRET` 环境变量时，系统自动拼接硬编码基础密钥 + 本机 MAC 地址的 SHA-256 哈希，确保不同服务器生成不同密钥（日志仅输出警告提示设置环境变量，不泄露密钥信息）
- **自定义密钥**：生产环境建议设置 `JWT_SECRET` 环境变量使用自定义密钥

### RBAC 权限控制实现原理

系统采用前后端双重权限控制，基于 RBAC（基于角色的访问控制）模型。

#### 角色定义

| 角色 | 说明 | 后端注解 | 前端菜单 | 前端按钮 |
|------|------|----------|----------|----------|
| ADMIN | 管理员 | `hasAnyRole('ADMIN','OPERATOR')` / `hasRole('ADMIN')` | 全部 | 全部 |
| OPERATOR | 运维 | `hasAnyRole('ADMIN','OPERATOR')` | 除用户/安全/代理外 | 除用户/安全/代理外 |
| VIEWER | 只读 | 无写权限注解 | 全部菜单（只读） | 无写按钮 |

#### 后端实现链路

```
1. 登录时生成 Token
   AuthController.login()
   → JwtTokenProvider.generateToken(authentication)
   → JWT Token 中写入 claim: { sub: "admin", role: "ROLE_ADMIN", jti: "uuid" }

2. 每次请求校验
   Request → JwtAuthenticationFilter.doFilterInternal()
   → 从 Header 提取 Bearer Token
   → jwtTokenProvider.validateToken() 验证签名和过期
   → jwtTokenProvider.getRoleFromToken() 提取 role claim
   → 创建 UsernamePasswordAuthenticationToken，authorities = [SimpleGrantedAuthority(role)]
   → SecurityContextHolder.getContext().setAuthentication(auth)

3. Controller 方法级鉴权
   @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
   public Result<Void> createMonitor(@RequestBody MonitorDTO dto) { ... }

   → Spring Security AOP 拦截
   → 检查 SecurityContext 中 Authentication 的 authorities 是否包含 ROLE_ADMIN 或 ROLEOPERATOR
   → 不匹配抛出 AccessDeniedException → GlobalExceptionHandler 返回 403
```

**关键配置**：
- `SecurityConfig.java` 标注 `@EnableMethodSecurity` 启用方法级权限校验
- `JwtTokenProvider.generateToken()` 将 role 写入 JWT claim（`claim("role", role)`）
- `JwtAuthenticationFilter` 从 Token 读取 role 并注入 SecurityContext（`new SimpleGrantedAuthority(role)`）
- Spring Security 要求角色前缀 `ROLE_`，JWT 中存储 `ROLE_ADMIN`，注解中写 `hasRole('ADMIN')` 即匹配 `ROLE_ADMIN`

#### 前端实现链路

```
1. 登录成功后获取用户信息
   Login.vue → api.post('/auth/login')
   → 返回 { token, user: { id, username, role: "ROLE_ADMIN" } }
   → Pinia store 存储 token + userInfo

2. 侧边栏菜单动态渲染
   MainLayout.vue → usePermission()
   → allowedMenus = MENUS.filter(m => checkRole(role, m.roles))
   → allowedMenuGroups = MENU_GROUPS.filter(g => MENUS.some(m => m.group === g.key && allowedMenuKeys.includes(m.key)))
   → v-for 渲染 allowedMenus，role 不匹配的菜单自动过滤掉

3. 按钮显隐控制
   v-permission="'monitor:create'"
   → permissionDirective.mounted() 执行
   → checkButton(role, 'monitor:create') 检查 BUTTONS 配置
   → 无权限 → el.style.display = 'none'（隐藏 DOM）
   → watch(userStore.role) 响应式监听，角色变化时重新检查

4. 路由守卫
   router.beforeEach()
   → menuPermissionMap[to.path] 从 MENUS 配置读取该路由允许的角色
   → userStore.userInfo?.role 获取当前用户角色
   → 不匹配 → next('/dashboard') 重定向到仪表盘
```

**权限配置文件结构**（`permissions.js`）：
```
MENUS[]        — 菜单配置，每项 { path, title, roles: ['ADMIN','OPERATOR'] }
MENU_GROUPS[]  — 分组配置，用于侧边栏折叠子菜单
BUTTONS{}      — 按钮配置，键 '模块:操作'，值 { roles: [...] }
checkRole()    — 检查角色是否在允许列表中
checkButton()  — 检查按钮权限
getAllowedMenuKeys() — 获取角色有权限的菜单 key 列表
```

**扩展方式**：
- 新增菜单：MENUS 数组添加 `{ path: '/xxx', title: 'XXX', roles: ['ADMIN'] }`
- 新增按钮：BUTTONS 对象添加 `'module:action': { roles: ['ADMIN', 'OPERATOR'] }`
- 后端：Controller 方法加 `@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")`

**VIEWER 角色限制**：变量管理菜单前端排除 VIEWER（`roles: ['ADMIN','OPERATOR']`），后端 `GlobalVariableController` 所有端点加 `@PreAuthorize`，双重限制。

### 密码安全
- 用户密码字段使用 `@JsonIgnore` 注解，API 响应中不泄露密码哈希
- 密码最短 8 位，必须包含大写字母、小写字母和数字
- 修改密码需验证旧密码 + 新密码确认

### 数据安全
- **SQLite 外键约束**：通过 HikariCP `connectionInitSql` 启用外键约束，确保级联删除正常工作
- **备份路径校验**：所有备份操作（下载/恢复/删除）校验路径必须在 `backups/` 目录下，防止路径穿越攻击
- **上传文件名清洗**：备份上传文件名移除路径分隔符和目录穿越字符
- **IP 安全**：移除 `X-Forwarded-For` 信任，直接使用 `getRemoteAddr()` 获取真实 IP
- **SSRF 防护**：监控请求禁止访问 `localhost`、`127.0.0.1`、`::1`、内网 IP、`169.254.x.x`（云元数据地址），可通过 `monitor.ssrf-protection` 配置
- **SSL 验证可配置**：监控请求的 SSL 证书验证通过 `monitor.ssl-verify-disabled` 控制（默认跳过，适合自签名证书场景）
- **全局变量脱敏**：`isSecret=true` 的变量在 API 读接口返回脱敏值，不暴露明文
- **脚本引擎沙箱**：GraalVM 预请求脚本引擎使用 `HostAccess.UNTRUSTED`，禁止 `Runtime.exec()`、`ProcessBuilder` 等危险 API
- **CORS 可配置**：允许的源通过 `cors.allowed-origins` 环境变量配置（默认 localhost）
- **登录速率限制**：失败尝试达阈值后锁定账号（`SecuritySettingsService` 控制）

### 异常处理
- **全局异常处理器**：`GlobalExceptionHandler` 统一处理所有异常，返回标准化错误响应，不泄露堆栈信息
- **异常类型**：AccessDeniedException(403)、AuthenticationException(401)、ValidationException(400)、RuntimeException(500)

---

## 性能优化

### 缓存机制
- **Caffeine 本地缓存**：用于替代 ConcurrentHashMap，自动过期淘汰，防止内存泄漏
  - 告警冷却时间缓存（2小时过期，最大1000条）
  - 连续失败计数缓存（1小时过期，最大500条）
  - 响应时间告警计数缓存（1小时过期，最大500条）
  - 任务执行时间戳缓存（24小时过期，最大200条）
  - 登录失败尝试缓存（30分钟过期，最大500条）
  - 告警静默规则缓存（30秒过期，增删改即时失效）
- **执行时缓存（ConcurrentHashMap + TTL 判断）**
  - SSL 检查时间缓存（按域名，默认 6 小时）
  - 域名资产记录时间缓存（按域名，默认 10 分钟）

### 数据库优化
- **补充索引**：新增数据库索引优化查询性能
  - `user_session(user_id, active)` - 会话查询优化
  - `monitor(enabled)` - 调度器查询优化
  - `alert_log(execution_id)` - 告警关联查询优化
  - `alert_log(alert_config_id)` - 告警渠道查询优化
  - `ssl_certificate(domain_asset_id)` - 证书查询优化
  - `group_variable(group_id)` - 分组变量查询优化
  - `execution_log(schema_check_status)` - Schema 状态筛选优化（Migration 42）
  - `execution_log(executed_at, schema_check_status)` - Schema 统计复合索引（Migration 42）
- **统计查询合并**：`DashboardService.getSchemaCheckStats()` 由 8 次独立 COUNT 合并为单条 `SUM(CASE WHEN ...)` 聚合，并移除 `REPLACE()` 包装使 `executed_at` 索引生效
- **WAL 模式**：启动时 `PRAGMA journal_mode=WAL` + `synchronous=NORMAL`，读写并发提升；Hikari `busy_timeout=5000` 降低锁冲突；备份前 `wal_checkpoint(TRUNCATE)`
- **数据保留**：`DataRetentionService` 每日 03:30 清理超期执行/告警日志（`monitor.log-retention-days`）
- **入库截断**：请求/响应体超过 `monitor.log-body-max-size` 截断

### N+1 查询修复
- **MonitorService.getGroupsForMonitor()**：改为批量查询替代循环单条查询
- **DashboardService.countDistinctResponseTimeoutMonitors()**：改为 SQL COUNT(DISTINCT) 替代全表加载
- **VariableEngine**：单次监控执行只加载一次 global + group 变量，避免每字段重复全量查询

### 代码优化
- **ObjectMapper 单例**：移除热路径中的 `new ObjectMapper()`，改为 Spring 管理的单例 Bean 注入
- **日志级别**：生产环境日志级别改为 INFO，移除 SQL 日志输出

---

## 多协议执行器架构

- **接口**：`MonitorExecutor`（`getMonitorType()` + `execute(monitor, log, vars)`）
- **注册表**：`MonitorExecutorRegistry` 在启动时自动收集所有执行器并按类型注册
- **抽象基类**：`AbstractMonitorExecutor` 统一 config/host/port 解析、`openSocket`（含 SSRF 校验）、`readBytes`、失败标记
- **内置实现**：`HttpMonitorExecutor`、`PingMonitorExecutor`、`TcpMonitorExecutor`、`SshMonitorExecutor`、`TelnetMonitorExecutor`、`FtpMonitorExecutor`、`VncMonitorExecutor`、`MysqlMonitorExecutor`、`PostgresMonitorExecutor`、`RedisMonitorExecutor`、`MemcachedMonitorExecutor`、`MongoMonitorExecutor`、`ZookeeperMonitorExecutor`、`AmqpMonitorExecutor`、`MqttMonitorExecutor`
- **调度**：`ExecutionService.executeMonitor()` 按 `monitor.monitorType` 查找执行器；执行后的响应时间告警、失败告警、Schema 告警与协议无关
- **扩展方式**：新增协议只需实现一个 `MonitorExecutor` 并加 `@Component`

## 安全机制（v1.2.1）

- **JWT 密钥**：来自 `JWT_SECRET`（≥32 字符）或自动生成并持久化到 `.jwt-secret`，移除了可预测的 MAC 派生
- **权限实时校验**：`JwtAuthenticationFilter` 每次请求从数据库加载权限，降权即时生效
- **强制改密**：`must_change_password` 标记 + 过滤器 428 拦截（放行 `/auth/*`、`/status/*`、`/version`、`/user/profile`）
- **SSRF 防护**：`SsrfProtectionService` 在**连接期**校验实际解析 IP（回环/内网/CGNAT/链路本地/云元数据/IPv6）；HTTP 使用校验型 OkHttp `Dns`（覆盖重定向），其余协议在 `openSocket` 校验，告警 webhook 同样校验
- **脚本沙箱**：GraalJS `HostAccess.UNTRUSTED`，仅通过 `ProxyExecutable` 暴露白名单 `crypto`/`util` 宿主函数（`ScriptRuntime`），不授予任意 Java 访问

## 数据库迁移

- **版本表**：`schema_version(version, applied_at)`，破坏性迁移（1/36/38/40）加版本守卫，避免每次启动拷贝-删除-重建
- **alert_log 索引**：重建表后重新创建 `sent_at`/`trigger_type`/`monitor_id`/`execution_id`/`alert_config_id` 索引
- **PRAGMA**：通过 JDBC URL 下发 `busy_timeout=5000&journal_mode=WAL&synchronous=NORMAL&foreign_keys=on`，连接池每个连接生效
