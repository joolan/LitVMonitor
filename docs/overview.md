# 项目说明

## 项目简介

LitVMonitor 是一个轻量级多协议监控系统，无需安装代理，支持变量提取与传递（监控间数据联动）、SSL证书检测、域名证书管理、API签名、预请求脚本、代理设置、多渠道告警（邮件/Webhook/钉钉/企业微信/飞书）、Uptime统计、公开状态页、API Schema变更检测、巡检模式、仪表盘自动刷新与备忘录和版本展示。

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.2.5 |
| 安全 | Spring Security | 6.2.4 |
| ORM | MyBatis-Plus | 3.5.5 |
| 数据库 | SQLite | 3.45.1 |
| HTTP 客户端 | OkHttp | 4.12.0 |
| JSONPath | json-path | 2.8.0 |
| JS 脚本引擎 | GraalJS | 24.1.1 |
| JWT | jjwt | 0.12.5 |
| 邮件 | Spring Boot Mail | 3.2.5 |
| 工具库 | Hutool | 5.8.32 |
| 缓存 | Caffeine | 3.1.8 |
| 前端框架 | Vue 3 | 3.5 |
| UI 组件库 | Element Plus | 2.14 |
| 图表 | ECharts | 5.6 |
| 构建工具 | Vite | 5.4 |
| HTTP 客户端 | Axios | 1.20 |
| 状态管理 | Pinia | 2.3 |
| 时间处理 | dayjs | 1.11 |

---

## 功能清单

### 1. 用户系统
- JWT 认证，24 小时 Token 有效期，Token 包含 `jti` 用于会话追踪
- 三种角色：ADMIN（管理员）、OPERATOR（运维）、VIEWER（仅查看）
- **RBAC 权限控制**：JWT Token 内嵌角色，Controller 写操作通过 `@PreAuthorize` 限制
- **安全机制**：禁用/删除的用户无法登录（`CustomUserDetailsService` 每次请求验证用户状态），即使 Token 未过期也会被拒绝
- **Admin 账号保护**：admin 账号不能被删除、禁用或修改角色
- **个人资料修改**：已登录用户可修改昵称、邮箱、密码（需验证旧密码）
- 默认账户：`admin`，初始密码随机生成并仅在启动日志输出一次（可用 `ADMIN_INIT_PASSWORD` 预设），首次登录强制改密

### 用户角色权限矩阵

| 操作 | ADMIN | OPERATOR | VIEWER |
|------|-------|----------|--------|
| 查看监控/日志/变量/域名/告警/状态页 | ✅ | ✅ | ✅ |
| 创建/编辑/删除 监控/任务/变量/告警 | ✅ | ✅ | ❌ |
| 手动执行监控/任务/巡检 | ✅ | ✅ | ❌ |
| 清理日志 | ✅ | ✅ | ❌ |
| 创建/恢复/删除备份 | ✅ | ❌ | ❌ |
| 下载备份 | ✅ | ❌ | ❌ |
| 用户管理（CRUD） | ✅ | ❌ | ❌ |
| 安全设置（IP/锁定/会话） | ✅ | ❌ | ❌ |
| Schema/报告/巡检管理 | ✅ | ✅ | ❌ |
| 修改个人资料 | ✅ | ✅ | ✅ |

### 2. 监控项管理
- 创建/编辑/删除 HTTP 监控项
- 支持 GET/POST/PUT/DELETE 方法
- **Postman 风格请求配置**：
  - POST/PUT 支持多种 Body 类型：none / JSON / Form Data / XML / Raw Text
  - JSON/XML/Raw Text 模式：等宽字体代码编辑器，支持 `{{variable}}` 变量
  - Form Data 模式：Key-Value 表单编辑器，支持变量，自动转换为请求体
- 可配置请求头（支持变量）
- 响应验证：状态码、包含文本、JSONPath 表达式匹配、**正则表达式匹配**
- **响应体大小监控**：可配置响应体大小上限（字节），超过则标记为失败
- **监控项复制**：一键复制现有监控项配置
- **变量赋值**：从响应中提取值存入全局/任务变量（下拉选择变量 + JSONPath + 默认值）
- **响应时间告警**：配置阈值 + 连续触发次数，超阈值连续 N 次后自动告警
- 手动测试执行，实时查看结果
- **注意**：单独创建的监控项只能手动点击"测试"执行，不会自动定时运行；需添加到"监控任务"中并配置执行计划才会自动执行

### 3. 监控任务
- 将多个监控项组合成有序执行链
- 每个监控项可配置：排序、失败是否继续
- 任务执行时自动跳过禁用状态的监控项
- **关联监控项**：任务列表显示关联的监控项数量，点击数量可查看详情（名称、排序、失败继续）
- **Cron 调度执行**（配置后才会自动执行）：
  - 固定间隔：每 N 分钟执行一次
  - 每天执行：指定时间点
  - 每周执行：指定星期 + 时间点
- 任务告警：失败阈值 + 告警通道选择
- 异步执行，不阻塞 HTTP 请求

### 4. 变量系统
- **全局变量**：持久化存储，所有任务和监控项共享，可在管理页面编辑值
- **任务变量**：任何监控项均可写入任意任务变量，每次任务运行时重置，不同任务之间相互隔离
- **内置变量**（即时生成，每次请求重新计算，北京时间）：
  - `{{env.timestamp}}` / `{{env.timestamp_s}}` — 当前时间戳（毫秒/秒）
  - `{{env.time}}` / `{{env.date}}` / `{{env.datetime}}` / `{{env.datetime_ms}}` — 北京时间各格式
  - `{{env.unix}}` — Unix 时间戳（秒）
  - `{{env.uuid}}` / `{{env.uuid_short}}` — UUID / 8位短UUID
  - `{{env.random}}` / `{{env.nonce}}` — 0~999999随机数 / 32位随机字符串
- 变量替换语法：`{{scope.name}}`，如 `{{global.baseUrl}}`、`{{group.token}}`
- 变量不存在时替换为空字符串
- **变量赋值来源**（三种）：
  - **响应体**（默认）：从响应 Body 中用 JSONPath 提取值
  - **响应头**：从响应 Header 中按名称提取值（大小写不敏感）
  - **Cookie**：从 `Set-Cookie` 响应头中按 Cookie 名称提取值
- **变量追踪**：执行日志记录变量引用（替换前→替换后）和变量设置（来源:路径 => 提取值）

### 5. 域名证书
- 每次监控执行自动发现并记录目标域名和 IP
- HTTPS 目标自动检测 SSL 证书，记录证书详情
- IP 历史记录：追踪同一域名的所有历史 IP
- SSL 剩余天数展示（列表中直接显示，带颜色标签）
- **证书状态**：VALID（有效）/ EXPIRED（已过期）/ MISMATCH（证书错误）
- **域名匹配校验**：通过证书 SAN（含通配符 `*.domain` 单级匹配）校验证书是否属于该域名，不匹配则标记为"证书错误"
- **自动汇集**：无需手动添加，监控项测试时自动记录

### 6. SSL 证书监控
- 定时检查（每日 9:00）
- 可配置告警阈值：到期前 N 天告警、到期前 1 天立即告警
- **证书错误告警**：证书与域名不匹配时同样触发告警
- **执行时告警**：开启后，监控项/监控任务执行中若发现证书已过期或证书错误，立即触发告警并写入告警记录
- 支持多告警通道（新系统 AlertChannel + 旧系统 AlertConfig）
- **告警记录**：触发 SSL 告警时，无论是否勾选告警渠道，都会写入告警记录（无渠道则记录 `NO_CHANNEL`）

### 7. 告警系统

#### 7.0 告警处理流程（优先级从高到低）

每次告警触发时，系统按以下顺序依次判断：

```
1. 恢复通知（最高优先级）
   ├─ 条件：监控项从失败变为成功，且之前有告警记录
   └─ 结果：直接发送恢复通知，重置限频计数

2. 静默检查
   ├─ 条件：当前时间在静默规则的时间窗口内
   ├─ 注意：静默期间不计入限频配额
   └─ 结果：抑制通知，记录 SILENCED 状态

3. 冷却检查
   ├─ 条件：距上次发送未超过模板冷却时间
   ├─ 注意：冷却期间不计入限频配额
   └─ 结果：抑制通知，记录 SUPPRESSED 状态

4. 限频检查
   ├─ 条件：相同告警累计次数 >= 模板配置的最大告警次数
   ├─ 计数维度：监控项按monitorId，任务按groupId，SSL按domain
   ├─ 注意：只有实际发送的告警才累计限频次数
   └─ 结果：抑制通知，记录 SUPPRESSED 状态

5. 正常发送
   ├─ 通过所有检查
   ├─ 限频计数 +1
   └─ 逐渠道发送，记录 SENT/FAILED 状态
```

**关键设计**：
- 恢复通知优先级最高，恢复时发送后重置限频计数
- 静默和冷却期间的告警不计入限频配额，只有实际发送的告警才累计限频次数
- 限频与冷却时间独立计算：冷却时间控制同类告警最小间隔，限频控制最大发送次数

#### 7.1 告警模板（按触发类型配置）
- **模板管理**：独立菜单"告警模板"，按触发类型配置消息模板
- **模板类型**：系统预置（初始6个模板，不可删除，可启用/停用）和自定义（用户手动创建，可编辑/删除）
- **触发类型**：FAIL（执行失败）、RESPONSE_TIME（响应超时）、GROUP_FAIL（任务失败）、SSL_CERT（SSL证书）、SCHEMA_CHANGE（API Schema变更）、ALL（通用）
- **预定义变量**：`{{monitorId}}`、`{{monitorName}}`、`{{groupId}}`、`{{url}}`、`{{status}}`、`{{statusCode}}`、`{{errorMessage}}`、`{{executedAt}}`、`{{responseTime}}`、`{{domain}}`
- **冷却时间**：每个模板独立配置冷却时间（默认30分钟），同类型告警在此时间内只发送一次，冷却期间不计入限频配额
- **告警限频**：配置启用后，相同监控项/任务/域名的告警累计达到上限后抑制通知
  - **最大告警次数**：相同指纹的告警最多发送此次数，达到后后续告警记录为 `SUPPRESSED`
  - **恢复通知**：开启后，告警恢复时自动发送已恢复通知（优先级最高），发送后重置限频计数
  - **计数维度**：SSL_CERT 按 domain，GROUP_FAIL 按 groupId，其他按 monitorId+groupId
  - **计数规则**：只有实际发送的告警才累计限频次数，静默和冷却期间的告警不计入
- **唯一启用规则**：同一触发类型只能有一个启用的模板，启用新模板时自动禁用同类型其他模板
- 新建模板默认**禁用**，需手动启用
- 若无启用模板，告警时使用系统默认模板

#### 7.2 告警渠道（邮件/Webhook）
- **渠道管理**：独立菜单"告警渠道"，配置邮件和Webhook通道
- **邮件**：SMTP 主机/端口/用户名/密码/SSL，支持多收件人
- **Webhook**：URL、请求方式（POST/PUT）、Content-Type、自定义请求头
- **渠道测试**：一键发送测试告警，实时反馈成功/失败
- **渠道状态展示**：所有下拉选择告警渠道的位置均显示渠道名称 + 启用/禁用状态标签

#### 7.3 响应时间告警
- 每个监控项可配置响应时间阈值和连续触发次数
- 达到阈值连续 N 次后自动发送告警

#### 7.4 任务失败告警
- 任务执行失败超过阈值时自动告警
- 无论是否配置告警通道，都会写入告警记录

#### 7.5 SSL证书告警
- **定时任务**：每天 9:00 AM 自动检查所有启用了 SSL 告警的域名
- **检查条件**：已过期、即将过期（≤N天）、明天过期、**证书错误（MISMATCH）**
- 告警阈值可配置（默认30天前预警）
- **执行时告警开关**：`sslAlertOnExecute`，开启后监控执行中检测到证书过期/证书错误时立即告警
- 无告警渠道时也会写入告警记录（`NO_CHANNEL` 状态）

#### 7.6 告警记录
- 所有触发的告警均记录到 `alert_log` 表，**所有触发类型统一使用告警模板系统**（FAIL/RESPONSE_TIME/GROUP_FAIL/SSL_CERT）
- **始终记录**：只要触发了告警条件，无论是否配置告警通道，都会写入告警记录
  - 未配置通道 → `NO_CHANNEL` 状态
  - 有通道 → 逐通道记录发送结果（`SENT` / `FAILED` / `CONFIG_UNAVAILABLE`）
  - 冷却期内 → `SUPPRESSED` 状态
  - 限频达到上限 → `SUPPRESSED` 状态
  - 静默期间 → `SILENCED` 状态（只写一条记录，不逐渠道重复）
  - 恢复通知 → `SENT` 状态
- **来源字段**：准确标识告警来源
  - 监控项触发 → `监控项#{id} {监控项名称}`
  - 监控任务触发 → `监控任务#{id} {监控任务名称}`
  - SSL证书触发 → `证书告警 {域名}`
- **执行ID**：记录 `executionId`，与执行日志关联（同一任务一次执行的所有日志共享同一执行ID）
- 记录完整上下文：监控名称、来源、告警渠道、触发类型、HTTP 状态码、响应时间、错误信息、**告警时间**
- 支持按触发类型、告警渠道、发送状态、**时间范围**、执行ID 筛选
- 独立菜单入口："告警记录"

### 8. 执行日志
- 记录每次执行的完整信息：**URL**、状态、状态码、响应时间、请求/响应体、域名、IP
- **执行ID**：`executionId`，任务执行时所有子监控项日志共享同一执行ID（UUID），单监控测试也生成独立执行ID
- **Schema 校验状态**：`schemaCheckStatus` 记录每次执行的 Schema 校验结果（未启用/未配置/非JSON/无响应体/无变更/变更类型列表）
- **关联告警记录**：详情弹窗中展示该执行ID关联的所有告警记录
- **变量追踪信息**：
  - 变量引用：显示 URL/请求头/请求体中 `{{var}}` 的替换情况
  - 变量设置：显示从响应中提取并写入的变量及值
- 支持按监控 ID、任务 ID、域名、IP、状态、Schema 校验状态、时间范围筛选
- 日志清理：一键删除 30 天前的日志
- **时间显示**：统一显示北京时间（YYYY-MM-DD HH:mm:ss）

### 9. 仪表盘
- **监控项卡片**：启用数 / 禁用数
- **监控任务卡片**：启用数 / 禁用数
- **响应超时卡片**：近10分钟 / 近1小时 / 近6小时（去重监控数）
- **SSL证书异常卡片**：即将过期 / 已过期 / 证书错误（域名数）
- **可用性统计卡片**：可用率 / 成功 / 失败 / 总次数；成功、失败、总次数可点击跳转到执行日志（带状态 + 时间范围）；时间窗口支持 1h/6h/24h/7d/30d 切换
- **Schema 检测卡片**：已检查 / 检出变更 / 无变更 / 破坏性变更；各指标可点击跳转到执行日志（带 Schema 过滤 + 时间范围）；时间窗口支持切换
- **时间窗口持久化**：可用性统计、Schema 检测的时间窗口选择通过 `localStorage` 保存，切换菜单返回后自动恢复
- **告警趋势图**：最近24小时按小时聚合的告警次数柱状图
- **告警类型分布图**：按触发类型（FAIL/RESPONSE_TIME/GROUP_FAIL/SSL_CERT/SCHEMA_CHANGE）的饼图分布
- **URL 响应时间趋势**：按分钟/小时粒度展示各URL响应时间，支持搜索和Top10筛选
- 最近执行日志列表
- **备忘录**：仪表盘顶部「备忘录」按钮，弹窗文本编辑器，支持 Ctrl+S 保存，按用户维度存储

### 9.1 仪表盘备忘录
- **入口**：仪表盘页面顶部「备忘录」按钮（Notebook 图标）
- **功能**：弹窗文本编辑器，支持自由文本记录
- **快捷键**：Ctrl+S 保存
- **存储**：按用户维度存储（`user_memo` 表，username UNIQUE），每个用户独立备忘录
- **API**：`GET /memo`（读取）、`POST /memo`（保存），需登录认证

### 10. 任务并发控制
- 任务执行时设置 `running` 标志，防止同一任务重叠执行
- 尝试执行正在运行中的任务时自动跳过并提示
- 前端显示"执行中"状态标签，禁用重复执行按钮

### 11. 告警静默（维护窗口）
- **一次性静默（每天时段）**：指定起止日期，期间每天的静默时段都生效
- **周期性静默**：支持三种重复方式：
  - **每天**：指定多个时间段（如 02:00~06:00, 12:00~13:00）
  - **每周**：指定星期几 + 多个时间段
  - **每月**：指定几号 + 多个时间段
- **适用范围**：全部 / 指定监控项 / 指定任务
- 启用/禁用开关，支持编辑和删除
- **静默行为**：静默期间告警仍会生成告警记录（发送状态为 `SILENCED`），但不会通过任何告警渠道发送通知，且不消耗限频配额

### 12. 操作审计日志
- **自动记录**所有写操作：创建/更新/删除监控项、监控任务、用户等
- **登录审计**：记录登录成功/失败，密码自动脱敏（如 `a***3`）
- **请求详情**：记录完整的请求体 JSON 数据
- 记录操作人、操作类型、对象类型、对象名称、详情、客户端 IP
- 支持按用户名和操作类型筛选
- 侧边栏菜单位于"系统管理"下

### 13. 数据备份
- **创建备份**：一键复制 SQLite 数据库文件，备份文件带时间戳命名（ADMIN）
- **下载备份**：下载数据库备份文件（仅 ADMIN）
- **备份列表**：显示文件名、大小、创建时间（所有角色可查看）
- **恢复备份**：选择历史备份文件恢复（需重启应用生效，仅 ADMIN）
- **删除备份**：清理不需要的备份文件（仅 ADMIN）
- 备份存储目录：`litv-monitor-server/backups/`

### 14. 系统管理菜单
- 侧边栏新增"系统管理"一级菜单，包含：
  - 变量管理
  - 代理设置（ADMIN）
  - 用户管理（ADMIN）
  - 安全设置（ADMIN）
  - 数据备份
  - 审计日志

### 15. 安全设置（ADMIN）
- **IP 访问控制**：
  - IP 白名单：启用后仅允许白名单中的 IP 访问后端接口
  - IP 黑名单：优先级最高，被匹配的 IP 直接返回 403
  - 支持 CIDR 格式（如 `192.168.1.0/24`）
- **IP 来源配置**：
  - IP来源请求头：从指定请求头获取客户端真实IP（默认 `X-Real-IP`），多个头逗号分隔
  - 严格IP模式：开启后，X-Forwarded-For 包含多个IP时拒绝请求
  - 可信代理IP：逗号分隔的可信代理IP列表，非可信代理的XFF头不读取
  - `IpUtils` 工具类缓存配置60秒，启动时初始化，修改后需重启生效
- **登录失败锁定**：
  - 可配置连续登录失败 N 次后自动锁定账号
  - 锁定时长可配置，到期自动解锁
  - 被锁定账号在用户管理中显示锁图标，鼠标悬浮提示
  - 管理员可解锁所有账号或指定账号
  - admin 被锁只能重启后端服务解锁（仅解锁 admin）
- **同时在线人数限制**：
  - 可配置同一账号最大同时在线人数
  - 超过限制自动挤掉最早登录的会话
  - 用户管理中可查看在线会话数、登录IP、浏览器信息，支持踢出会话
  - 当前登录会话标有"当前"标签，防止误踢自己
- **公开状态页**：开关「允许匿名访问」，默认关闭；开启后匿名用户可访问 `/status`，具体展示哪些监控项由监控项的「公开状态页展示」开关决定
- **Admin 账号保护**：admin 账号不能被删除、禁用或修改角色
- **个人资料修改**：右上角用户名下拉菜单 → 修改资料（昵称、邮箱、密码）

### 16. 数据库性能优化
- 为执行日志和告警日志表添加索引，提升分页查询性能
- 索引覆盖：monitor_id、group_id、executed_at、status、trigger_type、sent_at
- **新增索引**：user_session(user_id, active)、monitor(enabled)、alert_log(execution_id)、alert_log(alert_config_id)、ssl_certificate(domain_asset_id)、group_variable(group_id)
- **Schema 索引**（Migration 42）：execution_log(schema_check_status)、execution_log(executed_at, schema_check_status)
- **统计查询合并**：Schema 检测统计由 8 次 COUNT 合并为单条 CASE WHEN 聚合，去除 REPLACE 包装使索引生效
- **时间格式统一**：所有时间列统一空格分隔格式，保证字符串比较与索引范围扫描正确
- **WAL 模式**：SQLite 启用 WAL + `busy_timeout=5000`，提升并发读写稳定性；连接池 20
- **自动数据保留**：每日 03:30 自动清理超期执行/告警日志（默认 30 天）
- **入库截断**：请求/响应体超过阈值截断，控制数据库膨胀
- **变量引擎优化**：单次执行只加载一次 global + group 变量
- 数据库迁移版本：27（补充索引）、41（时间格式修复）、42（Schema 索引）、45（清理孤儿表）、46（监控项状态页展示字段）、47-50（监控告警、任务失败判定、兜底通道、IP来源配置）

### 17. 前端权限系统（集中式）
- **集中配置**：所有菜单和按钮权限在 `permissions.js` 一个文件中定义
- **菜单权限**：侧边栏根据 `MENUS` 配置动态渲染，不满足角色的菜单自动隐藏
- **按钮权限**：使用 `v-permission` 指令控制按钮显隐，如 `<el-button v-permission="'monitor:create'">`
- **路由守卫**：路由守卫根据 `MENUS` 中的角色配置拦截未授权访问
- **权限 composable**：`usePermission()` 提供 `hasButton()`、`hasMenu()` 等方法
- **扩展方式**：新增功能只需在 `permissions.js` 中添加菜单/按钮定义，无需修改各页面组件

### 18. API 签名与预请求脚本
- **签名配置 UI**：左侧表单式配置，选择签名类型后自动生成右侧脚本
- **预请求脚本**：右侧代码编辑器，可直接编辑脚本，保存时仅存储脚本内容
- **签名类型**：MD5_SIGN（微信支付V2）、HMAC_SHA256（AWS/Stripe）、RSA_SHA256（微信支付V3）
- **内置加密函数**：`md5()`、`sha256()`、`hmacSha256()`、`hmacSha256Base64()`、`rsaSha256Sign()`、`rsaSha256SignHex()`、`base64()`、`base64Decode()`、`sha1()`、`hmacMd5()`
- **RSA签名**：`crypto.rsaSha256Sign(message, privateKeyPem)` 使用私钥进行SHA256withRSA签名，返回Base64；`crypto.rsaSha256SignHex()` 返回十六进制。支持 `BEGIN RSA PRIVATE KEY` 和 `BEGIN PRIVATE KEY` 两种PEM格式
- **内置工具对象**：`timestamp`、`datetime`、`date`、`time`、`nonce`、`uuid` 等
- **变量支持**：脚本中可使用 `{{global.xxx}}`、`{{group.xxx}}`、`{{env.timestamp}}` 等变量，变量替换在脚本执行之前完成
- **预请求脚本执行顺序**：变量替换 → 脚本执行（内置签名 + 自定义JS） → HTTP 请求

### 19. 代理设置（ADMIN）
- **统一代理**：为所有监控项的 HTTP 请求配置代理，支持 HTTP 和 SOCKS5 类型
- **多代理管理**：可配置多个代理服务器，同时只能启用一个
- **代理认证**：支持带用户名/密码的代理服务器（可选）
- **启用/停用**：操作列按钮控制，同时只能启用一个
- **连通性测试**：一键测试代理是否可用（通过 httpbin.org 验证）
- **自动应用**：所有 HTTP 请求（监控执行、测试）自动通过启用的代理发送

### 20. 告警渠道平台扩展
- **钉钉**：自定义 Webhook，支持关键词安全设置
- **企业微信**：自定义 Webhook，支持 Markdown 消息格式
- **飞书**：自定义 Webhook，支持富文本消息格式
- **渠道表单**：AlertChannelList.vue 根据平台类型动态切换配置表单
- **向后兼容**：旧邮件/Webhook 渠道继续工作

### 21. Uptime 统计
- **仪表盘卡片**：显示各时间窗口的可用率（1h / 6h / 24h / 7d / 30d）
- **后端接口**：`GET /dashboard/uptime` 返回各窗口的 uptime 百分比
- **时间窗口切换**：前端卡片支持点击切换不同时间窗口

### 22. 公开状态页
- **可配置公开**：默认匿名不可访问（返回 403），需在「系统管理 → 安全设置 → 公开状态页」开启「允许匿名访问」
- **监控项级筛选**：仅展示在监控项中开启「公开状态页展示」开关的项目（`monitor.show_on_status_page`）
- **访问路径**：`/status`；已登录用户始终可访问
- **界面**：StatusPage.vue 深色科技风，卡片网格自适应 1-4 列，30 秒自动刷新，展示监控项状态、响应时间、最后检查时间
- **整体状态**：仅统计开启了状态页展示的监控项（operational / degraded / down）

### 23. 多主题换肤
- **入口**：顶栏用户信息左侧画笔图标，弹出主题网格
- **主题**：默认（蓝）、深色、翠绿、极光紫、中国红、活力橙，共 6 套
- **持久化**：选择保存到浏览器 `localStorage`，刷新保持
- **实现**：CSS 变量 + `document.documentElement` 主题类；深色模式含 Element Plus 组件专门适配
- **说明**：公开状态页为独立设计，不受换肤影响

### 24. API Schema 变更检测
- **独立管理**：ApiSchemaList.vue 管理 JSON Schema 定义，与监控项解耦
- **可视化编辑器**：JsonSchemaEditor.vue + SchemaFieldRow.vue 提供双模式可视化编辑（可视化树 + JSON 文本），支持拖拽排序字段、快速添加模板、必填标记、折叠展开
- **监控项集成**：MonitorList.vue 编辑弹窗中配置期望 Schema（可视化编辑器）、变更类型、告警通道
- **自定义校验**：ApiSchemaService 实现 JSON Schema 校验（type、required、嵌套属性、数组）
- **变更检测**：监控执行时自动对比期望 Schema 与实际响应，递归双向检测
- **破坏性变更**：必填字段删除自动判定为 BREAKING，其他变更为 ADDED/REMOVED/MODIFIED
- **历史版本对比**：Schema 每次修改自动记录历史版本，支持选择任意版本左右对照，差异字段高亮
- **告警集成**：检测到匹配变更时通过配置的告警通道发送通知（triggerType=SCHEMA_CHANGE）

### 25. 巡检模式
- **巡检配置**：InspectionList.vue 管理巡检计划（定时执行的批量监控）
- **批量执行**：InspectionService 通过 @Async 异步执行选定的监控项
- **巡检报告**：inspection_history + inspection_detail 表记录详细结果（含 Schema 变更计数）
- **Schema 变更追踪**：每次巡检记录每个监控项的 Schema 变更信息，历史汇总变更总数
- **结果查看**：弹窗展示每个监控项的执行结果（状态、响应时间、错误信息、Schema 变更）

### 26. 仪表盘备忘录
- **入口**：仪表盘页面顶部「备忘录」按钮（Notebook 图标）
- **功能**：弹窗文本编辑器，支持自由文本记录
- **快捷键**：Ctrl+S 保存
- **存储**：按用户维度存储（`user_memo` 表，username UNIQUE），每个用户独立备忘录
- **API**：`GET /memo`（读取）、`POST /memo`（保存），需登录认证

### 27. 侧边栏版本展示
- **位置**：侧边栏 Logo 下方，显示后端版本 / 前端版本（如 `1.2.1 / 1.2.1`）
- **数据来源**：`GET /version` 接口（公开，无需认证），返回 `{ backend, frontend }`
- **配置**：后端版本读取 `application.yml` 的 `app.version`；前端版本读取 `package.json` 的 `version`

### 28. 多协议监控（15 种协议）
- **支持协议**：HTTP、Ping、TCP、SSH、Telnet、FTP、VNC、MySQL、PostgreSQL、Redis、Memcached、MongoDB、ZooKeeper、AMQP/RabbitMQ、MQTT
- **创建入口**：监控项管理 →「添加HTTP监控」/「添加其他监控」
- **配置**：非 HTTP 协议填写主机地址 + 端口（带默认值提示），以及超时、启用、公开状态页展示、告警配置
- **架构**：`MonitorExecutor` 策略接口 + `MonitorExecutorRegistry` 自动注册 + `AbstractMonitorExecutor` 抽象基类
- **统一能力**：响应时间告警、连续失败告警、加入监控任务与巡检、状态页展示均与 HTTP 一致

### 29. 周期提醒
- **用途**：服务器续费、SSL 证书续费、机房费用、定期巡检、事务提醒等周期任务
- **分类预设**：服务器续费/SSL证书续费（提前30天）、机房费用（提前7天）、定期巡检（提前3天）、事务提醒（提前1天）、其他（提前1天）
- **重复类型**：不重复 / 每天 / 每周 / 每月 / 每年
- **通知**：通过告警渠道（邮件/Webhook/钉钉/企微/飞书）发送，支持提前提醒与到期提醒
- **调度**：`ReminderScheduler` 每分钟检查，Caffeine 防重复触发
- **仪表盘**：顶部「周期提醒」按钮显示 已过期 + 今天到期 + 近7天到期 数量徽章

### 30. 漏洞情报导航
- **入口**：仪表盘顶部「漏洞情报」按钮
- **内容**：上块为 6 个主流高价值情报站卡片（阿里云漏洞库 AVD、CNVD、CNNVD、CVE、NVD、奇安信威胁情报中心），下块为 15 个参考情报站列表
- **实现**：站点数据硬编码于前端，点击新标签页打开

---

## 权限配置示例（permissions.js）

```js
// 按钮权限定义
export const BUTTONS = {
  'monitor:create': { roles: ['ADMIN', 'OPERATOR'] },
  'monitor:edit':   { roles: ['ADMIN', 'OPERATOR'] },
  'monitor:delete': { roles: ['ADMIN', 'OPERATOR'] },
  'monitor:test':   { roles: ['ADMIN', 'OPERATOR'] },
  'monitor:copy':   { roles: ['ADMIN', 'OPERATOR'] },
  'monitor:batch-status': { roles: ['ADMIN', 'OPERATOR'] },
  'group:create':   { roles: ['ADMIN', 'OPERATOR'] },
  'group:edit':     { roles: ['ADMIN', 'OPERATOR'] },
  'group:delete':   { roles: ['ADMIN', 'OPERATOR'] },
  'group:run':      { roles: ['ADMIN', 'OPERATOR'] },
  'variable:create': { roles: ['ADMIN', 'OPERATOR'] },
  'variable:edit':  { roles: ['ADMIN', 'OPERATOR'] },
  'variable:delete': { roles: ['ADMIN', 'OPERATOR'] },
  'domain:edit':    { roles: ['ADMIN', 'OPERATOR'] },
  'domain:delete':  { roles: ['ADMIN', 'OPERATOR'] },
  'alert-template:create': { roles: ['ADMIN', 'OPERATOR'] },
  'alert-template:edit':   { roles: ['ADMIN', 'OPERATOR'] },
  'alert-template:delete': { roles: ['ADMIN', 'OPERATOR'] },
  'alert-channel:create':  { roles: ['ADMIN', 'OPERATOR'] },
  'alert-channel:edit':    { roles: ['ADMIN', 'OPERATOR'] },
  'alert-channel:delete':  { roles: ['ADMIN', 'OPERATOR'] },
  'alert-channel:test':    { roles: ['ADMIN', 'OPERATOR'] },
  'alert-silence:create':  { roles: ['ADMIN', 'OPERATOR'] },
  'alert-silence:edit':    { roles: ['ADMIN', 'OPERATOR'] },
  'alert-silence:delete':  { roles: ['ADMIN', 'OPERATOR'] },
  'alert-silence:enable':  { roles: ['ADMIN', 'OPERATOR'] },
  'alert-silence:disable': { roles: ['ADMIN', 'OPERATOR'] },
  'schema:create':  { roles: ['ADMIN', 'OPERATOR'] },
  'schema:edit':    { roles: ['ADMIN', 'OPERATOR'] },
  'schema:delete':  { roles: ['ADMIN', 'OPERATOR'] },
  'schema:validate': { roles: ['ADMIN', 'OPERATOR'] },
  'inspection:create': { roles: ['ADMIN', 'OPERATOR'] },
  'inspection:edit':   { roles: ['ADMIN', 'OPERATOR'] },
  'inspection:delete': { roles: ['ADMIN', 'OPERATOR'] },
  'inspection:run':    { roles: ['ADMIN', 'OPERATOR'] },
  'log:cleanup':    { roles: ['ADMIN', 'OPERATOR'] },
  'user:create':    { roles: ['ADMIN'] },
  'user:edit':      { roles: ['ADMIN'] },
  'user:delete':    { roles: ['ADMIN'] },
  'security:settings': { roles: ['ADMIN'] },
  'security:kick':  { roles: ['ADMIN'] },
  'security:unlock': { roles: ['ADMIN'] },
  'backup:create':  { roles: ['ADMIN'] },
  'backup:download': { roles: ['ADMIN'] },
  'backup:restore': { roles: ['ADMIN'] },
  'backup:delete':  { roles: ['ADMIN'] },
  'proxy:create':   { roles: ['ADMIN'] },
  'proxy:edit':     { roles: ['ADMIN'] },
  'proxy:delete':   { roles: ['ADMIN'] },
  'proxy:activate': { roles: ['ADMIN'] },
  'proxy:test':     { roles: ['ADMIN'] }
}
```
