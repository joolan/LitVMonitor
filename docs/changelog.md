# 迭代修复记录

## v1.3.0: 安全修复与体验优化

### 修复
- **用户创建/编辑 500 错误**：UserService 密码校验、角色校验、管理员保护等异常由 `RuntimeException` 改为 `IllegalArgumentException`，返回 400 + 具体错误信息而非笼统的"服务器内部错误"
- **登录错误提示**：AuthController 捕获 `BadCredentialsException` 返回"用户名或密码错误"，捕获 `DisabledException` 返回"账号已被禁用"；前端响应拦截器区分登录接口与其他接口的 401 处理
- **用户管理提交多余字段**：编辑用户后表单携带 `id`、`mustChangePassword`、`createdAt`、`updatedAt` 等后端 DTO 无字段，添加 `@JsonIgnoreProperties(ignoreUnknown = true)` 并前端提交前清理多余字段
- **登录页深色模式**：登录页输入框受深色主题影响显示异常，强制 `color-scheme: light` + CSS 覆盖保持白底样式

### 优化
- **备份下载进度**：改用 `ReadableStream` 流式读取，Chrome/Edge 支持 `showSaveFilePicker` 用户选择保存路径 + 零延迟写入，Firefox/Safari 降级为 blob 方案
- **版本号独立管理**：前端版本从 `package.json` 构建时注入（`__APP_VERSION__`），后端 `/version` 只返回后端版本，显示格式改为 `前端版本 / 后端版本`
- **异常处理规范化**：全面排查 `RuntimeException` 使用场景，用户输入校验类异常统一返回 400，内部执行错误保留 500

### 文档
- 更新 Manual.vue（默认密码描述）
- 更新 README.md、changelog.md、devlog.md

---

## v1.2.2: 功能增强与交互优化

### 功能增强
- **告警模板类型**：新增 `templateType` 字段（SYSTEM/CUSTOM），初始6个系统预置模板标记为 SYSTEM 不可删除，用户新建模板为 CUSTOM 类型
- **周期提醒分类**：新增「事务提醒」分类（TASK_RENEWAL），默认提前1天提醒
- **监控ID/任务ID跳转**：
  - 监控项管理、监控任务列表的 ID 列可点击，跳转到执行日志并按对应 ID 筛选
  - 执行日志的「监控ID」列跳转到监控项管理，「任务ID」列跳转到监控任务
  - 告警记录的「监控名称」列跳转到监控项管理，「任务名称」列跳转到监控任务（有对应ID时显示链接，否则纯文本）
- **仪表盘自动刷新**：标题旁新增刷新图标，支持关闭/10秒/30秒/3分钟自动刷新间隔，设置保存在浏览器 localStorage
- **ID精确查询**：监控项管理和监控任务的搜索栏新增 ID 查询条件，后端 `list` 接口增加 `id` 参数支持精确匹配

### 交互优化
- **HTTP监控表单提示优化**：URL、请求头、JSONPath、JSONPath期望值、期望文本、正则表达式 的帮助说明从独立行改为 label 后 `?` 图标悬浮提示，节省垂直空间
- **仪表盘刷新设置弹窗**：左对齐、窄宽度（160px），radio 选项无右侧 margin

### 文档更新
- 使用手册（Manual.vue）更新：告警模板类型说明、事务提醒分类、ID跳转功能、仪表盘自动刷新、HTTP表单tooltip说明
- README.md 版本号更新至 v1.2.2
- overview.md 更新项目简介、告警模板类型、周期提醒分类

---

## v1.2.1: 安全加固与缺陷修复

### 安全修复（按审计优先级）

**认证与密钥**
- 移除硬编码 JWT `base-secret` 与基于 MAC 地址的可预测派生；`JWT_SECRET` 未设置时自动生成随机密钥并持久化到 `.jwt-secret`（48字节 Base64），设置时强制 ≥32 字符，否则启动失败
- 角色权限改为每次请求从数据库加载（不再信任 token 中的 `role` claim），降权即时生效
- 默认管理员不再使用固定口令 `admin123`：首次初始化优先读取环境变量 `ADMIN_INIT_PASSWORD`，否则生成随机强密码并仅在启动日志输出一次；新增 `must_change_password` 标记（Migration 55）与强制改密流程（后端拦截返回 428，前端登录后弹窗强制修改，改密后失效全部会话）
- 检测到存量管理员仍使用 `admin123` 时自动标记为必须改密

**越权 / 授权**
- 修复周期提醒 IDOR：`get/update/delete/complete/snooze/enable/disable` 均增加 `username` 归属校验
- `/alert/config/list`、`/alert/template/list` 增加 `@PreAuthorize(ADMIN/OPERATOR)`（此前可被任意登录用户读取 SMTP 密码 / webhook token）
- `/backup/list` 增加 `@PreAuthorize(ADMIN)`
- 监控项读取接口对 VIEWER 角色屏蔽敏感字段（请求头、请求体、签名私钥、预请求脚本、变量提取、config）

**SSRF**
- 新增 `SsrfProtectionService`，在**连接期**校验实际解析出的 IP：回环、内网、链路本地、CGNAT、组播、`0.0.0.0/8`、`169.254.0.0/16`（含云元数据 169.254.169.254）、IPv6 ULA/IPv4-mapped
- 全部协议执行器（TCP/SSH/MySQL/Redis/FTP/VNC/Telnet/PostgreSQL/AMQP/MQTT/Memcached/MongoDB/ZooKeeper/Ping）在建立连接前校验；HTTP 执行器使用校验型 OkHttp `Dns`（覆盖重定向）并对目标主机显式校验；告警/提醒 webhook 同样校验
- 修复此前 `monitor.ssrf-protection=true` 实为死代码（`isInternalHost` 从未调用）的问题

**密钥泄露与日志**
- 移除 webhook URL / body 明文日志，审计日志掩码不再泄露首尾字符
- `ssl-verify-disabled` 保持可配置（默认沿用 true 以兼容自签证书/证书链不完整环境，可用 `SSL_VERIFY_DISABLED=false` 开启严格校验）

### 缺陷 / 逻辑修复

- **迁移安全**：新增 `schema_version` 表，破坏性迁移（1/36/38/40）仅在未执行时运行，避免每次启动拷贝-删除-重建；修复 `alert_log` 重建后索引永久丢失（重新创建 5 个索引）
- **巡检标志泄漏**：`InspectionService.runInspection` 在 config 缺失/历史写入失败时未重置全局运行标志，导致后续巡检永久不再执行；已在所有提前返回路径补 `finishInspection()`
- **监控列表过滤 bug**：关键词 OR 条件未分组，导致与 `enabled` 组合时 `enabled` 失效；已用 `and(...)` 包裹
- **SQLite 并发**：PRAGMA 改由 JDBC URL 下发（确保连接池每个连接生效），连接池 20 → 5
- **限流计数原子化**：`alert_rate_limit` 由读-改-写改为原子 `UPDATE ... current_count = current_count + 1`，避免丢更新

### 前端修复

- `v-permission` 指令：修复每个元素泄漏 watcher 的问题（卸载时停止监听），新增 `updated` 钩子响应动态权限值
- 路由：新增 404 兜底路由与页面；`userInfo` 从 localStorage 水合，修复硬刷新时合法管理员被弹回仪表盘的问题
- 告警静默：修复编辑无限期静默时 `endTime` 为空导致的空指针崩溃
- 监控任务：补齐表单初始模型缺失的 `failCriteriaType/failCountThreshold/failPercentThreshold` 字段
- JSON Schema 编辑器：新增 `flush()` 并在提交前调用，修复 300ms 防抖导致最后一次编辑丢失；卸载时清理定时器
- API 拦截器：处理 428（需先改密）并清理本地登录态

### 优化

- 删除 `ExecutionService` 中约 350 行死代码（重复的 OkHttp 客户端/代理/SSL、`validateResponse`、`recordDomainAsset`、`isInternalHost` 等，均已由 `HttpMonitorExecutor` 承担）
- 告警/提醒出站 HTTP 客户端由「每次发送新建」改为缓存复用
- 前端 ECharts 改为按需引入（`echarts/core` + Bar/Pie/Line + Grid/Tooltip/Legend + Canvas），Dashboard 分包从 ~1035KB 降至 ~542KB
- 监控列表搜索/重置时重置到第 1 页
- 登录失败锁定默认值改为启用（仅影响新装实例；已有实例可在「安全设置」中开启）

### Bug修复（v1.2.1 补充）

- 修复 `/backup/list` 等备份接口 500：`extractDbPath()` 未剥离 JDBC URL 的 PRAGMA 查询参数（`?busy_timeout=...`），导致 `Paths.get` 抛 `Illegal char <?>`
- 修复登出接口返回 401 导致前端无法退出：`/auth/logout` 加入 `permitAll`，过滤器对 `/auth/*` 跳过会话失效校验；前端 `MainLayout` 登出异常不再被当作"取消"，`stores/user.js` 的 `logout()` 永不 reject，并修复 JWT base64url 解码
- 修复未认证请求返回 403 而非 401：注册此前未被使用的 `JwtAuthenticationEntryPoint`（未认证 → 401 JSON），并新增 JSON `AccessDeniedHandler`（无权限 → 403 JSON）
- 前端 API 拦截器新增全局登录失效提示：401 时弹出「登录状态已失效，是否重新登录？」（确认后清理登录态并跳转，避免并发请求重复弹窗）；403 时以 `ElMessage` 提示无权限

### 安全加固：数据备份下载

- **路径穿透校验加强** `BackupController.validateBackupPath()`：
  - 词法校验（`normalize()` 消解 `..` 后必须位于 `backups/` 内）
  - **后缀校验：仅允许 `.db` 文件（大小写不敏感）**
  - 拒绝符号链接（`Files.isSymbolicLink`），防止软链指向 `.jwt-secret` 等目录外文件
  - 真实路径校验（`toRealPath()` 解析软链后再次确认仍在 `backups/` 内），防止父目录为软链的逃逸
- **下载错误响应修正**：`downloadBackup` 改用直接写 JSON（`writeError`）替代 `response.sendError`，避免触发 `/error` 转发被鉴权拦截导致状态被改写为 401
- `SecurityConfig` 放行 `/error`，避免错误转发被 `anyRequest().authenticated()` 拦截
- **实测**：`backups\..\..\.jwt-secret`、`.jwt-secret`、实时库 `litv-monitor.db`、指向 `.jwt-secret` 的软链、`evil.txt` / `evil.TXT` 均返回 **403**；合法 `.db` 备份下载返回 200

### 逻辑修复：周期提醒

- **修复"到期后自动完成/自动推进"的逻辑冲突**：`ReminderScheduler` 到期时只负责发送通知，**不再自动把一次性任务标记为已完成、也不再自动推进周期任务到下一次**。是否完成完全由用户手动操作决定
  - 未处理的任务会一直保持「已过期」并在仪表盘角标持续计数（不再出现"已过期 → 一会自动变已完成"）
  - 周期任务点击「完成」后才推进到下一次；已错过多次时直接跳到下一个未来时间点
  - 通知按「到期时间点」去重：同一到期点只通知一次，24 小时后仍未处理会再次提醒
- **修复提前完成会落回同一次的问题**：`ReminderService.calculateNextOccurrence()` 改为以「当前到期点 `nextDueAt`」为基准推进（而非最初的 `dueDate`），并统一使用"严格晚于当前时间"的判定
- 更新 `ReminderList.vue` 说明与 `Manual.vue` 文档

### 新增：SSRF 防护模式可在「安全设置」中配置

- 新增安全设置项 `ssrf_mode`（数据库配置，运行时可改，最多 5 秒生效，无需重启），在「安全设置 → 监控请求安全（SSRF 防护）」中提供三档：
  - **严格 strict**：禁止 回环 / 内网(10、172.16/12、192.168/16) / 链路本地 / CGNAT / 云元数据(169.254.169.254) / IPv6 ULA
  - **允许内网 allow_internal**：允许访问内网地址，但仍禁止 回环 / 链路本地(含云元数据) / CGNAT 等
  - **关闭 off**：不限制（适用于系统本身部署在内网、需要监控内网地址的场景）
- `SsrfProtectionService` 由固定 `@Value` 改为读取数据库设置（带 5 秒缓存）；`monitor.ssrf-protection` 环境变量仅作为首次初始化默认值
- 拦截提示语补充"可在「安全设置 → 监控请求安全」调整防护模式"
- **实测**：`192.168.10.7` 在 strict 下被拦截（SSRF-BLOCKED），在 allow_internal / off 下放行（正常发起连接）；`127.0.0.1` 在 strict/allow_internal 下均被拦截（回环），仅 off 放行

### 密钥脱敏与其余加固

- **代理密码不回传**：`ProxyConfig.password` 加 `@JsonProperty(WRITE_ONLY)`，响应只返回 `hasPassword`；前端编辑时留空表示不修改
- **告警渠道密钥脱敏**：新增 `SecretMasker`，对 `alert_config.config` 中的 `password/secret/token/apiKey` 等字段及 webhook URL 的 token 参数脱敏为 `******`；更新时若前端回传哨兵值则用库中旧值回填（`smtpPassword` 等宽松匹配）
- **全局变量密钥完全脱敏**：`isSecret=true` 的值统一返回 `******`（此前保留首尾字符）
- **备份下载令牌改请求头**：新增 `X-Download-Token` 请求头，前端改用 `fetch` + blob 下载，避免令牌出现在 URL / 访问日志 / 浏览器历史
- **登录 IP 维度限速**：同一 IP 15 分钟内失败 30 次后返回 429（缓解撞库/暴力破解），成功后清零
- **安全响应头**：HSTS、`Referrer-Policy: no-referrer`、`Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'`、`X-Frame-Options: DENY`
- **审计日志完全脱敏**：密码字段统一为 `***`

### 性能与维护

- 前端**图标按需注册**（仅注册实际使用的 43 个，替代全量 293 个）+ Vite `manualChunks` 拆分 vendor；应用主包 1270KB → 66KB（vendor 分包可缓存），Dashboard 分包 531KB → 23KB
- 修复主题「翠绿」图标引用了不存在的 `Leaf`，改为 `Cherry`
- `PreRequestScriptEngine` 复用共享 GraalVM `Engine`
- `ExecutionService` 实现 `retryCount`（最大尝试次数，默认 1 不重试）
- 移除 `AlertService` 中 `AlertChannel`/`AlertConfig` 共用表导致的死分支
- 其余列表页搜索/重置时重置到第 1 页；日志/监控列表监听 `route.query`，修复仪表盘深链不刷新
- 新增 `.editorconfig`、`.prettierrc.json`、`.eslintrc.cjs`

### N+1 查询 / 列表上限 / 时区统一 / 组件拆分

- **N+1 查询优化**：`MonitorService.getGroupCounts()` 由「加载全部 `group_monitor` 行」改为一次 `GROUP BY`；`MonitorGroupService.listGroups()` 由「逐任务 `selectCount`」改为一次 `GROUP BY`（`GroupMonitorMapper.countByMonitor/countByGroup`）
- **列表上限**：MyBatis-Plus 分页拦截器 `setMaxLimit(1000)`；告警渠道/配置/模板、全局变量、代理等「返回全部」接口加 `LIMIT 1000`
- **时区统一**：启动时强制 JVM 时区为 `Asia/Shanghai`（与主机时区解耦）；`DashboardService`/`StatusPageService` 的时间边界由 SQL `datetime('now','localtime')` 改为 Java 侧北京时间参数；`schema.sql`/`DatabaseInitConfig` 中 `DEFAULT CURRENT_TIMESTAMP`（UTC）统一改为 `datetime('now','localtime')`
- **前端组件拆分**：从 `MonitorList.vue` 抽出
  - `components/MonitorHttpDialog.vue`：HTTP 监控弹窗（常规配置 / 更多配置项两个 Tab，含判断条件、三类告警、变量赋值、API签名与预请求脚本），props `modelValue/editingRow`，emits `update:modelValue/saved`
  - `components/MonitorOtherDialog.vue`：其他类型监控弹窗
  - `MonitorList.vue` **2108 → 571 行**，仅保留列表/搜索/分页/关联任务/批量关联逻辑

## v1.2.0: 多协议监控（15种协议）

### 新增功能

**0. 多协议监控支持**
- `MonitorType` 枚举：HTTP、PING、TCP、SSH、TELNET、FTP、VNC、MYSQL、POSTGRESQL、REDIS、MEMCACHED、MONGODB、ZOOKEEPER、AMQP、MQTT（15种协议）
- 策略模式架构：`MonitorExecutor` 接口 + `MonitorExecutorRegistry` 自动注册
- `AbstractMonitorExecutor` 抽象基类：统一 config 解析、host/port 解析、socket 建立、readBytes、失败标记
- `HttpMonitorExecutor`：从 ExecutionService 提取完整 HTTP 逻辑（OkHttp、代理、SSL、前置脚本、变量提取、域名资产录制）
- `PingMonitorExecutor`：ICMP 可达性检测（`InetAddress.isReachable`，超时 5s）
- `TcpMonitorExecutor`：TCP 端口连通性检测（`Socket.connect`）
- `SshMonitorExecutor`：SSH Banner 识别（连接 22 端口读取 `SSH-2.0-xxx`）
- `MysqlMonitorExecutor`：MySQL Initial Handshake Packet 解析（协议版本 + Server Version）
- `RedisMonitorExecutor`：RESP 协议 `PING` → `+PONG`
- `TelnetMonitorExecutor`：Telnet 服务探测（读取 Banner，无 Banner 时发送换行再读）
- `FtpMonitorExecutor`：FTP 服务端 Banner 识别（`220 ...`）
- `VncMonitorExecutor`：VNC 服务端 Banner 识别（`RFB xxx.xxx`）
- `PostgresMonitorExecutor`：PostgreSQL SSLRequest 探测（响应 `S`/`N` 均表示服务存活）
- `MemcachedMonitorExecutor`：`version\r\n` → `VERSION x.y.z`
- `ZookeeperMonitorExecutor`：`ruok` → `imok`（未启用4lw白名单时降级为 TCP 存活）
- `AmqpMonitorExecutor`：AMQP 0-9-1 协议头 `AMQP\0\0\x09\x01` → 协议头回显或 connection.start 帧
- `MqttMonitorExecutor`：MQTT 3.1.1 CONNECT（无认证）→ CONNACK（解析 return code）
- `MongoMonitorExecutor`：OP_MSG `{hello:1,$db:"admin"}` → 解析响应，提取 maxWireVersion
- 非 HTTP 监控配置存储：`monitor` 表新增 `config` TEXT 字段（JSON），含 `host` + `port`
- Migration 54：`ALTER TABLE monitor ADD COLUMN monitor_type VARCHAR(20) DEFAULT 'HTTP'` + `config TEXT`
- 所有现有监控项自动兼容（默认 `monitor_type = 'HTTP'`）
- `ExecutionService` 策略分发：根据 `monitorType` 查找对应 Executor 执行
- 前端 `MonitorList.vue`：「添加HTTP监控」+「添加其他监控」双按钮
- 非 HTTP 监控弹窗：类型分组下拉（网络探测/远程登录/数据库/消息队列）、主机地址、端口（带默认值提示，Ping 隐藏端口）、超时、启用、状态页展示、告警配置
- 表格新增「类型」列（彩色 Tag），编辑按钮根据类型自动路由到对应弹窗
- 非 HTTP 协议统一支持：响应时间告警、连续失败告警、状态页展示、任务/巡检复用

**1. 仪表盘漏洞情报导航**
- `Dashboard.vue` 顶部「周期提醒」按钮左侧新增「漏洞情报」按钮
- 弹窗分上下两块：上方为 6 个主流高价值情报站卡片（主视觉），下方为 15 个其他参考情报站列表
- 卡片/条目点击后新标签页打开，前端硬编码，无需数据库配置
- 高价值站：阿里云漏洞库(AVD)、CNVD、CNNVD、CVE Program、NVD、奇安信威胁情报中心
- 参考站：Exploit-DB、GitHub Advisory、OSV、CISA KEV、Snyk、腾讯威胁情报、微步在线、Seebug、360漏洞库、OSCS、NVDB、FreeBuf、安全客、VulDB、Packet Storm

**2. 签名配置支持「签名转大写」**
- 监控项编辑 → API签名配置，在「包含时间戳」下方新增「签名转大写」开关
- 适用于 MD5 / HMAC-SHA256 / RSA-SHA256 三种签名类型
- 开启后生成的预请求脚本会对最终签名追加 `.toUpperCase()`，满足部分接口要求全大写签名的场景；默认关闭保持原始大小写

### Bug修复

- 修复 HTTP 监控提交时未显式设置 `monitorType: 'HTTP'` 的问题
- **修复预请求脚本 / API签名完全无法执行的问题**（影响所有使用签名或自定义脚本的监控项）：
  - `PreRequestScriptEngine` 使用了无效的 GraalJS 选项 `js_bigint`、`js.small-ic`，导致 JS Context 构建失败，脚本从未真正执行，只有内置签名生效
  - `crypto.md5` 引用了不存在的 `Polyglot.import` polyfill，导致调用即报错；改为直接使用 `MessageDigest` 正确实现
  - `HostAccess.UNTRUSTED` 下 `java.*` 包全局不可用（脚本内 `java.security.*` 报 `ReferenceError`），改用 `ProxyExecutable` + `ScriptRuntime` 宿主对象提供 `crypto` / `util` 能力，不暴露任意 Java 访问，更安全
  - 当存在自定义预请求脚本时不再同时执行内置签名，避免双重签名污染规范字符串
  - 回写请求改动时兼容数字类型（新增 `valueToString`），修复 `util.timestamp()` 等数值写入 params/headers 后提取失败的问题
  - 效果：`签名转大写`、`.toLowerCase()` / `.toUpperCase()`、HMAC/SHA/RSA、`util.datetime()` 等自定义脚本能力全部恢复正常
- 修复请求体 Content-Type 与 bodyType 不一致的问题：
  - 后端默认 Content-Type 现按 bodyType 推导（json→`application/json`、xml→`application/xml`、text→`text/plain`、form→`application/x-www-form-urlencoded`），此前固定为 `application/json`
  - 前端切换 bodyType 及保存时自动同步请求头中的 Content-Type，修复从 Form 切换到 JSON 后残留 `application/x-www-form-urlencoded` 的问题
  - 自定义 Content-Type（如 `application/vnd.api+json`）不受影响；显式请求头仍优先于默认值

## v1.1.0: 仪表盘备忘录 + 版本展示 + 周期任务提醒 + Bug修复

### 新增功能

**0. 周期任务提醒**
- `reminder_task` 表（Migration 53）：username, title, description, category, due_date, recurrence_type, recurrence_config, advance_enabled, advance_minutes, advance_days, alert_channel_ids, enabled, completed, next_due_at 等字段
- 5 种分类预设：服务器续费(提前30天)、SSL证书续费(提前30天)、机房费用(提前7天)、定期巡检(提前3天)、其他(提前1天)
- 5 种重复类型：不重复(once)、每天(daily)、每周(weekly)、每月(monthly)、每年(yearly)
- 周期任务到期/完成后自动计算下次触发时间
- `ReminderController`：11 个 REST 端点（CRUD + complete + snooze + enable + disable + dashboard + preview）
- `ReminderService`：核心业务逻辑（CRUD、完成、延迟、周期计算）
- `ReminderNotifyService`：通过 AlertChannel 渠道发送通知（邮件/Webhook/钉钉/企微/飞书），通知内容包含"任务通知"关键字
- `ReminderScheduler`：每分钟定时检查，触发提前提醒和到期提醒，Caffeine 缓存防重复触发
- `TriggerType` 新增 `REMINDER` 枚举值
- 前端 `ReminderList.vue`：列表页（分页+筛选+表格）、创建/编辑弹窗、延迟弹窗（5分钟/15分钟/30分钟/1小时/明天/自定义）
- `Dashboard.vue` 顶部新增「周期提醒」按钮，显示过期+今天+近7天到期任务数量徽章
- `permissions.js` 新增 reminder 菜单和 5 个按钮权限
- `router/index.js` 新增 `/reminder` 路由
- `api/index.js` 新增 `reminderApi`（11 个方法）
- `SecurityConfig` 新增 `/reminder/**` 认证规则（已登录用户可访问）

**1. 仪表盘备忘录**
- `user_memo` 表（username UNIQUE, content TEXT），按用户维度存储
- `UserMemoController`：`GET /memo`（读取）、`POST /memo`（保存），需登录认证
- `UserMemoService`：getMemo/saveMemo，不存在时自动创建
- `DatabaseInitConfig` Migration 52：创建 user_memo 表
- `Dashboard.vue` 顶部新增「备忘录」按钮（Notebook 图标），弹窗文本编辑器
- 支持 Ctrl+S 快捷键保存，保存成功后自动关闭弹窗
- `api/index.js` 新增 `memoApi`（get/save）

**2. 侧边栏版本展示**
- `VersionController`：`GET /version`（公开，无需认证），返回 `{ backend, frontend }`
- `application.yml` 新增 `app.version: 1.1.0`
- `MainLayout.vue` 侧边栏 Logo 下方显示版本号（如 `1.1.0 / 1.1.0`）
- `api/index.js` 新增 `versionApi`（get）
- `SecurityConfig` 新增 `/version` permitAll 规则

### Bug修复

**3. 变量敏感值编辑保存后变为空**
- **问题**：编辑 `isSecret=true` 的变量时，前端显示脱敏值 `****`，保存后值被覆盖为空
- **修复**：`GlobalVariableService.updateVariable()` 检测 `isSecret=true` 且 value 包含 `****` 时跳过值更新；`VariableList.vue` 编辑时清空 value 输入框，提示"留空则不修改原值"
- **文件**：`GlobalVariableService.java`、`VariableList.vue`

**4. 状态公示页 logo 不一致**
- **问题**：StatusPage.vue 使用内联 SVG，与侧边栏 `<img src="/vite.svg">` 不一致
- **修复**：StatusPage.vue 改为 `<img src="/vite.svg">`，CSS 中 `.brand-icon` 移除 `color` 属性，`svg` 选择器改为 `img`
- **文件**：`StatusPage.vue`

**5. 代理 enabled/active 字段不一致（Linux 环境）**
- **问题**：Linux 环境下代理测试成功但监控请求不走代理。根因是 `enabled=0, active=1` 的脏数据，`refreshActiveProxy()` 要求 `enabled=1` 才加载，导致活跃代理被忽略
- **修复**：
  - `ProxyConfigService.refreshActiveProxy()` 移除 `enabled` 门控，仅检查 `active=true`
  - `ProxyConfigService.setActive()` 激活时同步设置 `enabled=true`
  - `ProxyConfigService.create()` 新建活跃代理时自动停用其他代理
  - `ExecutionService.resolveActiveProxyConfig()` 增加 self-healing：内存缓存为空但数据库有 active=true 记录时自动重新加载
  - `ExecutionService.getClient()` SSL 设置改为独立 try/catch，防止 SSL 失败拖垮代理配置
  - `DatabaseInitConfig` Migration 51：修复 `active=1 AND enabled=0` 的脏数据
  - 移除死代码：`ProxyConfigService.getActiveProxy()` 方法和 `activeProxy` 字段
- **文件**：`ProxyConfigService.java`、`ExecutionService.java`、`DatabaseInitConfig.java`

**6. 后端版本号升级**
- `pom.xml` version: `1.0.0` → `1.1.0`
- `package.json` version: `1.0.0` → `1.1.0`
- `application.yml` 新增 `app.version: 1.1.0`

### 新增/修改文件

| 文件 | 变更 |
|------|------|
| `UserMemo.java` | 新建实体（id, username, content, createdAt, updatedAt） |
| `UserMemoMapper.java` | 新建 Mapper |
| `UserMemoService.java` | 新建 Service（getMemo, saveMemo） |
| `UserMemoController.java` | 新建 Controller（GET /memo, POST /memo） |
| `VersionController.java` | 新建 Controller（GET /version） |
| `DatabaseInitConfig.java` | Migration 51（proxy enabled 修复）、Migration 52（user_memo 表） |
| `ProxyConfigService.java` | refreshActiveProxy 移除 enabled 门控；setActive 设置 enabled=true；create 停用其他；移除死代码 |
| `ExecutionService.java` | resolveActiveProxyConfig self-healing；getClient SSL 独立 try/catch |
| `GlobalVariableService.java` | updateVariable 跳过 masked 值 |
| `SecurityConfig.java` | /version permitAll |
| `application.yml` | app.version: 1.1.0 |
| `pom.xml` | version 1.1.0 |
| `package.json` | version 1.1.0 |
| `Dashboard.vue` | 备忘录按钮 + 弹窗编辑器 |
| `MainLayout.vue` | 侧边栏版本号展示 |
| `StatusPage.vue` | logo 改为 img |
| `VariableList.vue` | 敏感值编辑清空 + 提示文本 |
| `api/index.js` | memoApi、versionApi |

---

## 新增: 监控级失败告警 + 任务失败判定策略 + 告警模版兜底通道 + IP来源配置 + 备份流式下载 + UI优化

### 新增/修复内容

**1. 监控级失败告警**
- Monitor 实体新增 `alertEnabled`（是否启用告警）、`alertConsecutiveCount`（连续失败阈值）、`alertConfigIds`（告警模版ID列表）
- `ExecutionService` 使用 Caffeine 缓存跟踪每个监控项的连续失败次数，达到阈值时触发告警
- MonitorList.vue 新增监控级告警配置区域：开关、连续失败次数、告警模版多选

**2. 任务失败判定策略**
- MonitorGroup 实体新增 `failCriteriaType`（ANY/COUNT/PERCENT）、`failCountThreshold`、`failPercentThreshold`
- `GroupExecutionService` 按配置判定任务是否失败：ANY=任一失败、COUNT=失败数≥阈值、PERCENT=失败率≥阈值
- GroupList.vue 新增失败判定条件表单

**3. 告警模版兜底通道**
- AlertTemplate 实体新增 `fallbackChannelIds`（兜底通道ID列表）
- 当触发方未配置告警通道时，使用模版的兜底通道发送
- AlertTemplateList.vue 新增兜底通道多选框和信息提示

**4. IP来源配置（数据库）**
- `security_setting` 表新增：`ip_source_header`（请求头名）、`ip_strict_mode`（严格模式）、`xff_trusted_proxies`（可信代理）
- 新建 `IpUtils.java` 工具类：缓存配置、解析 XFF/Real-IP、校验IP格式、可信代理判断
- `AuditLogService` 和 `JwtAuthenticationFilter` 使用 `IpUtils.getClientIp()` 获取真实IP
- `ProxyConfigInit` 启动时初始化 IpUtils 缓存
- SecuritySettings.vue 新增IP来源配置表单区（含问号气泡提示）

**5. 备份流式下载**
- 新建 `POST /backup/download-token` 接口，生成5分钟有效的临时下载令牌
- `GET /backup/download` 支持可选 `token` 查询参数，令牌验证后返回文件流
- `BackupList.vue` 改用令牌方式下载（先获取token，再浏览器原生下载）

**6. AlertChannel测试按钮loading状态**
- AlertChannelList.vue `sendTest` 方法增加 `testingId` ref，测试中按钮显示loading

**7. 代理日志增强**
- `ExecutionService` 使用代理时输出 `log.info` 记录代理类型、主机、端口

**8. Sidebar滚动修复**
- MainLayout.vue `.sidebar` 改为flex纵向布局，`.el-menu` 设置 `flex:1; overflow-y:auto`

**9. 执行日志详情布局优化**
- LogList.vue URL行改为整行（`word-break:break-all`），腾出空间显示IP地址字段

**10. Schema编辑器数组项修复**
- `SchemaFieldRow.vue` 新增 `isArrayItem` prop，数组项隐藏name输入（显示"元素"标签），隐藏拖拽/新增/删除
- `emptyField()` 新增 `_uid` 计数器，draggable `item-key` 改用 `_uid` 修复子字段焦点丢失

**11. Element Plus废弃修复**
- `el-checkbox` 的 `label` 全部改为 `value`（MonitorList.vue、DomainList.vue、GroupList.vue）
- `rows="2"` 改为 `:rows="2"`（ApiSchemaList.vue、InspectionList.vue）
- `editingMonitor` 改为 `editingId`（MonitorList.vue）
- `el-switch` modelValue 绑定 `enabled: !!schema.enabled`（ApiSchemaList.vue）

### 修改文件

| 文件 | 变更 |
|------|------|
| `Monitor.java` | 新增 alertEnabled/alertConsecutiveCount/alertConfigIds |
| `MonitorDTO.java` | 同上 |
| `MonitorGroup.java` | 新增 failCriteriaType/failCountThreshold/failPercentThreshold |
| `MonitorGroupDTO.java` | 同上 |
| `AlertTemplate.java` | 新增 fallbackChannelIds |
| `AlertTemplateDTO.java` | 同上 |
| `AlertTemplateController.java` | 创建/更新映射 fallbackChannelIds |
| `ExecutionService.java` | 监控级连续失败告警；代理日志 |
| `GroupExecutionService.java` | 任务失败判定策略（ANY/COUNT/PERCENT） |
| `IpUtils.java` | 新建：IP来源配置解析和缓存 |
| `AuditLogService.java` | 使用IpUtils获取真实IP |
| `JwtAuthenticationFilter.java` | 使用IpUtils获取真实IP |
| `ProxyConfigInit.java` | 启动初始化IpUtils |
| `BackupController.java` | 新增download-token接口；download支持token参数 |
| `DatabaseInitConfig.java` | 迁移V47-V50 |
| `MonitorList.vue` | 监控告警表单；editingId修复 |
| `GroupList.vue` | 失败判定表单；checkbox value修复 |
| `AlertTemplateList.vue` | 兜底通道多选 |
| `AlertChannelList.vue` | 测试按钮loading |
| `BackupList.vue` | 令牌式下载 |
| `LogList.vue` | 详情布局优化 |
| `SecuritySettings.vue` | IP来源配置表单 |
| `MainLayout.vue` | Sidebar滚动修复 |
| `SchemaFieldRow.vue` | _uid修复焦点；isArrayItem |
| `ApiSchemaList.vue` | enabled布尔值；rows类型 |
| `InspectionList.vue` | rows类型 |
| `DomainList.vue` | checkbox value |

---

## 修复: FormData格式错误 + POST请求body为空500 + 监控任务并行执行优化

### 修复内容

**1. FormData请求体格式错误（JSON而非URL-encoded）**
- **原因**：前端 `bodyType=form` 时将 formParams 转为 JSON 字符串存储（`{"key":"value"}`），后端 `ExecutionService` 原样发送，但 Content-Type 为 `application/x-www-form-urlencoded`，导致服务端收到的是 JSON 而非 `key=value&key=value` 格式
- **修复**：`ExecutionService.java` 在构建请求体时，检测 `bodyType=form` 且 Content-Type 为 `application/x-www-form-urlencoded` 且 body 以 `{` 开头，自动将 JSON 转为 URL-encoded 格式（`URLEncoder.encode`）
- **影响范围**：所有 bodyType=Form-Data 的 POST/PUT 监控项

**2. POST/PUT请求无body导致500错误**
- **原因**：`ExecutionService.java` 先以 `method("POST", null)` 构建请求，后续仅在 `body != null && !body.isEmpty()` 时才通过 `.post(requestBody)` 设置body。当body为空时，OkHttp验证失败抛出 `method POST must have a request body`
- **修复**：重构请求构建逻辑，POST/PUT/PATCH 请求始终设置body（空body使用空字符串），不再依赖条件判断是否附加body；同时增加 PATCH 方法支持
- **影响范围**：所有POST/PUT监控项测试和定时执行

**2. 监控任务并行执行优化**
- **背景**：任务下排序号=0的监控项无顺序依赖，串行执行浪费时间
- **方案**：`GroupExecutionService` 重写执行逻辑
  - `sortOrder = 0`（含null）的监控项使用 `CompletableFuture.supplyAsync` 并发执行
  - `sortOrder > 0` 的监控项按排序号升序串行执行
  - 并行组内任意一个失败 → `hasFailure = true`，但不中断其他并行任务
  - 串行组失败时检查 `continueOnFail` 决定是否中断
- **日志增强**：输出并行/串行分组数量和执行方式

### 修改文件

| 文件 | 变更 |
|------|------|
| `ExecutionService.java` | POST/PUT/PATCH 始终设置body；FormData JSON→URL-encoded自动转换 |
| `GroupExecutionService.java` | 注入 taskExecutor；并行+串行分组执行逻辑 |

---

## 修复: 9项Bug修复 + 告警模版限频持久化 + 代理测试 + 变量引擎增强

### 修复内容

**1. 告警模版限频字段不保存**
- **原因**：`AlertTemplateDTO` 缺少 4 个字段（`rateLimitEnabled`、`rateLimitCount`、`recoveryNotify`、`recoveryConsecutiveCount`），且 `AlertTemplateController` 的 create/update 方法未映射这些字段
- **修复**：`AlertTemplateDTO.java` 补全 4 字段；`AlertTemplateController.java` create/update 方法增加 setter 调用

**2. Schema 可视化编辑器输入失焦**
- **原因**：`draggable` 的 `item-key="name"` 导致每次修改字段名时 Vue 重建组件；`onFieldUpdate` 未调用 `syncVisualToJson()`
- **修复**：每个 field 对象增加唯一 `_uid` 字段，`item-key` 改为 `_uid`；`onFieldUpdate` 使用 `Object.assign` 原地更新 + 300ms 防抖同步

**3. 变量正则不支持连字符**
- **原因**：`VARIABLE_PATTERN` 使用 `\w+`，不匹配 `applet-test-host` 等含 `-` 的变量名
- **修复**：正则改为 `\w[\w\-]*`，支持变量名中包含连字符

**4. 任务变量回退保存前缀错误**
- **原因**：`VariableEngine.extractVariablesWithTrace` 的 catch 块中，`group.` 前缀的变量名未剥离就保存，导致加载时变为 `group.group.xxx`
- **修复**：catch 块中对 `global.` 和 `group.` 前缀都做正确剥离

**5. 监控项创建500错误**
- **原因**：`MonitorDTO` 缺少 `showOnStatusPage` 字段；表单编辑后 `id` 残留导致创建请求携带旧 id
- **修复**：`MonitorDTO` 增加 `showOnStatusPage` 字段；`submitForm` 用副本并删除 `id`/`createdAt`/`updatedAt`/`createdBy`/`groupCount`

**6. 监控项编辑后 Form Data 参数丢失**
- **原因**：`showDialog` 编辑分支未从 `row.body` 恢复 `formParams`
- **修复**：编辑时若 `bodyType === 'form'`，解析 body JSON 还原为 formParams 数组

**7. 公开状态页日期格式500错误**
- **原因**：`el-date-picker` 默认传 Date 对象（序列化为毫秒时间戳），后端 `LocalDateTime` 反序列化失败
- **修复**：`el-date-picker` 增加 `value-format="YYYY-MM-DD"`，`buildSubmitForm` 拼接时间字符串

**8. 变量提取仅在验证通过时执行**
- **原因**：`ExecutionService` 第283行条件 `validationResult.valid && ...`，导致验证失败时不提取变量
- **修复**：移除 `validationResult.valid` 条件，只要有提取配置就执行

**9. 代理测试500 + 邮件测试失败**
- **代理测试**：测试 URL 从 HTTPS 改为 HTTP（`http://httpbin.org/ip`），兼容本地代理
- **邮件测试**：增加端口465 SSL支持（`mail.smtp.ssl.enable`），超时从10s提升到30s

### 新增功能

**代理测试按钮**
- 后端：`ProxyConfigController` 新增 `POST /proxy/{id}/test`，使用 `java.net.http.HttpClient` + `ProxySelector` 验证代理连通性
- 前端：`ProxyList.vue` 操作列增加「测试」按钮，`proxyApi` 增加 `test` 方法，`permissions.js` 增加 `proxy:test` 权限
- 移除了无效的「启用」开关列（实际由操作列的启用/停用控制）

### 修改文件

| 文件 | 变更 |
|------|------|
| `AlertTemplateDTO.java` | +4 字段 |
| `AlertTemplateController.java` | create/update 映射限频字段 |
| `MonitorDTO.java` | +showOnStatusPage 字段 |
| `VariableEngine.java` | 正则支持连字符；catch 块修复前缀剥离 |
| `ExecutionService.java` | 移除变量提取的验证通过条件 |
| `ProxyConfigController.java` | +test 端点 |
| `AlertService.java` | 邮件 SSL/超时优化 |
| `JsonSchemaEditor.vue` | _uid 稳定键 + 防抖同步 |
| `MonitorList.vue` | body 区块位置、formParams 恢复、submit 副本清理 |
| `AlertSilenceList.vue` | 日期格式修复 |
| `ProxyList.vue` | 移除启用开关、增加测试按钮 |
| `api/index.js` | +proxyApi.test |
| `permissions.js` | +proxy:test |

---

## 新功能: 可配置公开状态页 + 状态页重构 + 多主题换肤

### 公开状态页改为可配置
**背景**：原状态页固定公开（`/status` 无需认证），存在信息暴露风险；且无法按监控项筛选展示内容。

- **系统级开关**：`security_setting` 新增 `status_page_public`（默认 `false`）。`SecuritySettingsService.initDefaultSettings()` 初始化，`isStatusPagePublic()` 读取
  - 关闭时匿名访问 `/status` 返回 403；已登录用户仍可访问
  - 前端「系统管理 → 安全设置 → 公开状态页」新增开关（悬浮图标说明）
- **监控项级开关**：`monitor` 表新增 `show_on_status_page`（默认 `1`，Migration 46）
  - `Monitor.java` 新增 `showOnStatusPage` 字段
  - `StatusPageService` 查询过滤 `AND m.show_on_status_page = 1`，整体状态统计同步过滤
  - 前端监控项添加/编辑弹窗「超时」后新增「公开状态页展示」开关 + 悬浮说明
- **两层控制**：系统开关开启 + 监控项开关开启，该监控项才在公开状态页展示

### 状态页界面重构
- `StatusPage.vue` 完全重写：深色科技风（`#0f1923` 渐变背景）
- 卡片网格布局：CSS Grid 自适应 1-4 列（≤640px 1 列 / ≤960px 2 列 / ≤1280px 3 列 / >1280px 4 列），解决原单列列表空间利用率低的问题
- 视觉增强：渐变标题、毛玻璃状态徽章、状态圆点脉冲动画、卡片悬浮上移、顶部状态光带
- 交互增强：30 秒自动刷新（`setInterval` + `onUnmounted` 清理）

### 多主题换肤
- 新增 `stores/theme.js`（Pinia）：6 套主题（默认/深色/翠绿/极光紫/中国红/活力橙），`localStorage` 持久化
- `MainLayout.vue` 顶栏用户信息左侧新增换肤入口（画笔图标 + el-popover 主题网格）
- `styles/index.scss` 新增各主题 CSS 变量（`--primary-color`、`--bg-color`、`--card-bg`、`--text-color`、`--border-color` 等）
- `main.js` 启动时读取 `localStorage` 立即应用主题类，避免闪烁
- 侧边栏/顶栏颜色改用 CSS 变量，随主题切换

### 深色模式 Element Plus 适配
- **表格**：斑马纹行、悬浮行、当前行背景改为不透明实色；**固定列**改用正确的 Element Plus 2.x 类名 `.el-table-fixed-column--left/right`（原 `.el-table__fixed-right` 为 Element UI 旧结构，无效），修复斑马纹行透明穿透问题
- **弹窗表单**：`el-input`、`el-textarea`、`el-input-number`、`el-select__wrapper`、`el-dialog` 头部/底部、`el-divider`、`el-tabs`、`el-collapse` 等
- **业务组件**：`el-descriptions`（SSL 证书弹窗表格）、`code-block`、`sign-panel`、`editor-toolbar`、`section-block`、`JsonSchemaEditor`
- **其他**：`el-pagination`、`el-select-dropdown`、`el-popper`、`el-alert`、`el-empty`

---

## 安全加固: 全面安全审计修复

**背景**：对项目进行安全审计，发现多项安全隐患（供应链、越权、SQL注入、接口鉴权、脚本引擎沙箱等），逐项修复。

### CRITICAL 修复
- **JWT 密钥日志泄露**：移除 `JwtTokenProvider.java` 中 `log.info` 输出 MAC 哈希前 16 位的行为，改为 warn 级别提示设置 `JWT_SECRET` 环境变量
- **GraalVM 脚本引擎沙箱化**：`PreRequestScriptEngine.java` 将 `allowAllAccess(true)` 改为 `HostAccess.UNTRUSTED`，禁止 `Runtime.exec()`、`ProcessBuilder` 等危险 API 调用；增加 `js.commonjs-require=false`、`js.strict=true` 限制

### HIGH 修复
- **全局变量 isSecret 脱敏**：`GlobalVariableService` 新增 `maskSecretValue()` 方法，`isSecret=true` 的变量在读接口返回脱敏值（前 2 位 + `****` + 后 2 位），前端表格不再明文展示
- **SSRF 防护**：`ExecutionService` 新增 `isInternalHost()` 方法，拦截 `localhost`、`127.0.0.1`、`::1`、内网 IP、`169.254.x.x`（云元数据地址）；通过 `monitor.ssrf-protection` 配置开关控制（默认开启）
- **审计日志权限收紧**：`AuditLogController` 增加 `@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")`，VIEWER 角色不再可查看
- **巡检/Schema 权限补全**：`InspectionController` 的 `POST /config`、`PUT /config/{id}`、`DELETE /config/{id}`、`POST /run/{configId}`；`ApiSchemaController` 的 `POST`、`PUT /{id}`、`DELETE /{id}` 均增加 `@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")`

### MEDIUM 修复
- **SQL 拼接白名单**：`DashboardService.getUrlResponseTimeTrend()` 对 `granularity` 参数增加白名单校验，非法值回退为 `hour`
- **SSL 验证可配置**：`ExecutionService` 的 SSL 跳过验证改为通过 `monitor.ssl-verify-disabled`（默认 `true`）控制；`application.yml` 新增对应配置项
- **密码确认校验**：`UserService.updateProfile()` 增加 `newPasswordConfirm` 参数校验，两次密码不一致时抛出异常
- **密码策略增强**：密码最短长度 6 → 8 位，新增大写+小写+数字组合要求
- **CORS 可配置化**：`SecurityConfig` 的 `allowedOrigins` 从硬编码改为读取 `cors.allowed-origins` 环境变量（默认 `localhost:5173,localhost:3000`）
- **文件名清洗**：`BackupController.uploadAndRestore()` 对上传文件名做正则清洗，移除路径分隔符和目录穿越字符

### 依赖升级
- `hutool-all` 5.8.25 → **5.8.32**（修复已知 CVE）
- `axios` ^1.6.7 → **^1.7.0**（修复 prototype pollution）
- `OkHttp` 保持 4.12.0（4.12.1 尚未发布）

### 新增配置项（application.yml）
```yaml
monitor:
  ssl-verify-disabled: true    # 是否禁用监控请求的 SSL 证书验证
  ssrf-protection: true        # 是否启用 SSRF 防护

cors:
  allowed-origins: http://localhost:5173,http://localhost:3000
```

---

## 安全加固: 前端权限漏洞修复 + 冗余代码清理

### 前端权限漏洞修复
- **CRITICAL**：MonitorList.vue 启用/禁用开关、AlertTemplateList.vue 启用/禁用开关、MonitorList.vue 分组「失败继续」开关——三个 el-switch 均缺少 `v-permission`，VIEWER 可执行写操作。已补全权限指令
- **HIGH**：Backup 页面菜单 `roles` 为空（所有角色可见），限制为 `['ADMIN']`
- **MEDIUM**：`checkButton()` 未定义权限默认允许，改为默认拒绝（`return false`）
- **LOW**：删除废弃的 `AlertList.vue`（未被路由引用，无权限检查）

### 后端数据泄露修复
- `ProxyConfigController` 的 `GET /list` 和 `GET /{id}` 增加 `@PreAuthorize("hasRole('ADMIN')")`（代理密码泄露）
- `AlertChannelController` 的 `GET /list` 和 `GET /{id}` 增加 `@PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")`（webhook 等配置泄露）

### 冗余代码清理
**后端删除**：
- `UserRole` 枚举（从未被引用）
- `ResponseTimeAlertService.alertConfigMapper`（未使用注入）
- `ExecutionService.paramRefs`（赋值后从未读取）
- `GroupExecutionService.countGroups()/countEnabledGroups()`（与 `MonitorGroupService` 重复）
- `AlertSilenceService.isRecurringMatch()`（实现但从未调用）
- `ExecutionLogService.countLogs()`、`MonitorService.countMonitors()`、`DomainAssetService.countAliveDomains()`、`UserSessionService.isAccountLocked()`（均未被调用）
- `V35__monitor_dependency.sql`（建表后 Migration 43 立即删除）

**前端删除**：
- `authApi`（3 个方法，由 stores/user.js 直接 axios 替代）
- `alertApi` 4 个未使用方法、`variableApi` 2 个、`schemaApi` 3 个、`logApi` 2 个、`securityApi` 1 个（共 13 个 API 方法）
- `formatTimeShort`、`formatTimeFull`（与 `formatTime()` 重复）

---

## 优化: 100+ 检测项性能与稳定性（P0 + P1）

**背景**：评估 100 个检测项时的性能，定位到变量引擎重复全量查询、每次执行 SSL 握手、SQLite 写并发未调优、日志表无限增长等问题，做如下优化（不含新功能）。

### 执行链路优化
- **变量引擎**：新增 `loadVariables(groupId)`，单次监控执行只加载一次 global + group 变量，后续替换复用；每监控 DB 查询由 ~12-14 次降至 2 次
- **SSL 检查 TTL 缓存**：`SslService` 按 `domain:port` 缓存检查时间，TTL 内不再握手（`monitor.ssl-check-interval-minutes`，默认 360 分钟）
- **域名资产记录节流**：DNS 解析 + `domain_asset`/`domain_ip_history` 更新按域名降频（`monitor.domain-asset-update-interval-minutes`，默认 10 分钟）
- **写路径合并**：Schema 校验/响应时间告警改为在 insert 前完成，单次执行由「insert + update」两次写降为**一次写**
- **`toJson` 复用单例 ObjectMapper**，避免热路径反复 `new ObjectMapper()`
- **OkHttp 连接池** 10 → 50

### SQLite 与数据治理
- **WAL 模式**：启动时 `PRAGMA journal_mode=WAL` + `synchronous=NORMAL`，提升并发读写
- **`busy_timeout=5000`**：Hikari `connection-init-sql` 增加忙等待，降低 `SQLITE_BUSY`
- **连接池** 10 → 20
- **备份前 WAL checkpoint**：`BackupController` 创建备份前执行 `wal_checkpoint(TRUNCATE)`，确保备份文件完整
- **自动数据保留**：新增 `DataRetentionService`，每日 03:30 清理超过 `monitor.log-retention-days`（默认 30 天）的执行日志与告警日志
- **请求/响应体入库截断**：超过 `monitor.log-body-max-size`（默认 256KB）截断，控制 DB 膨胀

### 其他
- **告警静默查询缓存**：`AlertSilenceService` 启用规则列表加 30 秒缓存，增删改即时失效
- **清理孤儿表**：Migration 45 删除无任何实体/Mapper 引用的 `report_config`、`report_history`、`api_schema_v2`
- **前端提示文案**：域名证书页、执行日志页、使用手册新增缓存/数据保留说明，告知操作人部分变更非即时生效

**配置项（application.yml `monitor.*`）**：`ssl-check-interval-minutes`、`domain-asset-update-interval-minutes`、`log-body-max-size`、`log-retention-days`

---

## 修复: 时间字段读写不一致导致巡检历史开始时间为空

**问题**：巡检历史列表「开始时间」无值。根因是自定义 `SQLiteDateTimeHandler` 实际未生效（新写入仍为 `2026-09-12T13:33:23.415825700`），而 Migration 41 已把历史数据转成空格分隔，默认 MyBatis 读取器只能解析 ISO 的 T 分隔格式，空格格式解析为 null。

**修复**：
- `MyBatisPlusConfig.java` — 移除自定义 `SqlSessionFactory` Bean（未生效），改用 MyBatis-Plus 官方扩展点 `ConfigurationCustomizer` 注册 TypeHandler
- `SQLiteDateTimeHandler.java` — 移除 `@MappedJdbcTypes`（导致注册到特定 JdbcType 键、被默认处理器覆盖）；`parse()` 改为兼容 T/空格 + 小数秒，统一截断为 `yyyy-MM-dd HH:mm:ss`
- `DatabaseInitConfig.java` — 新增 **Migration 44**：所有时间列 `substr(replace(col,'T',' '),1,19)`，统一为空格分隔、无小数秒

**验证**：巡检历史 `startedAt`/`completedAt` 正常返回；新写入 `2026-09-12 13:37:01`；全表 T 分隔残留为 0；时间范围筛选、仪表盘 Schema/可用性统计均正常。

---

## 移除: 依赖拓扑功能

**原因**：依赖拓扑功能与本系统的实际使用场景不匹配。该功能仅做关系可视化，不参与任何告警/执行逻辑；自动发现把「任务内执行顺序」误判为服务依赖，易误导；且影响分析未接入 UI、存在结果重复等缺陷。综合评估后决定彻底移除。

**数据库迁移**：Migration 43（`DROP TABLE IF EXISTS monitor_dependency`），同时移除 Migration 35 建表逻辑

**后端删除**：
- `DependencyController.java` / `DependencyService.java` / `MonitorDependency.java` / `MonitorDependencyMapper.java`
- `DatabaseInitConfig.java` — 移除 Migration 35 建表块、Migration 41 fixes 中的 `monitor_dependency` 行

**前端删除**：
- `DependencyTopology.vue`
- `router/index.js` — 移除 `/dependency` 路由
- `permissions.js` — 移除 `dependency` 菜单项及 `dependency:create/delete` 按钮权限
- `api/index.js` — 移除 `dependencyApi`
- `Manual.vue` — 移除「依赖拓扑」说明章节

**文档更新**：`README.md` / `docs/overview.md` / `docs/architecture.md` / `docs/operation.md` 同步移除功能描述

---

## 优化: 时间格式统一 + Schema 查询性能 + 仪表盘跳转与持久化

### 时间格式统一（去除 T 分隔符）

**问题**：SQLite 无原生日期类型，MyBatis-Plus 默认将 `LocalDateTime` 以 ISO 格式（`2026-09-12T11:05:52`）写入，而 SQLite 内置 `datetime()` 函数返回空格分隔格式（`2026-09-12 11:05:52`），导致字符串比较错乱；同时前端 `toISOString()` 返回 UTC，与库中北京时间相差 8 小时。

**数据库迁移**：Migration 41（批量修复存量数据）、Migration 42（补充索引）

**后端改动**：
- `SQLiteDateTimeHandler.java`（新增）— MyBatis TypeHandler，写入统一格式化为 `yyyy-MM-dd HH:mm:ss`，读取兼容 T/空格两种历史格式
- `DateTimeUtil.java`（新增）— `now()` / `format()` 工具方法，统一空格分隔格式
- `JacksonConfig.java`（新增）— Jackson 自定义 `LocalDateTime` 序列化/反序列化器
- `MyBatisPlusConfig.java` — 自定义 `SqlSessionFactory` Bean，显式注册 `SQLiteDateTimeHandler`
- `LitVMonitorApplication.java` — 移除重复的 `@MapperScan`
- `ExecutionLogService.java` — 移除 `REPLACE(executed_at, 'T', ' ')` 查询 workaround，改用普通 `ge/le`
- `DatabaseInitConfig.java` — Migration 41 遍历 70+ 时间列执行 `REPLACE(col, 'T', ' ')`

**前端改动**：
- `LogList.vue` — 时间范围改用 `formatTime()`（北京时间）替代 `toISOString()`（UTC），修复跳转后时间差 8 小时问题

---

### Schema 统计查询性能优化

**问题**：仪表盘 Schema 检测卡片执行 8 次独立 COUNT 查询，且查询条件带 `REPLACE()` 函数导致索引失效，`LIKE '%XXX%'` 全表扫描。

**数据库迁移**：Migration 42

**改动**：
- `DatabaseInitConfig.java` — 新增索引 `idx_execution_log_schema_status(schema_check_status)`、`idx_execution_log_executed_schema(executed_at, schema_check_status)`
- `DashboardService.getSchemaCheckStats()` — 8 次 COUNT 合并为 1 次 `CASE WHEN` 聚合查询；去掉 `REPLACE()` 函数改用 `executed_at >= ?` 走索引
- `DashboardService.getUptimeStats()` — 同样去掉 `REPLACE()` + `datetime()` 包装

**效果**：Schema 统计 45ms、Uptime 统计 16ms（原多次全表扫描）

---

### 仪表盘可用性统计点击跳转

**改动文件**：
- `ExecutionLogController.java` / `ExecutionLogService.java` — `/log/list` 新增 `status` 过滤参数
- `LogList.vue` — 新增状态下拉框；接收 `status` query 参数
- `Dashboard.vue` — 新增 `goLogUptime(status)`，成功/失败/总次数可点击跳转

**跳转规则**：成功 → `status=SUCCESS`、失败 → `status=FAIL`、总次数 → 全部，均携带当前时间窗口（`hours`）

---

### 仪表盘时间窗口持久化

**改动文件**：
- `Dashboard.vue` — 可用性统计、Schema 检测的时间窗口选择通过 `localStorage` 持久化（key：`dashboard_uptimeHours`、`dashboard_schemaHours`），切换菜单返回仪表盘时自动恢复上次选择

---

## 新功能: JSON Schema 可视化编辑器 + Schema 校验状态 + 巡检 Schema 变更计数

### JSON Schema 可视化编辑器

**新增组件**：
- `JsonSchemaEditor.vue` — 双模式可视化编辑器（可视化 + JSON 文本）；工具栏增强：快速添加模板（分页/RESTful/用户/时间字段）；空状态引导
- `SchemaFieldRow.vue` — 递归字段行组件：两行布局（主控件行 + 扩展选项行）、必填红星标识（点击切换）、object/array 折叠展开（显示子字段数+必填数）、拖拽排序手柄
- `vuedraggable@^4.1.0` — 字段拖拽排序依赖

**集成**：
- `ApiSchemaList.vue` — Schema 编辑弹窗 textarea 替换为 JsonSchemaEditor，保存时校验 Schema 格式
- `MonitorList.vue` — 期望 Schema textarea 替换为 JsonSchemaEditor，保存时校验 Schema 格式

**核心特性**：
- 树形视图：递归渲染字段树，每个字段可配置类型、名称、描述、必填、枚举值、默认值
- 类型切换：object/array 自动管理子字段/items；string/integer/number/boolean/null 直接切换
- 双模式切换：树形视图 ↔ JSON 编辑，切换时自动同步数据
- 校验：保存前校验根节点类型、required 数组、properties 结构等

---

### 执行日志 Schema 校验状态

**数据库迁移**：Migration 39

**后端改动**：
- `ExecutionLog.java` — 新增 `schemaCheckStatus` 字段
- `ExecutionService.checkSchemaAlert()` — 执行时记录 Schema 校验状态到主日志条目（`未启用`/`未配置Schema`/`非JSON响应`/`无响应体`/`无变更`/`无匹配变更` 或逗号分隔的变更类型如 `ADDED,MODIFIED`）
- 校验状态在 `updateById` 后写入，确保每次执行都记录

**前端改动**：
- `LogList.vue` — 执行日志列表新增「Schema」列，显示校验状态；详情弹窗显示完整状态信息

---

### 巡检 Schema 变更信息

**数据库迁移**：Migration 39（续）

**后端改动**：
- `InspectionDetail.java` — 新增 `schemaChangeInfo` 字段（TEXT，存储该监控项的 Schema 变更详情）
- `InspectionHistory.java` — 新增 `schemaChangeCount` 字段（INTEGER，本次巡检的 Schema 变更总数）
- `InspectionService.runInspection()` — 巡检完成后从 details 统计变更数写入 `history.schemaChangeCount`；每个 detail 记录其 Schema 变更信息

**前端改动**：
- `InspectionList.vue` — 历史列表新增「Schema」列（橙色数字 tag 表示有变更）；报告弹窗新增「Schema变化」统计卡片

---

### Schema 历史持久化修复

**问题**：`api_schema_history` 表的外键 `FOREIGN KEY (schema_id) REFERENCES api_schema(id) ON DELETE CASCADE` 导致每次 Migration 38 重建 `api_schema` 表时级联删除所有历史记录

**修复**：Migration 40 重建 `api_schema_history` 表，移除 `ON DELETE CASCADE` 约束，历史记录在 Schema 删除/重建后仍保留

---

### 其他改动

- `JsonSchemaEditor.vue` — 移除 JSON 预览面板（模式切换已提供该能力）
- `MonitorDTO.java` — 新增 `schemaAlertEnabled`、`expectedSchemaJson`、`schemaAlertChangeTypes`、`schemaAlertChannelIds` 字段

---

## 新功能: 告警渠道扩展 + Uptime + 状态页 + Schema + 报告 + 巡检 + 拓扑

### 告警渠道平台扩展

**新增平台**：钉钉、企业微信、飞书

**改动文件**：
- `ChannelType.java` — 新增 DINGTALK, WECHAT, FEISHU 枚举值
- `AlertService.java` — 新增 `sendDingTalk()`, `sendWechat()`, `sendFeishu()` 方法
- `AlertChannelList.vue` — 根据平台类型动态切换配置表单

**配置说明**：
- 钉钉：Webhook URL + 关键词安全设置
- 企业微信：Webhook URL + Markdown 消息格式
- 飞书：Webhook URL + 富文本消息格式

---

### Uptime 统计

**改动文件**：
- `DashboardService.java` — `getUptimeStats()` 计算 1h/6h/24h/7d/30d 可用率
- `DashboardController.java` — `GET /dashboard/uptime`
- `Dashboard.vue` — Uptime 卡片 + 时间窗口切换

---

### 公开状态页

**改动文件**：
- `StatusPageController.java` — `GET /status`（无需认证）
- `StatusPageService.java` — 聚合监控状态、SSL证书、最近告警
- `SecurityConfig.java` — `/status/**` permitAll
- `StatusPage.vue` — 公开状态页前端
- `router/index.js` — `/status` 路由 `requiresAuth: false`

---

### API Schema 变更检测

**数据库迁移**：Migration 28-29

**新增表**：
- `api_schema` — API Schema 定义（name, api_path, method, schema, enabled, last_check_at）
- `api_schema_history` — Schema 变更历史（schema_id, change_type, field_path, old_value, new_value, detected_at）

**新增文件**：
- `ApiSchema.java` / `ApiSchemaHistory.java` — 实体
- `ApiSchemaMapper.java` / `ApiSchemaHistoryMapper.java` — Mapper
- `ApiSchemaService.java` — 自定义 JSON Schema 校验 + 变更检测
- `ApiSchemaController.java` — CRUD + 校验 + 历史
- `ApiSchemaList.vue` — 前端页面

**变更类型**：ADDED / REMOVED / MODIFIED / BREAKING

---

### API Schema 独立管理 + 监控项集成 + 历史版本对比

**数据库迁移**：Migration 38（去除 api_schema.monitor_id，monitor 表新增 4 个 schema 告警字段）

**后端改动**：
- `ApiSchema.java` — 去掉 monitorId 字段，Schema 与监控项解耦
- `Monitor.java` — 新增 schemaAlertEnabled、expectedSchemaJson、schemaAlertChangeTypes、schemaAlertChannelIds
- `MonitorDTO.java` — 同步新增 4 个 schema 告警字段
- `ApiSchemaService.java` — 重写：listAll()、detectChanges()（递归双向对比）、compareSchemaJson()（版本结构化差异）、compareVersions()（获取某次变更详情）
- `ApiSchemaController.java` — /monitor/{monitorId} 改为 /list；新增 /history/{historyId}/compare 和 /compare
- `ExecutionService.java` — 新增 checkSchemaAlert()，执行时自动校验 JSON Schema 并检测变更
- `DatabaseInitConfig.java` — Migration 38 重建 api_schema 表 + monitor 表加字段

**前端改动**：
- `ApiSchemaList.vue` — 去掉监控项选择器，直接列出所有 Schema；历史弹窗重写为版本对比视图（左右对照 + 差异高亮 + 变更摘要表格）
- `MonitorList.vue` — 编辑弹窗新增"API Schema告警"区（开关、期望Schema、变更类型多选、告警通道多选）
- `api/index.js` — 新增 compareVersions、compareSchemas API
- `enums.js` — 新增 SCHEMA_CHANGE 触发类型
- `AlertTemplateList.vue` / `AlertLogList.vue` — 新增 SCHEMA_CHANGE 选项
- `Dashboard.vue` — 告警类型标签/颜色新增 SCHEMA_CHANGE
- `JsonSchemaEditor.vue` — 可视化编辑器：双模式切换；工具栏增强（快速添加模板 + JSON 实时预览面板）；空状态引导
- `SchemaFieldRow.vue` — 字段行：两行布局、必填红星、折叠展开子字段、拖拽排序手柄
- `ApiSchemaList.vue` — Schema 编辑弹窗替换为可视化编辑器
- `MonitorList.vue` — 期望 Schema 替换为可视化编辑器
- `package.json` — 新增 vuedraggable@^4.1.0 依赖

**核心流程**：监控执行 → 响应是 JSON → 用 expectedSchemaJson 校验 → detectChanges 检测变更 → 过滤匹配类型 → AlertService.sendAlertByIds() 发送告警（triggerType=SCHEMA_CHANGE）

---

### 巡检模式

**数据库迁移**：Migration 32-34

**新增表**：
- `inspection_config` — 巡检配置（name, monitor_ids, schedule_cron, enabled）
- `inspection_history` — 巡检历史（config_id, status, total_count, success_count, failed_count）
- `inspection_detail` — 巡检详情（history_id, monitor_id, status, status_code, response_time, error_message）

**新增文件**：
- `InspectionConfig.java` / `InspectionHistory.java` / `InspectionDetail.java` — 实体
- `InspectionConfigMapper.java` / `InspectionHistoryMapper.java` / `InspectionDetailMapper.java` — Mapper
- `InspectionService.java` — @Async 批量执行 + 巡检报告
- `InspectionController.java` — 配置 + 历史 + 执行
- `InspectionList.vue` — 前端页面（含详情弹窗）

---

### 依赖拓扑

**数据库迁移**：Migration 35

**新增表**：
- `monitor_dependency` — 监控依赖（source_id, target_id, dependency_type, description）

**新增文件**：
- `MonitorDependency.java` — 实体
- `MonitorDependencyMapper.java` — Mapper
- `DependencyService.java` — 拓扑图 + 影响分析
- `DependencyController.java` — CRUD + 拓扑 + 影响分析
- `DependencyTopology.vue` — Canvas 拓扑图 + 影响分析弹窗

---

### Alert Log FK 约束修复

**数据库迁移**：Migration 36

**改动**：
- 重建 `alert_log` 表移除 FK 约束（日志表不应有 FK）
- `AlertService.java` — 防御性检查：插入 alert_log 前验证 monitor 是否存在

**原因**：删除监控项后，alert_log 中的外键约束导致查询异常

---

### 前端权限修复

**改动文件**：
- `ApiSchemaList.vue` / `ReportList.vue` / `InspectionList.vue` / `DependencyTopology.vue` — 新增 `v-permission` 指令
- `StatusPage.vue` — 从 raw axios 改为集中式 `statusApi` + `formatTime`

---

## 架构重构: 告警系统解耦（模板 + 渠道分离）

**需求**：将告警消息模板从告警渠道中抽取出来，实现功能解耦和统一管理。

**新架构**：
- **告警模板（`alert_template`）**：按触发类型配置消息模板，支持预定义变量，独立冷却时间
- **告警渠道（`alert_channel`）**：纯通道配置（邮件/Webhook），不含模板逻辑
- **唯一启用规则**：同一触发类型只能有一个启用的模板
- **向后兼容**：旧 `alert_config` 表保留，旧 API 继续工作

**新增文件**：
- `AlertTemplate.java` / `AlertChannel.java` — 实体
- `AlertTemplateMapper.java` / `AlertChannelMapper.java` — Mapper
- `AlertTemplateDTO.java` / `AlertChannelDTO.java` — DTO
- `AlertTemplateController.java` / `AlertChannelController.java` — Controller
- `AlertTemplateList.vue` / `AlertChannelList.vue` — 前端页面

**AlertService 改进**：
- `sendAlertByChannel()` — 使用新模板系统发送
- `sendAlertSync(AlertChannel, ExecutionLog)` — 新系统测试发送
- `sendAlertByIds(configIds, executionLog, triggerType)` — 统一按渠道ID发送，**优先用新系统（AlertChannel + 模板），找不到回退旧 AlertConfig**，无渠道记录 NO_CHANNEL
- `resolveTemplate(triggerType)` — 按触发类型查找模板，回退到 ALL，无启用模板时返回 null
- `renderTemplate()` — 统一模板渲染，支持 `{{domain}}` 变量

**触发类型统一接入模板系统**：
- FAIL / RESPONSE_TIME / GROUP_FAIL / SSL_CERT 均通过 `sendAlertByIds` / `sendAlertByChannel` 走新模板，告警内容使用告警模板（如 `[LitVMonitor] ...`）

**SSL 告警升级**：
- `SslService.checkSslCertificate()` — 保存证书时校验 SAN 域名匹配，判定 VALID/EXPIRED/MISMATCH 状态
- `checkExecutionSslAlert()` — 执行时告警（过期/证书错误立即触发）
- `sendSslAlert()` — 优先使用新 AlertChannel 发送，找不到则回退到旧 AlertConfig，无渠道写入 NO_CHANNEL 记录

---

## 性能与安全优化（v2）

### 第一批：安全性修复（6项）
| # | 优化项 | 修改文件 |
|---|--------|---------|
| 1 | JWT Secret 追加 MAC 地址 | `application.yml`, `JwtTokenProvider.java` |
| 2 | SQLite 外键启用 | `application.yml` (HikariCP connectionInitSql) |
| 3 | 备份路径穿越修复 | `BackupController.java` (validateBackupPath) |
| 4 | X-Forwarded-For 安全处理 | `JwtAuthenticationFilter.java`, `AuthController.java` |
| 9 | 密码字段 @JsonIgnore | `SysUser.java` |
| 22 | 日志级别 INFO + 移除 SQL 日志 | `application.yml` |

### 第二批：稳定性（4项）
| # | 优化项 | 修改文件 |
|---|--------|---------|
| 7 | 全局异常处理器 | `GlobalExceptionHandler.java` (新增) |
| 18 | 补充数据库索引 | `DatabaseInitConfig.java` (Migration 27) |
| 15 | ObjectMapper 单例注入 | `VariableEngine.java`, `AlertSilenceService.java` |
| 16 | N+1 查询修复 | `MonitorService.java`, `DashboardService.java` |

### 第三批：内存优化（1项）
| # | 优化项 | 修改文件 |
|---|--------|---------|
| 11 | 内存 Map 改 Caffeine Cache | `AlertService.java`, `GroupExecutionService.java`, `ResponseTimeAlertService.java`, `MonitorScheduler.java`, `UserSessionService.java` + `pom.xml` |

### 第四批：前端优化（3项）
| # | 优化项 | 修改文件 |
|---|--------|---------|
| 17 | 路由守卫从 Store 读角色 | `router/index.js` |
| 19 | 错误处理统一 | 8个 Vue 文件 (移除冗余 error.response) |
| 24 | 登出确认弹窗 | `MainLayout.vue` |

### 依赖清理
- 移除 `spring-boot-starter-websocket`（前端未使用）
- 移除 `spring-boot-starter-quartz`（未使用 Quartz）
- 移除 `mapstruct` + `mapstruct-processor`（未使用）
- 移除重复的 `mybatis-plus-boot-starter`（Spring Boot 2 版本）
- 移除前端 `socket.io-client`（未使用）
- 新增 `caffeine` 依赖（替代 ConcurrentHashMap）

---

## 已知 TODO

- 无测试代码（`src/test` 目录为空）
- 仪表盘 ECharts 响应时间趋势图使用占位数据，需接入真实数据
- 告警系统双轨并行：旧 `alert_config` + 新 `alert_template`/`alert_channel`，旧 API（`/alert/config/*`）仍保留，所有触发点已迁移到新模板系统
