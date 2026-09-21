# 开发日志

按日期记录每次开发的改动、文件变更、遇到的问题和解决方案。

---

## 2026-09-21（v1.3.2 功能增强与角色权限完善）

### 监控项导入导出
- `MonitorImportExportController.java`：新增 `GET /monitor/export`（Apache POI 直接构建 XSSFWorkbook）和 `POST /monitor/import`（支持告警通道替换策略）
- `pom.xml`：新增 `poi-ooxml 5.2.5` 依赖
- `api/index.js`：新增 `monitorApi.export()` 和 `monitorApi.import(formData)`
- `MonitorList.vue`：新增导入/导出按钮 + 导入弹窗（渠道替换选项）

### 域名证书星标关注
- `DomainAsset.java`：新增 `starred` 字段
- `DatabaseInitConfig.java`：Migration 57 添加 `starred INTEGER DEFAULT 0` 列
- `DomainAssetController.java`：新增 `PUT /{id}/star` 切换接口，列表支持 `starred=true` 过滤
- `DomainList.vue`：星标列（v-if 控制 VIEWER 不可见）+ "只看关注"复选框

### 告警静默优化
- `AlertSilenceService.isInTimeWindow()`：重写判断逻辑——无日期+有时间段仅在时段内静默；无日期+无时间段永久静默；weekly/monthly 从 config 顶层读取 days/dates（修复之前从 window 对象查找导致过滤失效）
- `DatabaseInitConfig.java`：Migration 58 重建 alert_silence 表，start_time/end_time 改为可空
- `AlertSilenceList.vue`：静默时段标签增加 ? 图标悬浮说明（inline-flex 垂直居中对齐）

### 监控任务告警恢复
- `GroupExecutionService.java`：任务成功时每次都调用 `sendAlertByIds`，让 AlertService 自行判断恢复条件（之前只在第一次成功时调用一次，无法满足连续成功次数要求）
- `AlertService.sendAlertByIds()`：静默分支增加 `incrementCount(fingerprint, false)`，确保失败计数被记录（之前静默直接 return 导致 current_count=0，恢复检测认为从未失败）

### 角色权限
- `ReminderController.java`：写操作增加 `@PreAuthorize`（create/update: ADMIN+OPERATOR, delete: ADMIN only）
- `UserController.java`：编辑/删除 admin 用户时非 admin 本人返回错误
- `SecuritySettingsController.java`：踢出会话不允许踢出 admin 用户
- `ProxyConfigController.java`：全部接口改为 `hasAnyRole('ADMIN', 'OPERATOR')`
- `BackupController.java`：列表/创建/恢复/上传恢复/数据清理放开给 OPERATOR；下载/删除保持 ADMIN only
- `permissions.js`：reminder 菜单改为 ADMIN+OPERATOR；proxy/backup 菜单和按钮改为 ADMIN+OPERATOR；backup:download/backup:delete 保持 ADMIN only

### VIEWER 角色修复
- `AlertLogList.vue`：VIEWER 不调用 `loadAlertConfigs()`，隐藏告警渠道筛选下拉
- `DomainList.vue`：VIEWER 隐藏关注列和"只看关注"复选框
- `Dashboard.vue`：VIEWER 隐藏周期提醒按钮
- `GroupList.vue`：VIEWER 不调用 `loadAllAlertConfigs()`
- `permissions.js`：reminder 菜单改为 `['ADMIN', 'OPERATOR']`

### 监控任务编辑修复
- `GroupList.vue`：showDialog 创建时 `delete form.id`；submitForm 成功后重置 editingId 并清除 form.id

### 非HTTP监控项
- `MonitorOtherDialog.vue`：告警通道下拉选项增加启用/禁用状态标签

### 导入Excel修复
- `MonitorList.vue`：导入弹窗打开时先调用 `loadAlertConfigs()` 加载渠道列表

### 版本/文档
- 版本号升级至 1.3.2
- 更新 changelog.md、README.md、operation.md

---

### v1.3.1

**周期提醒重复通知修复**
- `ReminderScheduler.java`：过期任务（now >= nextDue）时跳过提前提醒，只发到期提醒，避免同一到期点发 2 条通知

**恢复通知不发送修复**
- `ExecutionService.java`：连续失败触发告警后不再重置计数器为 0，保留计数以便恢复检测
- `AlertRateLimitService.java`：恢复通知查询（`findTemplateWithRateLimit`）移除 `rateLimitEnabled=true` 硬性要求；限频检查保留独立的 `findTemplateWithRateLimitFlag` 方法

**告警模板表单优化**
- `AlertTemplateList.vue`：恢复通知开关始终展示（移除 `v-if="rateLimitEnabled"`），连续正常次数仅依赖恢复通知开关

**数据清理功能**
- `BackupController.java`：新增 `POST /backup/cleanup`，接收 executionDays/alertDays/inspectionDays，按天数分别清理，仅 ADMIN 权限
- `BackupList.vue`：新增「数据清理」按钮 + 弹窗，独立配置每项清理天数（默认 30 天），二次确认后执行

**文档/版本**
- 版本号升级至 1.3.1
- 更新 Manual.vue（告警限频、数据备份章节）
- 更新 changelog.md、README.md

---

## 2026-09-19（安全修复与体验优化）

### v1.3.0

**登录错误提示修复**
- `AuthController.java`：捕获 `BadCredentialsException` 返回 `Result.error(401, "用户名或密码错误")`，捕获 `DisabledException` 返回 `Result.error(401, "账号已被禁用")`，不再 `throw e` 交由 JwtAuthenticationEntryPoint 返回通用"未认证"
- `api/index.js`：401 拦截器区分 `/auth/login` 请求与其他请求，登录接口透传后端 message

**用户管理 500 修复**
- `UserService.java`：`validatePasswordPolicy`、`validateRole`、`updateUser`（管理员保护）、`deleteUser`（管理员保护）、`updateProfile`（旧密码验证）中 `RuntimeException` → `IllegalArgumentException`
- `UserDTO.java`：添加 `@JsonIgnoreProperties(ignoreUnknown = true)` 防止多余字段导致反序列化异常
- `UserList.vue`：提交前删除 `id`、`mustChangePassword`、`createdAt`、`updatedAt`

**登录页深色模式**
- `Login.vue`：`.login-container` 添加 `color-scheme: light` + `:deep()` 覆盖输入框样式，不受深色主题影响

**备份下载进度**
- `BackupList.vue`：`ReadableStream` 流式读取 + `showSaveFilePicker`（Chrome）/ blob 降级（Firefox），支持选择保存路径 + 实时进度

**版本号独立管理**
- `vite.config.js`：`define: { __APP_VERSION__ }` 从 `package.json` 注入
- `VersionController.java`：移除 `frontend-version`，只返回 `backend` 版本
- `MainLayout.vue`：显示 `前端本地版本 / 后端版本`
- `application.yml`：移除 `frontend-version` 配置

**异常处理规范化**
- 全面排查 16 处 `RuntimeException`，8 处用户校验类改为 `IllegalArgumentException`，8 处内部执行错误保留

**文档/版本**
- 版本号升级至 1.3.0（pom.xml, application.yml, package.json, README.md, start scripts）
- 更新 Manual.vue、changelog.md、devlog.md

---

## 2026-09-18（功能增强与交互优化）

### v1.2.2: 功能增强与交互优化

**4.1 告警模板类型**
- `AlertTemplate.java` 新增 `templateType` 字段（SYSTEM/CUSTOM）
- `DatabaseInitConfig.java` Migration 56：添加 `template_type` 列，标记初始6个模板为 SYSTEM
- `DefaultAlertTemplateInit.java`：新插入模板使用 `template_type='SYSTEM'`
- `AlertTemplateController.java`：创建时自动设为 CUSTOM，删除时校验 SYSTEM 不可删
- `AlertTemplateList.vue`：新增模板类型列（系统预置/自定义），SYSTEM 类型隐藏删除按钮

**4.2 周期提醒分类**
- `ReminderCategory.java` 新增 `TASK_REMINDER("事务提醒", 1)`
- `ReminderList.vue`：筛选和表单下拉选项、标签类型映射同步更新

**4.3 监控ID/任务ID跳转**
- `MonitorList.vue`、`GroupList.vue`：ID 列改为 `<router-link>` 跳转到 `/log?monitorId={id}` / `/log?groupId={id}`
- `LogList.vue`：`applyQuery()` 支持 `monitorId`、`groupId` 查询参数；「监控ID」、「任务ID」列可点击跳转到对应管理页面
- `AlertLogList.vue`：「监控名称」列有 monitorId 时跳转到监控项管理，「任务名称」列有 groupId 时跳转到监控任务
- `MonitorService.java`、`MonitorGroupService.java`：`listMonitors`/`listGroups` 增加 `Long id` 参数，精确匹配
- `MonitorController.java`、`MonitorGroupController.java`：`@RequestParam(required = false) Long id`

**4.4 仪表盘自动刷新**
- `Dashboard.vue`：标题旁新增 Refresh 图标 + el-popover（关闭/10s/30s/180s），setInterval 自动刷新，localStorage 持久化，onUnmounted 清理定时器
- `main.js`：全局注册 Refresh 图标

**4.5 HTTP表单提示优化**
- `MonitorHttpDialog.vue`：URL、请求头使用 `#label` slot + QuestionFilled tooltip；JSONPath、JSONPath期望值、期望文本、正则表达式同样增加 label tooltip
- `.label-help-icon` CSS 修复对齐；新增 `.var-hint-inline` 样式

**4.6 文档更新**
- `Manual.vue`：告警模板类型、事务提醒、ID跳转、仪表盘自动刷新、HTTP表单tooltip
- `README.md`：版本号 v1.2.2
- `overview.md`：项目简介、告警模板类型、周期提醒分类
- `changelog.md`：v1.2.2 变更记录

---

## 2026-09-17（安全加固）

### 3. v1.2.1: 安全加固与缺陷修复

**3.1 认证与密钥**
- `JwtTokenProvider`：移除 `base-secret`/MAC 派生，改为 `JWT_SECRET`（≥32字符）或自动生成随机密钥持久化到 `.jwt-secret`
- `JwtAuthenticationFilter`：权限一律取自数据库 `UserDetails`；新增 428 强制改密拦截（放行 `/auth/*`、`/status/*`、`/version`、`/user/profile`）
- `UserService`：`initAdminUser` 使用 `ADMIN_INIT_PASSWORD` 或随机强密码，标记 `mustChangePassword`；对存量 `admin123` 自动标记；`createUser/updateUser` 增加密码策略与角色白名单校验；`updateProfile` 改密后清除标记
- `SysUser` 新增 `mustChangePassword`；Migration 55 增加 `must_change_password`
- `AuthController` 登录/`/me` 返回 `mustChangePassword`；`UserController.updateProfile` 改密后失效该用户全部会话
- 前端 `Login.vue` 新增强制改密弹窗；`stores/user.js` 水合 `userInfo`；`api/index.js` 处理 428

**3.2 SSRF**
- 新增 `service/SsrfProtectionService`（连接期 IP 校验，含云元数据/CGNAT/IPv6）
- `AbstractMonitorExecutor.openSocket` 解析+校验后连接（防 DNS rebinding）；`PingMonitorExecutor` 显式校验
- `HttpMonitorExecutor` 注入 `ssrfGuard`，OkHttp 使用校验型 `Dns` + 目标主机显式校验
- `AlertService`/`ReminderNotifyService` 出站 webhook 增加校验型 `Dns` 与主机显式校验

**3.3 授权**
- `ReminderService` 全部单条操作增加 `username` 归属校验；`ReminderController` 传入当前用户
- `AlertController`/`AlertTemplateController` 列表加 `@PreAuthorize`；`BackupController` `/list` 加 `@PreAuthorize`
- `MonitorController` 对非 ADMIN/OPERATOR 屏蔽敏感字段

**3.4 数据库/正确性**
- `DatabaseInitConfig`：新增 `schema_version` 表与 `isMigrated/markMigrated/hasColumn`；迁移 1/36/38/40 加版本守卫；重建 `alert_log` 索引
- `MonitorService.listMonitors`：关键词 OR 分组修复
- `InspectionService.runInspection`：提前返回路径补 `finishInspection()`
- `AlertRateLimitMapper` 新增原子更新方法，`AlertRateLimitService` 改用原子计数
- `application.yml`：PRAGMA 移入 JDBC URL，连接池 20→5

**3.5 前端**
- `directives/permission.js` 重写（卸载停止 watcher + updated 钩子）
- 新增 `views/NotFound.vue` + 路由兜底
- 修复 `AlertSilenceList` endTime 空指针、`GroupList` 表单缺字段
- `JsonSchemaEditor` 新增 `flush()` 并暴露，提交前调用；卸载清定时器
- 修复 `changelog.md` 记录

**3.6 优化（Batch 4）**
- `ExecutionService` 删除死代码：`getClient`/`resolveActiveProxyConfig`/`resolveIpAddress`/`isInternalHost`/`toJson`/`extractQueryString`/`appendQueryParams`/`validateResponse`/`recordDomainAsset` 及对应字段（约 350 行）
- `AlertService`/`ReminderNotifyService` 的 `buildSafeClient()` 改为 volatile 缓存复用
- `Dashboard.vue`：`import * as echarts from 'echarts'` → `echarts/core` 按需注册，Dashboard 分包 1035KB→542KB
- `MonitorList.vue`：搜索/重置重置分页到第 1 页
- `SecuritySettingsService`：`lockout_enabled` 默认值 false→true

**验证**：后端编译/打包通过；`ExecutionService` 重构后实际执行监控项（bd/baidu）返回 SUCCESS/200；前端构建通过。

**3.7 备份路径与登出修复**
- `BackupController.extractDbPath()`：先剥离 JDBC URL 的 `?` 查询参数再 `Paths.get`，修复 `/backup/list` 500（`Illegal char <?>`）
- `SecurityConfig`：`/auth/logout` 加入 `permitAll`
- `JwtAuthenticationFilter`：新增 `isAuthEndpoint()`，`/auth/*` 跳过会话失效校验，保证登出始终成功
- `MainLayout.vue`：确认弹窗与登出异常分离，登出后无条件清理并跳转登录页
- `stores/user.js`：`logout()` 永不 reject；`getJtiFromToken()` 修正 base64url 解码
- 验证：`/backup/list` 返回 200；登出在无 token / 伪造 token / 正常 token 下均返回 200

**3.8 未认证返回 401 + 全局登录失效提示**
- `SecurityConfig`：注册 `JwtAuthenticationEntryPoint`（此前未注册，导致未认证被 Spring 默认返回 403），并新增 JSON `AccessDeniedHandler` 返回 403
- 效果：未认证/伪造 token 请求返回 **401**，已登录但无权限返回 **403**
- `api/index.js`：401 → 全局 `ElMessageBox` 提示「登录状态已失效，是否重新登录？」（带并发去重），确认后清理并跳转；403 → `ElMessage` 提示无权限
- 验证：未认证请求 401、伪造 token 401、公开 `/version` 200

**3.9 备份下载路径穿透加固**
- `BackupController.validateBackupPath()`：在词法校验（normalize + startsWith）基础上，新增「仅允许 `.db` 后缀（大小写不敏感）」、「拒绝符号链接」、「toRealPath 真实路径二次校验」，防止软链指向 `.jwt-secret` 等备份目录外文件
- `downloadBackup`：`response.sendError(...)` 改为直接写 JSON（新增 `writeError`），修复错误转发到 `/error` 被鉴权拦截、状态被改写为 401 的问题
- `SecurityConfig`：放行 `/error`
- 验证：`backups\..\..\.jwt-secret` / `.jwt-secret` / `litv-monitor.db` / 指向 `.jwt-secret` 的软链 / `evil.txt` / `evil.TXT` → 均 403；合法备份 → 200（528384 字节）

**3.10 周期提醒到期逻辑修复**
- 现象：提醒到期未手动完成，却先显示「已过期」随后自动变「已完成」；周期任务未处理会静默推进到下一次
- 根因：`ReminderScheduler.processTask()` 到期后自动 `completed=true`（once）或自动推进 `nextDueAt`（周期）
- 修复：调度器只发送通知，不改状态；完成/推进仅由用户手动「完成」触发
- `ReminderService.calculateNextOccurrence()`：以当前 `nextDueAt` 为基准推进，改用"严格晚于 now"判定，修复提前完成落回同一次的问题
- 通知按到期点去重（key 含 `nextDueAt`，TTL 24h）
- 验证：插入已过期的一次/每日测试提醒，调度器发送通知后 `completed` 仍为 0、`next_due_at` 不变

**3.11 SSRF 防护模式移入安全设置**
- `SecuritySettingsService`：新增 `ssrf_mode` 设置项（strict/allow_internal/off），`monitor.ssrf-protection` 作为首次初始化默认值
- `SsrfProtectionService`：改为从数据库读取模式（Caffeine 5s 缓存），`isBlocked()` 按模式分级——回环/链路本地/云元数据/CGNAT 在所有模式禁止，私有段仅 strict 禁止
- `SecuritySettings.vue`：新增「监控请求安全（SSRF 防护）」卡片（下拉选择 + 说明 tooltip）
- 拦截提示语补充引导到安全设置
- 验证：`192.168.10.7` strict→拦截、allow_internal/off→放行；`127.0.0.1` strict/allow_internal→拦截、off→放行

**3.12 密钥脱敏 + 其余加固与优化（按审计优先级收尾）**
- `ProxyConfig`：`password` 改 `WRITE_ONLY`，新增 `isHasPassword()`；`ProxyConfigService.update` 空密码不覆盖；`ProxyList.vue` 不回填密码
- 新增 `util/SecretMasker`（mask/restore/maskUrl）；`AlertChannelController`、`AlertController` 列表脱敏、更新回填
- `GlobalVariableService.maskSecretValue` 改为完全脱敏 `******`
- `BackupController.downloadBackup` 支持 `X-Download-Token` 头；`BackupList.vue` 改 fetch+blob
- `UserSessionService` 新增 IP 维度失败计数；`AuthController.login` 429 限速
- `SecurityConfig` 新增 HSTS / Referrer-Policy / CSP 头
- `AuditLogService.maskSensitiveFields` 完全脱敏
- `main.js` 图标按需注册；`vite.config.js` manualChunks；`theme.js` Leaf→Cherry
- `PreRequestScriptEngine` 共享 Engine；`ExecutionService` retryCount；`AlertService` 移除死分支
- 列表页分页重置 + route.query 监听；新增 ESLint/Prettier/EditorConfig
- 注意：**不要**再用 PowerShell `Get-Content/Set-Content` 就地改写 UTF-8 源文件（会把中文转成 GBK 并损坏），已踩坑并修复 `ReminderList.vue`

**3.13 N+1 / 列表上限 / 时区 / 组件拆分**
- `GroupMonitorMapper` 新增 `countByMonitor()` / `countByGroup()`（GROUP BY）；`MonitorService.getGroupCounts`、`MonitorGroupService.listGroups` 改用
- `MyBatisPlusConfig` 分页拦截器 `setMaxLimit(1000)`；多处「返回全部」接口加 `LIMIT 1000`
- `LitVMonitorApplication.main` 强制 JVM 时区 `Asia/Shanghai`；`DashboardService`/`StatusPageService` 时间边界改 Java 参数；`schema.sql`/`DatabaseInitConfig` 的 `CURRENT_TIMESTAMP` → `datetime('now','localtime')`（用 `[IO.File]` 显式 UTF-8 读写，无损坏）
- 抽出 `components/MonitorOtherDialog.vue`（props: modelValue/editingRow/alertConfigs；emits: update:modelValue/saved）
- 抽出 `components/MonitorHttpDialog.vue`（HTTP 表单弹窗，含两个 Tab；props: modelValue/editingRow；emits: update:modelValue/saved），`MonitorList.vue` 2108 → 571 行
- 顺带修复：`MonitorOtherDialog` 的告警渠道 prop 之前误传 `alertConfigs`（应为 `allAlertConfigs`）
- 教训：批量按行区间删除时必须**从后往前**删，否则索引错位会把文件删坏（本次 `MonitorList.vue` 被删坏后已按已知内容完整重建，构建通过）
- 验证：监控列表 groupCount、任务列表 monitorCount、仪表盘概览均正常；前端构建通过

**验证**：后端编译/打包通过；强制改密流程（登录→428→改密→重登）实测通过；SSRF 对 `127.0.0.1`（TCP/HTTP）与 `169.254.169.254` 均被拦截；迁移版本 1/36/38/40 已记录；`alert_log` 5 个索引存在；HTTPS 监控在默认配置下正常。

---

## 2026-09-17

### 2. v1.2.0: 多协议监控（15种协议）

**2.1 策略模式架构**
- 新建 `MonitorType` 枚举（`enums/MonitorType.java`）：HTTP、PING、TCP、SSH、TELNET、FTP、VNC、MYSQL、POSTGRESQL、REDIS、MEMCACHED、MONGODB、ZOOKEEPER、AMQP、MQTT
- 新建 `MonitorExecutor` 接口（`executor/MonitorExecutor.java`）：`getMonitorType()` + `execute(monitor, log, vars)`
- 新建 `MonitorExecutorRegistry`（`executor/MonitorExecutorRegistry.java`）：Spring 自动发现所有 MonitorExecutor 实例，按 type 注册
- 新建 `AbstractMonitorExecutor` 抽象基类：统一 `getConfigValue`/`getIntConfigValue`/`resolveHost`/`resolvePort`/`timeoutMillis`/`openSocket`/`readBytes`/`markFail`/`markError`，消除重复代码
- 现有 5 个执行器（Ping/TCP/SSH/MySQL/Redis）全部迁移到基类

**2.2 HTTP 执行器（提取自 ExecutionService）**
- 新建 `HttpMonitorExecutor`（`executor/HttpMonitorExecutor.java`）：完整提取 HTTP 逻辑
  - OkHttp 客户端管理（带连接池 + 代理 + SSL 跳过验证）
  - SSRF 内网防护、域名/IP 解析
  - 前置脚本执行（API 签名、自定义逻辑）
  - 变量替换与引用追踪、请求体/响应体处理
  - 响应验证（状态码、文本、正则、JSONPath）
  - 变量提取（支持 body/headers/cookies 多源）
  - 域名资产自动录制

**2.3 非 HTTP 执行器（14种）**
- `PingMonitorExecutor`：`InetAddress.isReachable(5000)`，记录 host/IP
- `TcpMonitorExecutor`：`Socket.connect(host, port, timeout)`，记录连接耗时
- `SshMonitorExecutor`：连接端口 22，读取 SSH Banner（`SSH-2.0-xxx`）
- `MysqlMonitorExecutor`：读取 MySQL Initial Handshake Packet（协议版本 0x0a + Server Version String）
- `RedisMonitorExecutor`：RESP 协议发送 `*1\r\n$4\r\nPING\r\n`，验证 `+PONG`
- `TelnetMonitorExecutor`：读取 Banner；若超时无 Banner 则发送 `\r\n` 再读（应对等待输入的 Telnet 服务）
- `FtpMonitorExecutor`：服务端先发 Banner，校验 `220` 前缀
- `VncMonitorExecutor`：服务端先发 12 字节版本串，校验 `RFB` 前缀
- `PostgresMonitorExecutor`：发送 SSLRequest（`00 00 00 08 04 D2 16 2F`），响应 `S`/`N` 均表示服务存活
- `MemcachedMonitorExecutor`：发送 `version\r\n`，校验 `VERSION` 前缀
- `ZookeeperMonitorExecutor`：发送 `ruok`，校验 `imok`；未响应时降级为 TCP 存活（4lw 白名单可能未启用）
- `AmqpMonitorExecutor`：发送 AMQP 0-9-1 协议头，接受协议头回显（`AMQP`）或 connection.start 帧（首字节 `0x01`）
- `MqttMonitorExecutor`：手工构造 MQTT 3.1.1 CONNECT（clean session、无认证、clientId=litv-mon），校验 CONNACK（首字节 `0x20`），解析 return code
- `MongoMonitorExecutor`：手工构造 OP_MSG `{hello:1,$db:"admin"}`（BSON 手写编码），校验 opCode=2013，提取 `maxWireVersion`

**2.4 数据库迁移（Migration 54）**
- `ALTER TABLE monitor ADD COLUMN monitor_type VARCHAR(20) DEFAULT 'HTTP'`
- `ALTER TABLE monitor ADD COLUMN config TEXT`
- `CREATE INDEX idx_monitor_type ON monitor(monitor_type)`
- 所有现有监控项自动兼容（默认 HTTP）

**2.5 后端重构**
- `Monitor.java`：新增 `monitorType`（String）+ `config`（String）字段
- `MonitorDTO.java`：新增 `monitorType` + `config` 字段
- `ExecutionService.java`：`executeMonitor()` 方法重构为策略分发，根据 `monitorType` 查找对应 Executor
- 执行后告警逻辑（ResponseTimeAlert、MonitorFailureAlert、SchemaAlert）保持不变，协议无关

**2.6 前端改动**
- `MonitorList.vue`：
  - 页面按钮拆分为「添加HTTP监控」+「添加其他监控」
  - 新增非 HTTP 监控弹窗：类型分组下拉（网络探测/远程登录/数据库/消息队列，共14种）、主机地址、端口（默认值提示，Ping 隐藏端口）、超时、启用、状态页展示、告警阈值、告警通道
  - 表格新增「类型」列（彩色 Tag），方法列对非 HTTP 类型显示 `-`
  - 编辑按钮根据 `monitorType` 自动路由到 HTTP 或非 HTTP 弹窗
  - HTTP 监控提交时显式设置 `monitorType: 'HTTP'`

**2.7 验证与测试**
- 后端编译 + 前端构建通过
- 启动日志确认 15 个执行器全部注册
- MQTT 对真实公网 broker（broker.hivemq.com:1883）测试通过（CONNACK returnCode=0）
- 其余 8 个新协议使用 Python mock 服务器（本地）验证，全部 SUCCESS：
  - FTP(220)、VNC(RFB)、TELNET、PostgreSQL(SSL S)、AMQP、Memcached(VERSION)、ZooKeeper(imok)、MongoDB(maxWireVersion=17)
- 负向测试通过：VNC 连 FTP 端口 → FAIL、MySQL 连 FTP 端口 → FAIL、FTP 连关闭端口 → FAIL(Connection refused)

**2.8 仪表盘漏洞情报导航**
- `Dashboard.vue`：顶部 header-actions 在「周期提醒」左侧新增「漏洞情报」按钮（danger，Warning 图标）
- 新增 `vulnVisible` 弹窗（920px），分上下两块：
  - 上方 `vulnTopSites`（6 个高价值站）以 `el-row/el-col` 卡片网格展示，含名称、国内/国际标签、描述、URL
  - 下方 `vulnRefSites`（15 个参考站）以列表展示，含名称、描述、外链箭头
- 站点数据硬编码在前端（不落库），点击新标签页打开（`target="_blank" rel="noopener noreferrer"`）
- 弹窗内容样式置于 scoped 样式块顶层（因 el-dialog 内容 teleport 到 body，避免被 `.dashboard` 祖先选择器限制）
- 高价值站包含阿里云漏洞库(AVD)、CNVD、CNNVD、CVE、NVD、奇安信威胁情报中心

**2.9 修复请求体 Content-Type 与 bodyType 不一致**
- 现象：监控项 bodyType=json，但实际请求头为 `Content-Type: application/x-www-form-urlencoded`
- 根因：`headers` 字段中残留旧的 `Content-Type: application/x-www-form-urlencoded`（此前 bodyType 为 form 时写入），后端显式请求头优先于默认值，导致 json 类型仍发送 form-urlencoded
- 后端 `HttpMonitorExecutor`：新增 `defaultContentType(bodyType)`，默认值由固定 `application/json` 改为按 bodyType 推导（json/xml/text/form）
- 前端 `MonitorList.vue`：新增 `syncContentTypeHeader(bodyType)`，在 `onBodyTypeChange` 与 `submitForm` 时同步请求头 Content-Type
  - 仅替换已知的自动值（json/form/xml/text），自定义 Content-Type 保留
  - bodyType 为 none 时移除自动 Content-Type
- 验证（本地 echo server）：json→application/json、xml→application/xml、text→text/plain、自定义→保留；显式请求头仍优先
- 已有监控项需在编辑弹窗中重新保存一次以自动纠正

**2.10 签名配置新增「签名转大写」**
- `MonitorList.vue`：`signConfigForm` 新增 `uppercaseSign: false`
- MD5 / HMAC-SHA256 / RSA-SHA256 三个签名区块均新增「签名转大写」开关（位于时间戳相关项下方）
- `generateScriptFromConfig()`：新增 `const upper = signConfigForm.uppercaseSign ? '.toUpperCase()' : ''`，7 处 `const sign = crypto.xxx(...)` 赋值统一追加 `${upper}`
- 兼容旧配置：加载 signConfig 时先填默认 `uppercaseSign: false`，旧数据缺失该字段时不会残留上一次的值
- `Manual.vue` 补充说明

**2.11 修复预请求脚本引擎无法执行（签名大小写不生效的根因）**
- 现象：签名脚本中的 `.toLowerCase()` 不生效，实际签名始终为大写
- 排查：`stdout.log` 报 `Failed to execute pre-request script: Could not find option with name js_bigint`，进一步为 `js.commonjs-require`（实验性）、`js.small-ic`（不存在）
- 根因链：
  1. `PreRequestScriptEngine` 的 `Context.newBuilder("js")` 使用了无效选项 `js_bigint` / `js.small-ic`，Context 构建即抛异常，脚本从未执行
  2. 因脚本未执行，仅内置 `Md5SignFunction` 生效（其返回大写），故 `.toLowerCase()` 无效
  3. `crypto.md5` 引用了不存在的 `Polyglot.import('java.security.MessageDigest')`，即使脚本执行也会失败
  4. `HostAccess.UNTRUSTED` 下 `java.*` 包全局不可用，脚本内 `java.security.*` 报 `ReferenceError: java is not defined`
- 修复：
  - 移除无效/实验性选项，保留 `js.ecmascript-version=2023`、`js.strict=true`（并允许实验性选项）
  - 新增 `ScriptRuntime` 宿主类，实现全部 crypto/util 方法（md5/sha1/sha256/hmac*/base64/rsaSha256*、timestamp/datetime/nonce/uuid/random 等）
  - 通过 `ProxyExecutable`（绑定为 `__h`）暴露给脚本，`SCRIPT_PRELUDE` 中 `crypto`/`util` 委托到 `__h('fn', ...)`；不授予任意 Java 访问，避免越权
  - 当存在自定义脚本时不再执行内置签名（脚本为准），避免双重签名
  - 新增 `valueToString` 兼容数字回写
- 验证（本地 echo server）：md5 小写 / 大写、sha256('abc')、hmacSha256Base64、datetime、nonce、uuid 全部正确；内置签名回退路径仍正常
- 运维提醒：后端需在 `litv-monitor-server` 目录下启动（`jdbc:sqlite:./litv-monitor.db` 为相对路径），否则会使用错误的工作目录生成空库

---

### 1. v1.1.0: 仪表盘备忘录 + 版本展示 + 周期任务提醒 + Bug修复

**0.1 周期任务提醒**
- 新建 `reminder_task` 表（Migration 53）：username, title, description, category, due_date, recurrence_type, recurrence_config, advance_enabled, advance_minutes, advance_days, alert_channel_ids, enabled, completed, next_due_at
- 新建实体/枚举/DTO：ReminderTask.java, ReminderCategory.java, ReminderTaskDTO.java, ReminderSnoozeRequest.java
- 新建 Mapper：ReminderTaskMapper.java
- 新建服务：ReminderService.java（CRUD + complete/snooze + 周期计算）、ReminderNotifyService.java（通过 AlertChannel 发送通知）
- 新建调度器：ReminderScheduler.java（每分钟检查，Caffeine 防重复）
- 新建控制器：ReminderController.java（11 个端点）
- TriggerType 新增 REMINDER 枚举值
- 前端 ReminderList.vue：列表+筛选+创建编辑弹窗+延迟弹窗
- Dashboard.vue 顶部新增「周期提醒」按钮（AlarmClock 图标 + 过期+今天+近7天到期徽章）
- permissions.js 新增 reminder 菜单 + 5 个按钮权限
- router/index.js 新增 /reminder 路由
- api/index.js 新增 reminderApi（11 个方法）

**0.2 后端编译修复**
- ReminderService.java: `YearMonth.atDay()` 返回 `LocalDate`，需 `.atTime(base.toLocalTime())` 转为 `LocalDateTime`
- ReminderNotifyService.java: 缺少 `ReminderCategory` import

**1.1 仪表盘备忘录**
- 新建 `user_memo` 表（Migration 52）：username UNIQUE, content TEXT
- 新建 `UserMemo.java`/`UserMemoMapper.java`/`UserMemoService.java`/`UserMemoController.java`
- `Dashboard.vue` 顶部新增「备忘录」按钮 + 弹窗文本编辑器（Ctrl+S 保存）
- `api/index.js` 新增 `memoApi`

**1.2 侧边栏版本展示**
- 新建 `VersionController.java`：`GET /version` 公开接口，返回 `{ backend, frontend }`
- `application.yml` 新增 `app.version: 1.1.0`
- `MainLayout.vue` 侧边栏 Logo 下方显示版本号
- `SecurityConfig` 新增 `/version` permitAll
- `api/index.js` 新增 `versionApi`

**1.3 变量敏感值编辑保存 bug 修复**
- **问题**：`isSecret=true` 变量编辑时显示脱敏值 `****`，保存后覆盖原值为空
- **修复**：`GlobalVariableService.updateVariable()` 检测 masked 值时跳过更新；`VariableList.vue` 编辑时清空 value，提示"留空则不修改原值"

**1.4 状态公示页 logo 一致性**
- `StatusPage.vue` 内联 SVG 改为 `<img src="/vite.svg">`，与侧边栏一致

**1.5 代理 enabled/active 脏数据修复（Linux）**
- **问题**：Linux DB 中 `enabled=0, active=1`，`refreshActiveProxy()` 要求 `enabled=1`，活跃代理被忽略
- **修复**：移除 `enabled` 门控；`setActive()` 同步设置 `enabled=true`；Migration 51 修复脏数据；self-healing + SSL 独立 try/catch；移除死代码

**1.6 版本号升级**
- pom.xml/package.json: `1.0.0` → `1.1.0`

**修改文件：** UserMemo.java(新建), UserMemoMapper.java(新建), UserMemoService.java(新建), UserMemoController.java(新建), VersionController.java(新建), ReminderTask.java(新建), ReminderTaskMapper.java(新建), ReminderCategory.java(新建), ReminderTaskDTO.java(新建), ReminderSnoozeRequest.java(新建), ReminderService.java(新建), ReminderNotifyService.java(新建), ReminderController.java(新建), ReminderScheduler.java(新建), ReminderList.vue(新建), DatabaseInitConfig.java(Migration 51-53), TriggerType.java, ProxyConfigService.java, ExecutionService.java, GlobalVariableService.java, SecurityConfig.java, application.yml, pom.xml, package.json, Dashboard.vue, MainLayout.vue, StatusPage.vue, VariableList.vue, api/index.js, permissions.js, router/index.js

---

## 2026-09-16

### 1. 监控级失败告警 + 任务失败判定策略 + 告警模版兜底通道 + IP来源配置 + 备份流式下载 + UI优化

**1.1 监控级失败告警**
- Monitor 新增字段：alertEnabled, alertConsecutiveCount, alertConfigIds
- ExecutionService 使用 Caffeine 缓存跟踪连续失败次数，达到阈值触发 alertService.sendAlertByIds()
- MonitorList.vue 新增告警配置区域

**1.2 任务失败判定策略**
- MonitorGroup 新增字段：failCriteriaType(ANY/COUNT/PERCENT), failCountThreshold, failPercentThreshold
- GroupExecutionService 按配置判定任务是否失败
- GroupList.vue 新增失败判定表单

**1.3 告警模版兜底通道**
- AlertTemplate 新增 fallbackChannelIds 字段
- 当触发方未配置通道时使用兜底通道
- AlertTemplateList.vue 新增兜底通道多选

**1.4 IP来源配置（数据库）**
- security_setting 表新增 ip_source_header/ip_strict_mode/xff_trusted_proxies
- 新建 IpUtils.java（缓存配置、解析XFF、校验IP、可信代理判断）
- AuditLogService + JwtAuthenticationFilter 使用 IpUtils.getClientIp()
- ProxyConfigInit 启动时初始化缓存
- SecuritySettings.vue 新增IP来源配置表单

**1.5 备份流式下载**
- 新建 POST /backup/download-token 接口（5分钟令牌）
- GET /backup/download 支持 token 查询参数
- BackupList.vue 改用令牌下载

**1.6 其他UI优化**
- AlertChannelList.vue 测试按钮 loading 状态
- MainLayout.vue Sidebar 滚动修复（flex + overflow-y:auto）
- LogList.vue 详情布局优化（URL整行 + IP字段）
- SchemaFieldRow.vue _uid修复焦点丢失 + isArrayItem隐藏数组项name
- Element Plus 废弃修复：checkbox label→value, rows="2"→:rows="2", editingMonitor→editingId
- ApiSchemaList.vue el-switch enabled布尔值修复

**修改文件：** Monitor.java, MonitorDTO.java, MonitorGroup.java, MonitorGroupDTO.java, AlertTemplate.java, AlertTemplateDTO.java, AlertTemplateController.java, ExecutionService.java, GroupExecutionService.java, IpUtils.java(新建), AuditLogService.java, JwtAuthenticationFilter.java, ProxyConfigInit.java, BackupController.java, DatabaseInitConfig.java(V47-V50), MonitorList.vue, GroupList.vue, AlertTemplateList.vue, AlertChannelList.vue, BackupList.vue, LogList.vue, SecuritySettings.vue, MainLayout.vue, SchemaFieldRow.vue, ApiSchemaList.vue, InspectionList.vue, DomainList.vue

---

## 2026-09-15

### 1. FormData格式错误 + POST请求body为空500 + 监控任务并行执行优化

**问题1：FormData请求体格式错误**
- 前端 formParams 转为 JSON 字符串存储，Content-Type 设为 urlencoded
- 后端原样发送 JSON，服务端收到的不是 key=value 格式
- 修复：ExecutionService 检测 bodyType=form + urlencoded 时，自动 JSON → URL-encoded 转换

**问题2：POST/PUT请求body为空导致500**
- 原因：`method("POST", null)` + 后续条件判断 `body.isEmpty()` 导致OkHttp校验失败
- 修复：POST/PUT/PATCH 始终设置body，空body使用空字符串；增加PATCH支持

**问题2：监控任务串行执行效率低**
- 优化：sortOrder=0 并发执行（CompletableFuture），sortOrder>0 按序串行
- 并行组内失败不互相影响，串行组检查 continueOnFail

**修改文件：** ExecutionService.java（FormData转换 + POST body修复）, GroupExecutionService.java（并行执行）

---

### 2. 9项Bug修复 + 告警模版限频持久化 + 代理测试 + 变量引擎增强

**问题清单与修复：**

1. **告警模版限频不保存** — `AlertTemplateDTO` 缺少4字段 + Controller create/update 未映射
2. **Schema编辑器失焦** — `item-key="name"` 导致重建；改为 `_uid` 稳定键 + `Object.assign` 原地更新
3. **变量正则不匹配连字符** — `\w+` → `\w[\w\-]*`
4. **任务变量回退保存前缀错误** — catch 块中 `group.` 前缀未剥离
5. **监控项创建500** — DTO 缺 `showOnStatusPage` + 表单 `id` 残留
6. **Form Data参数编辑后丢失** — showDialog 编辑分支未恢复 formParams
7. **静默规则日期500** — el-date-picker 增加 `value-format="YYYY-MM-DD"`
8. **变量提取仅验证通过时执行** — 移除 `validationResult.valid` 条件
9. **代理测试500 + 邮件测试失败** — 代理改 HTTP；邮件增加 SSL(465) 支持

**新增功能：** 代理测试按钮（后端 + 前端 + 权限）

**修改文件：** AlertTemplateDTO, AlertTemplateController, MonitorDTO, VariableEngine, ExecutionService, ProxyConfigController, AlertService, JsonSchemaEditor, MonitorList, AlertSilenceList, ProxyList, api/index.js, permissions.js

---

## 2026-09-11

### 1. API Schema 独立管理 + 监控项集成 + 历史版本对比

**问题**：Schema 与 Monitor 绑定（api_schema.monitor_id FK），无法独立管理；MonitorDTO 缺少 schema 告警字段导致保存时丢失；历史记录仅存储变更描述，无结构化对比能力

**改动文件**：

后端：
- `MonitorDTO.java` — 新增 schemaAlertEnabled、expectedSchemaJson、schemaAlertChangeTypes、schemaAlertChannelIds
- `ApiSchemaService.java` — 新增 compareSchemaJson()（递归结构化差异）、compareVersions()（版本对比详情）；update() 方法自动记录历史
- `ApiSchemaController.java` — 新增 /history/{historyId}/compare 和 /compare 端点
- `Monitor.java` — 新增 4 个 schema 告警字段

前端：
- `ApiSchemaList.vue` — 完全重写：去掉监控项选择器；历史弹窗重写为版本列表 + 左右对照差异高亮 + 变更摘要
- `MonitorList.vue` — 编辑弹窗新增"API Schema告警"区（开关、期望Schema、变更类型多选、告警通道多选）
- `api/index.js` — 新增 compareVersions、compareSchemas API
- `Manual.vue` — API Schema 章节重写：新增 4 个 JSON Schema 案例（基础用户、分页列表、嵌套复杂结构、RESTful 通用响应）+ 编写技巧

文档：
- `README.md` / `docs/changelog.md` / `docs/overview.md` / `docs/architecture.md` / `docs/devlog.md` — 全部更新

**关键实现**：
- compareSchemaJson()：递归对比两个 JSON Schema 的每个节点，返回 List<SchemaChange>（字段路径 + 旧值/新值）
- 更新 Schema 时自动对比旧版本，有变更则写入 api_schema_history（oldSchema + newSchema 完整保存）
- 前端对比视图：左右分栏显示旧/新 Schema，高亮标记差异字段（绿=新增、红=删除、黄=修改），底部变更摘要表格

## 2026-09-13（安全审计与加固）

### 1. 安全审计修复

**背景**：对项目进行安全审计（供应链、越权、SQL注入、接口鉴权、脚本引擎沙箱），发现 3 个 CRITICAL、6 个 HIGH、9 个 MEDIUM 级别问题，逐项修复。

**改动文件**：

后端：
- `JwtTokenProvider.java` — 移除 JWT 密钥哈希日志泄露
- `PreRequestScriptEngine.java` — `allowAllAccess(true)` → `HostAccess.UNTRUSTED` 沙箱化
- `GlobalVariableService.java` — `isSecret` 变量脱敏（前2位+****+后2位）
- `ExecutionService.java` — SSRF 防护（`isInternalHost()`）、SSL 验证可配置（`monitor.ssl-verify-disabled`）
- `AuditLogController.java` — 增加 `@PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")`
- `InspectionController.java` — 写接口补全 `@PreAuthorize`
- `ApiSchemaController.java` — 写接口补全 `@PreAuthorize`
- `DashboardService.java` — `granularity` 白名单校验
- `SecurityConfig.java` — CORS origins 从硬编码改为 `cors.allowed-origins` 配置
- `BackupController.java` — 上传文件名清洗（移除路径分隔符）
- `UserService.java` — 密码确认校验 + 密码策略增强（8位+大小写+数字）
- `UserController.java` — 传递 `newPasswordConfirm` 参数

配置：
- `application.yml` — 新增 `monitor.ssl-verify-disabled`、`monitor.ssrf-protection`、`cors.allowed-origins`

依赖：
- `pom.xml` — `hutool-all` 5.8.25 → 5.8.32
- `package.json` — `axios` ^1.6.7 → ^1.7.0

### 2. 前端权限漏洞修复 + 冗余代码清理

**改动文件**：

前端权限修复：
- `MonitorList.vue` — 启用/禁用开关、分组「失败继续」开关补全 `v-permission`
- `AlertTemplateList.vue` — 启用/禁用开关补全 `v-permission`
- `permissions.js` — backup 菜单限制为 ADMIN；`checkButton()` 未定义权限默认拒绝
- `AlertList.vue` — 删除废弃文件（无路由引用、无权限检查）

后端数据泄露修复：
- `ProxyConfigController.java` — GET 接口增加 `@PreAuthorize("hasRole('ADMIN')")`
- `AlertChannelController.java` — GET 接口增加 `@PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")`

后端冗余清理：
- `UserRole.java` — 删除未使用的枚举
- `ResponseTimeAlertService.java` — 移除未使用的 `alertConfigMapper`
- `ExecutionService.java` — 移除未使用的 `paramRefs` 变量
- `GroupExecutionService.java` — 移除与 MonitorGroupService 重复的 count 方法
- `AlertSilenceService.java` — 移除未调用的 `isRecurringMatch()`
- `ExecutionLogService.java` / `MonitorService.java` / `DomainAssetService.java` / `UserSessionService.java` — 各移除 1 个未使用方法
- `V35__monitor_dependency.sql` — 删除废弃迁移文件

前端冗余清理：
- `api/index.js` — 移除 13 个未使用的 API 方法（authApi 整体、alertApi 4个、variableApi 2个、schemaApi 3个、logApi 2个、securityApi 1个）
- `utils/format.js` — 移除未使用的 `formatTimeShort`、`formatTimeFull`

### 3. 跨平台兼容性修复

- `SecuritySettingsService.java` — IP 列表分割正则 `[\\n,]` → `[\\r\\n,]`（兼容 Windows 粘贴）
- `BackupController.java` — 移除 `ATOMIC_MOVE`（Windows 文件锁定时失败）
- `start.bat` — 改用 `%~dp0` + `%JAVA_HOME%` 自动定位
- 新增 `start.sh` / `stop.sh`（Linux 本地启动脚本）
- 新增 `stop.bat`（Windows 停止脚本）

---

## 2026-09-14（可配置公开状态页 + 状态页重构 + 多主题换肤）

### 1. 公开状态页改为可配置

**问题**：原状态页固定公开（`/status` 无需认证），存在信息暴露风险；且无法按监控项筛选展示内容。

**改动文件**：

后端：
- `SecuritySettingsService.java` — 新增默认配置 `status_page_public=false` + `isStatusPagePublic()`
- `StatusPageController.java` — 匿名访问且未开启公开时返回 `Result.error(403, ...)`
- `StatusPageService.java` — 注入 `SecuritySettingsService`；监控列表与整体状态统计增加 `show_on_status_page = 1` 过滤
- `Monitor.java` — 新增 `showOnStatusPage` 字段
- `DatabaseInitConfig.java` — Migration 46：`ALTER TABLE monitor ADD COLUMN show_on_status_page INTEGER DEFAULT 1`

前端：
- `SecuritySettings.vue` — 新增「公开状态页」卡片（开关 + QuestionFilled 悬浮说明）
- `MonitorList.vue` — 「超时」后新增「公开状态页展示」开关 + 悬浮说明；form 默认值与重置补全 `showOnStatusPage`

### 2. 状态页界面重构

- `StatusPage.vue` — 完全重写为深色科技风卡片网格（自适应 1-4 列）、30 秒自动刷新、状态圆点脉冲动画、毛玻璃徽章
- 解决原单列列表在宽屏下空间利用率低的问题

### 3. 多主题换肤

- 新建 `stores/theme.js` — Pinia 主题状态，6 套主题，`localStorage` 持久化
- `MainLayout.vue` — 顶栏用户信息左侧新增换肤入口（画笔图标 + el-popover 主题网格）；侧边栏/顶栏颜色改用 CSS 变量
- `styles/index.scss` — 新增 6 套主题 CSS 变量 + 深色模式 Element Plus 适配
- `main.js` — 启动时读取 `localStorage` 立即应用主题类，避免闪烁

### 4. 深色模式适配修复（多轮）

- 表格斑马纹/悬浮/当前行背景改为不透明实色
- 固定列改用 Element Plus 2.x 正确类名 `.el-table-fixed-column--left/right`（原 `.el-table__fixed-right` 为 Element UI 旧结构，无效），修复斑马纹行透明穿透
- 弹窗表单：`el-input`、`el-textarea`、`el-input-number`、`el-select__wrapper`、`el-dialog`、`el-divider`、`el-tabs`、`el-collapse`
- 业务组件：`el-descriptions`、`code-block`、`sign-panel`、`editor-toolbar`、`section-block`、`JsonSchemaEditor`
- 其他：`el-pagination`、`el-select-dropdown`、`el-popper`、`el-alert`、`el-empty`

**关键实现**：
- Element Plus 2.x 固定列使用 sticky 定位 + `background: inherit`，继承自 `<tr>`；斑马纹行背景来自 `--el-fill-color-lighter`，若该变量为半透明 rgba 则固定列斑马纹行会透出后面列文字。修复方式：将 `--el-fill-color-lighter` 设为不透明实色，并显式覆盖 `.el-table-fixed-column--*` 单元格

---

## 2026-09-12（JSON Schema 可视化编辑器 + 美化 + Schema 校验状态 + 历史修复）

### 1. JSON Schema 可视化编辑器

新增：
- `JsonSchemaEditor.vue` — 双模式可视化编辑器（可视化 + JSON 文本）；工具栏增强：快速添加模板（分页/RESTful/用户/时间字段）；空状态引导
- `SchemaFieldRow.vue` — 递归字段行组件：两行布局（主控件行 + 扩展选项行）、必填红星标识（点击切换）、object/array 折叠展开（显示子字段数+必填数）、拖拽排序手柄
- `vuedraggable@^4.1.0` — 字段拖拽排序依赖
- `ApiSchemaList.vue` — Schema 编辑弹窗 textarea 替换为 JsonSchemaEditor，保存时校验 Schema 格式
- `MonitorList.vue` — 期望 Schema textarea 替换为 JsonSchemaEditor，保存时校验 Schema 格式
- `Manual.vue` — 文档更新：说明可视化编辑器的使用方式

**关键实现**：
- 树形视图：递归渲染字段树，每个字段可配置类型、名称、描述、必填、枚举值、默认值
- 类型切换：object/array 自动管理子字段/items；string/integer/number/boolean/null 直接切换
- 双模式切换：树形视图 ↔ JSON 编辑，切换时自动同步数据
- 校验：保存前校验根节点类型、required 数组、properties 结构等

---

### 2. 执行日志 Schema 校验状态

**数据库迁移**：Migration 39（execution_log.schema_check_status）

**改动文件**：
- `ExecutionLog.java` — 新增 `schemaCheckStatus` 字段
- `ExecutionService.checkSchemaAlert()` — 执行时记录 Schema 校验状态到主日志条目（`未启用`/`未配置Schema`/`非JSON响应`/`无响应体`/`无变更`/`无匹配变更` 或逗号分隔的变更类型如 `ADDED,MODIFIED`）；校验状态在 `updateById` 后写入
- `LogList.vue` — 执行日志列表新增「Schema」列，显示校验状态；详情弹窗显示完整状态信息

---

### 3. 巡检 Schema 变更信息

**数据库迁移**：Migration 39（inspection_detail.schema_change_info + inspection_history.schema_change_count）

**改动文件**：
- `InspectionDetail.java` — 新增 `schemaChangeInfo` 字段（TEXT，存储该监控项的 Schema 变更详情）
- `InspectionHistory.java` — 新增 `schemaChangeCount` 字段（INTEGER，本次巡检的 Schema 变更总数）
- `InspectionService.runInspection()` — 巡检完成后从 details 统计变更数写入 `history.schemaChangeCount`；每个 detail 记录其 Schema 变更信息
- `InspectionList.vue` — 历史列表新增「Schema」列（橙色数字 tag 表示有变更）；报告弹窗新增「Schema变化」统计卡片

---

### 4. Schema 历史持久化修复

**问题**：`api_schema_history` 表的外键 `FOREIGN KEY (schema_id) REFERENCES api_schema(id) ON DELETE CASCADE` 导致每次 Migration 38 重建 `api_schema` 表时级联删除所有历史记录

**根因**：Migration 38 执行 `DROP TABLE IF EXISTS api_schema` 时触发了 `ON DELETE CASCADE`，清空了 `api_schema_history` 的全部数据。每次重启后重建都会重复此过程。

**修复**：Migration 40 重建 `api_schema_history` 表，移除 `ON DELETE CASCADE` 约束，历史记录在 Schema 删除/重建后仍保留

**验证**：更新 schema 后重启服务，历史记录保持不变

---

### 5. 时间格式统一（去除 T 分隔符）

**问题**：数据库里时间字段存成 `2026-09-12T11:05:52`（T 分隔），而 SQLite `datetime()` 函数输出空格分隔；字符串比较 `>=` 时格式不一致导致筛选错乱。前端 `LogList.vue` 用 `toISOString()` 生成 UTC 时间，与库中北京时间差 8 小时。

**改动文件**：

后端：
- `config/SQLiteDateTimeHandler.java`（新增）— `BaseTypeHandler<LocalDateTime>`，`setNonNullParameter` 统一格式化为 `yyyy-MM-dd HH:mm:ss`，`parse()` 读取时兼容 T 与空格
- `util/DateTimeUtil.java`（新增）— `now()` / `format(LocalDateTime)`
- `config/JacksonConfig.java`（新增）— `Jackson2ObjectMapperBuilderCustomizer` 注册 JSR310 序列化器
- `config/MyBatisPlusConfig.java` — 自定义 `SqlSessionFactory` Bean，`configuration.getTypeHandlerRegistry().register(LocalDateTime.class, new SQLiteDateTimeHandler())`
- `LitVMonitorApplication.java` — 删除重复的 `@MapperScan`
- `service/ExecutionLogService.java` — 移除 `wrapper.apply("REPLACE(executed_at, 'T', ' ') ...")`，改 `wrapper.ge/le`
- `service/DashboardService.java` — `startTime.toString()` → `DateTimeUtil.format(startTime)`
- `service/ApiSchemaService.java` — 历史时间格式化改用 `DateTimeUtil.format()`
- `config/DatabaseInitConfig.java` — Migration 41，遍历 70+ 时间列执行 `UPDATE ... SET col = REPLACE(col, 'T', ' ') WHERE col LIKE '%T%'`

前端：
- `views/LogList.vue` — `start.toISOString().replace('T',' ')...` → `formatTime(start)`；`loadLogs()` 回退分支同样改用 `formatTime`

**踩坑**：
- `type-handlers-package: com.litv.monitor.config` 配置方式无法覆盖 MyBatis-Plus 内置的 `LocalDateTimeTypeHandler`，必须通过自定义 `SqlSessionFactory` 显式 `register()`
- `TypeHandlerRegistrar` 用 `@Autowired Configuration` 会因 Bean 不存在启动失败，改用 `SqlSessionFactory.getConfiguration()`
- API 返回格式还受 Jackson 影响，TypeHandler 只管存取库，序列化需单独配置 `JacksonConfig`

---

### 6. Schema 统计查询性能优化

**问题**：仪表盘 Schema 卡片 8 次独立 COUNT；`REPLACE(executed_at,'T',' ')` 使 `executed_at` 索引失效。

**改动文件**：
- `config/DatabaseInitConfig.java` — Migration 42 新增 `idx_execution_log_schema_status`、`idx_execution_log_executed_schema`
- `service/DashboardService.java` — `getSchemaCheckStats()` 合并为单条 SQL（`SUM(CASE WHEN ...)`）；`getUptimeStats()` 去掉 `REPLACE`/`datetime` 包装

**效果**：Schema 统计 45ms、Uptime 统计 16ms

---

### 7. 仪表盘可用性统计点击跳转

**改动文件**：
- `controller/ExecutionLogController.java` / `service/ExecutionLogService.java` — `/log/list` 新增 `status` 参数
- `views/LogList.vue` — 状态下拉框 + 接收 `status` query 参数
- `views/Dashboard.vue` — `goLogUptime(status)`，成功/失败/总次数点击跳转

---

### 8. 仪表盘时间窗口持久化

**改动文件**：
- `views/Dashboard.vue` — `uptimeHours` / `schemaHours` 初始化从 `localStorage` 读取，`watch` 写回，避免切换菜单后重置

---

### 9. 移除依赖拓扑功能

**背景**：评估后认为该功能与本系统场景不匹配——仅可视化、不参与告警/执行逻辑；自动发现把任务内执行顺序误判为服务依赖；影响分析未接入 UI 且结果有重复。

**改动文件**：

后端（删除）：
- `controller/DependencyController.java`、`service/DependencyService.java`、`entity/MonitorDependency.java`、`mapper/MonitorDependencyMapper.java`
- `config/DatabaseInitConfig.java` — 移除 Migration 35 建表块；移除 Migration 41 fixes 中 `{"monitor_dependency", "created_at"}`；新增 Migration 43 `DROP TABLE IF EXISTS monitor_dependency`

前端（删除/修改）：
- 删除 `views/DependencyTopology.vue`
- `router/index.js` — 移除 `/dependency` 路由
- `permissions.js` — 移除 dependency 菜单项 + 按钮权限
- `api/index.js` — 移除 `dependencyApi`
- `views/Manual.vue` — 移除依赖拓扑章节

文档：
- `README.md` / `docs/overview.md` / `docs/architecture.md` / `docs/operation.md` — 移除功能描述
- `docs/changelog.md` / `docs/devlog.md` — 追加移除记录（保留历史）

---

### 10. 修复巡检历史「开始时间」为空

**问题**：巡检历史列表开始时间无值。实测发现自定义 `SQLiteDateTimeHandler` 未生效（新写入仍是 `2026-09-12T13:33:23.415825700`），而 Migration 41 已把历史数据转为空格分隔，默认 MyBatis 读取器只认 ISO 的 T 分隔，空格格式解析为 null。

**根因**：
- 自定义 `SqlSessionFactory` Bean 中显式 `register()` 未生效
- `SQLiteDateTimeHandler` 带 `@MappedJdbcTypes`，被注册到特定 JdbcType 键，默认处理器仍命中通用键
- 解析器用 `LocalDateTime.parse`（要求 ISO 的 T），无法解析空格格式

**改动文件**：
- `config/MyBatisPlusConfig.java` — 移除自定义 `SqlSessionFactory` Bean，改用 `ConfigurationCustomizer` 注册 TypeHandler
- `config/SQLiteDateTimeHandler.java` — 移除 `@MappedJdbcTypes`；`parse()` 兼容 T/空格 + 小数秒
- `config/DatabaseInitConfig.java` — 新增 Migration 44：所有时间列 `substr(replace(col,'T',' '),1,19)`

**验证**：巡检历史 `startedAt`/`completedAt` 正常；新写入 `2026-09-12 13:37:01`；全表 T 残留 0；时间范围筛选与仪表盘统计正常。

---

### 11. 100+ 检测项性能与稳定性优化（P0 + P1）

**背景**：100 个检测项时，单次执行约 18-24 次 DB 操作，其中变量引擎占大头（每次替换都全量加载 global+group 变量）；HTTPS 每次执行都做 TLS 握手；SQLite 未开 WAL、无 busy_timeout；日志表无限增长。

**改动文件**：

后端：
- `service/VariableEngine.java` — 新增 `loadVariables(groupId)` 与接收预加载 Map 的 `replaceVariables`/`replaceVariablesWithTrace` 重载
- `service/ExecutionService.java` — 执行开始时加载一次变量并复用；Schema/响应时间告警移到 insert 前（合并写）；`toJson` 用静态单例 ObjectMapper；请求/响应体入库截断；域名资产记录节流；OkHttp 连接池 10→50
- `service/SslService.java` — SSL 检查按 `domain:port` TTL 缓存（`ssl-check-interval-minutes`）
- `service/DataRetentionService.java`（新增）— 每日 03:30 自动清理超期执行/告警日志
- `service/AlertSilenceService.java` — 启用静默列表 30 秒缓存 + 变更失效
- `config/DatabaseInitConfig.java` — 启动开启 WAL；Migration 45 删除孤儿表 `report_config`/`report_history`
- `controller/BackupController.java` — 备份前 `wal_checkpoint(TRUNCATE)`
- `resources/application.yml` — 连接池 20、`busy_timeout=5000`、新增 `monitor.*` 配置项

**验证**：`journal_mode=wal`；孤儿表已删除；索引保留；监控测试 SUCCESS；仪表盘/日志正常；新写入时间格式正确。

---

## 2026-09-10

### 1. 告警渠道平台扩展（钉钉/企业微信/飞书）

**改动文件**：
- `ChannelType.java` — 新增枚举值：DINGTALK, WECHAT, FEISHU
- `AlertService.java` — 新增 `sendDingTalk()`, `sendWechat()`, `sendFeishu()` 方法
- `AlertChannelList.vue` — 根据平台类型动态切换配置表单（钉钉/企业微信/飞书各有专属配置项）

**新增功能**：
- 钉钉 Webhook：支持关键词安全设置
- 企业微信 Webhook：支持 Markdown 消息格式
- 飞书 Webhook：支持富文本消息格式

---

### 2. Uptime 统计

**改动文件**：
- `DashboardService.java` — 新增 `getUptimeStats()` 方法，计算 1h/6h/24h/7d/30d 可用率
- `DashboardController.java` — 新增 `GET /dashboard/uptime` 接口
- `Dashboard.vue` — 新增 Uptime 卡片，支持时间窗口切换

---

### 3. 公开状态页

**改动文件**：
- `StatusPageController.java` — 新增 `GET /status` 接口（无需认证）
- `StatusPageService.java` — 聚合监控状态、SSL证书、最近告警数据
- `SecurityConfig.java` — 新增 `/status/**` permitAll 规则
- `StatusPage.vue` — 公开状态页前端（使用集中式 API）
- `router/index.js` — `/status` 路由 `requiresAuth: false`

---

### 4. API Schema 变更检测

**改动文件**：
- `DatabaseInitConfig.java` — Migration 28-29：`api_schema` + `api_schema_history` 表
- `ApiSchema.java` — 实体类
- `ApiSchemaHistory.java` — 实体类
- `ApiSchemaMapper.java` — Mapper
- `ApiSchemaHistoryMapper.java` — Mapper
- `ApiSchemaService.java` — 自定义 JSON Schema 校验（type、required、嵌套属性、数组）+ 变更检测
- `ApiSchemaController.java` — CRUD + 校验 + 历史接口
- `ApiSchemaList.vue` — 前端页面
- `api/index.js` — 新增 `schemaApi`

**数据库迁移**：
- Migration 28：创建 `api_schema` 表
- Migration 29：创建 `api_schema_history` 表

---

### 5. 巡检模式

**改动文件**：
- `DatabaseInitConfig.java` — Migration 32-34：`inspection_config` + `inspection_history` + `inspection_detail` 表
- `InspectionConfig.java` — 实体类
- `InspectionHistory.java` — 实体类
- `InspectionDetail.java` — 实体类
- `InspectionConfigMapper.java` — Mapper
- `InspectionHistoryMapper.java` — Mapper
- `InspectionDetailMapper.java` — Mapper
- `InspectionService.java` — @Async 批量执行 + 巡检报告生成
- `InspectionController.java` — 配置 + 历史 + 执行接口
- `InspectionList.vue` — 前端页面（含详情弹窗）
- `api/index.js` — 新增 `inspectionApi`

**数据库迁移**：
- Migration 32：创建 `inspection_config` 表
- Migration 33：创建 `inspection_history` 表
- Migration 34：创建 `inspection_detail` 表

---

### 7. 依赖拓扑

**改动文件**：
- `DatabaseInitConfig.java` — Migration 35：`monitor_dependency` 表
- `MonitorDependency.java` — 实体类
- `MonitorDependencyMapper.java` — Mapper
- `DependencyService.java` — 拓扑图数据 + 影响分析
- `DependencyController.java` — CRUD + 拓扑 + 影响分析接口
- `DependencyTopology.vue` — Canvas 拓扑图 + 影响分析弹窗
- `api/index.js` — 新增 `dependencyApi`

**数据库迁移**：
- Migration 35：创建 `monitor_dependency` 表

---

### 8. Alert Log FK 约束修复

**改动文件**：
- `DatabaseInitConfig.java` — Migration 36：重建 `alert_log` 表移除 FK 约束（日志表不应有 FK）
- `AlertService.java` — 防御性检查：插入 alert_log 前验证 monitor 是否存在

**原因**：删除监控项后，alert_log 中的外键约束导致查询异常。日志表应独立于业务表。

---

### 9. 前端权限修复

**改动文件**：
- `ApiSchemaList.vue` — 新增 `v-permission` 指令
- `ReportList.vue` — 新增 `v-permission` 指令
- `InspectionList.vue` — 新增 `v-permission` 指令
- `DependencyTopology.vue` — 新增 `v-permission` 指令
- `StatusPage.vue` — 从 raw axios 改为集中式 `statusApi` + `formatTime`

---

### 10. 文档更新

**改动文件**：
- `README.md` — 更新项目描述、结构计数
- `docs/overview.md` — 新增功能清单（20-26）、权限矩阵、按钮权限
- `docs/architecture.md` — 新增表结构、API接口、实体/Mapper计数
- `docs/operation.md` — 新增路由表、侧边栏菜单
- `docs/devlog.md` — 本条开发日志
- `docs/changelog.md` — 版本变更记录

---

## 2026-09-09

### 1. 性能与安全优化（14项）

**改动文件**：
- `JwtTokenProvider.java` — JWT Secret 自动拼接 MAC 地址 SHA-256 哈希
- `application.yml` — 分离 `jwt.secret` 和 `jwt.base-secret`；启用 SQLite 外键 `PRAGMA foreign_keys = ON`；日志级别改为 INFO；移除 `StdOutImpl`
- `JwtAuthenticationFilter.java` — `getClientIp()` 简化为 `request.getRemoteAddr()`，移除 X-Forwarded-For 信任
- `AuthController.java` — 同上
- `BackupController.java` — 新增 `validateBackupPath()` 防止路径穿越
- `GlobalExceptionHandler.java` — 新增全局异常处理器
- `SysUser.java` — 密码字段加 `@JsonIgnore`
- `pom.xml` — 新增 Caffeine 依赖；移除重复 mybatis-plus-boot-starter（Spring Boot 2 版本）；移除 websocket、quartz、mapstruct 依赖
- `AlertService.java` — ConcurrentHashMap 改 Caffeine Cache（2h/1000）
- `GroupExecutionService.java` — ConcurrentHashMap 改 Caffeine Cache（1h/500）
- `ResponseTimeAlertService.java` — ConcurrentHashMap 改 Caffeine Cache（1h/500）
- `MonitorScheduler.java` — ConcurrentHashMap 改 Caffeine Cache（24h/200）
- `UserSessionService.java` — ConcurrentHashMap 改 Caffeine Cache（30m/500）
- `VariableEngine.java` — `new ObjectMapper()` 改为注入 Spring 单例
- `AlertSilenceService.java` — 同上
- `MonitorService.java` — N+1 查询修复，`selectBatchIds` 批量查询
- `DashboardService.java` — SQL `COUNT(DISTINCT)` 替代全表加载
- `router/index.js` — 路由守卫从 Pinia Store 读角色
- `MainLayout.vue` — 登出确认弹窗
- 8个 Vue 文件 — 移除 `error.response?.data?.message` 冗余写法
- `DatabaseInitConfig.java` — Migration 27：6个补充索引

**遇到的问题**：无

---

### 2. WebSocket 及无用依赖清理

**改动文件**：
- 删除 `WebSocketConfig.java`
- `SecurityConfig.java` — 移除 `/ws/**` permitAll 规则
- `pom.xml` — 移除 `spring-boot-starter-websocket`、`spring-boot-starter-quartz`、`mapstruct` + `mapstruct-processor`
- `package.json` — 移除 `socket.io-client`

**原因**：前端代码中未使用 WebSocket，socket.io-client 依赖已安装但未引用；Quartz 依赖已引入但项目仅使用 `@Scheduled`；MapStruct 未使用。

---

### 3. 审计日志 requestUrl 字段

**改动文件**：
- `AuditLog.java` — 新增 `requestUrl` 字段
- `AuditLogService.java` — 新增 `buildFullUrl()` 方法，拼接 requestURL + queryString
- `AuditLogList.vue` — 新增"请求地址"列
- `DatabaseInitConfig.java` — Migration 26：`audit_log` 表新增 `request_url` 字段

---

### 4. 变量管理 VIEWER 权限限制

**改动文件**：
- `GlobalVariableController.java` — 所有读写端点加 `@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")`
- `permissions.js` — 变量管理菜单 `roles: ['ADMIN', 'OPERATOR']`

---

### 5. 文档拆分

**改动文件**：
- 重写 `README.md` — 精简为入口文档，含文档索引表
- 新建 `docs/overview.md` — 项目说明（功能清单、技术栈、权限矩阵）
- 新建 `docs/operation.md` — 操作说明（快速启动、配置、变量系统、执行流程、预请求脚本API参考）
- 新建 `docs/architecture.md` — 软件架构（项目结构、数据库设计、API接口、性能、安全）
- 新建 `docs/development.md` — 开发规范（后端/前端/数据库规范、14个Bug踩坑记录）
- 新建 `docs/changelog.md` — 迭代修复记录（版本变更级别的记录）

---

### 6. 使用手册内容补充到文档

**改动文件**：
- `docs/operation.md` — 补充预请求脚本 API 参考（request对象、快捷函数、crypto函数、util函数）、6个代码示例、SSL证书检测机制两种方式、证书状态说明
- `docs/architecture.md` — 补充告警记录状态说明表（SENT/FAILED/NO_CHANNEL/SUPPRESSED/SILENCED/CONFIG_UNAVAILABLE）
- `docs/architecture.md` — 补充 RBAC 权限控制实现原理（前后端完整链路、角色定义、扩展方式）

---

## 历史记录（迁移自原 README.md 踩坑记录）

以下 Bug 记录已迁移至 `docs/development.md`，此处仅保留索引。

| # | Bug | 根因 |
|---|-----|------|
| 1 | 添加任务变量返回 403 | SQLite NOT NULL 约束 + Security 403 映射 |
| 2 | 原生 axios 导致 403 | 未使用带 JWT 拦截器的 api 实例 |
| 3 | 变量赋值配置不保存 | 未使用带 JWT 拦截器的变量 API |
| 4 | 测试告警异常不返回前端 | @Async 异常被吞 |
| 5 | 邮件发送端口类型错误 | Integer.parseInt 缺失 |
| 6 | 变量不存在时保留模板原文 | 变量未找到应返回空字符串 |
| 7 | 全局 axios 拦截器未处理业务错误 | response.data.code 判断缺失 |
| 8 | 任务变量删除 groupId 约束 | group_id 应允许 NULL |
| 9 | Vue 模板解析器误解析 `{{` | 使用 v-text 指令替代 |
| 10 | 响应时间告警配置不持久化 | @TableField + LambdaUpdateWrapper |
| 11 | 告警记录 sentAt 显示 null | 缺少显式 setSentAt |
| 12 | 任务定时执行间隔变为 2 分钟 | Duration.toMinutes() 截断问题 |
| 13 | 无角色权限控制 | JWT role claim + @PreAuthorize |
| 14 | 任务失败告警永远不触发 | alertConfigIds 为空 |
