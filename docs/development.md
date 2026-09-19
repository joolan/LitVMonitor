# 开发规范

## 后端规范

1. **RESTful 风格**：资源名用复数（/monitor, /group），操作用 HTTP 方法区分
2. **统一响应格式**：所有接口返回 `Result<T>`，包含 code/message/data
3. **异常处理**：Controller 层不做 try-catch，由框架统一处理
4. **DTO 分离**：请求参数用 DTO，响应直接返回 Entity（MyBatis-Plus 自动映射）
5. **Service 层事务**：涉及多表操作的方法加 `@Transactional`
6. **密码加密**：BCrypt
7. **日志级别**：DEBUG（开发环境），ERROR（异常），WARN（降级）
8. **数据库迁移**：`DatabaseInitConfig` 中按版本号依次执行迁移脚本（共 27 个迁移），每个迁移用 try-catch 包裹

## 前端规范

1. **API 统一封装**：所有请求通过 `api/index.js` 的 `api` 实例（带 JWT 拦截器），不直接使用原生 `axios`
2. **组件命名**：PascalCase（`MonitorList.vue`）
3. **状态管理**：Pinia store 管理用户状态
4. **路由守卫**：未登录跳转 `/login`，已登录访问 `/login` 跳转 `/dashboard`
5. **Element Plus**：统一使用 Element Plus 组件库
6. **模板变量显示**：使用 `v-text` 指令显示包含 `{{` 的文本，避免 Vue 模板解析器误解析
7. **操作说明**：关键配置区域提供克制且必要的操作说明，简单说明使用 `InfoFilled` 图标 + 文字，复杂说明使用 `QuestionFilled` 图标 + `el-tooltip` 悬浮展示
8. **使用手册**：独立菜单页面，折叠章节式布局，涵盖快速入门、系统架构、功能说明

## 数据库规范

1. **表命名**：snake_case（`execution_log`）
2. **字段命名**：snake_case（`created_at`），MyBatis-Plus 自动映射 camelCase
3. **主键**：自增 INTEGER
4. **时间字段**：`created_at` / `updated_at`，由 `MetaObjectHandler` 自动填充
5. **外键**：使用 `ON DELETE CASCADE` 或 `ON DELETE SET NULL`
6. **迁移策略**：ALTER TABLE 增加字段用 try-catch 包裹，已存在则跳过

---

## 开发经验与踩坑记录

### Bug 1: 添加任务变量返回 403

**现象**：前端调用 `POST /variable/group` 创建任务变量时返回 403。

**根因**：不是权限问题，而是数据库约束错误。`group_variable` 表的 `group_id` 列定义为 `NOT NULL`，但任务变量不再绑定任务（`groupId` 为 null），插入时触发 `SQLiteException: NOT NULL constraint failed`。该异常被 Spring Security 的 `ExceptionTranslationFilter` 捕获后，映射为 HTTP 403 状态码返回。

**修复**：
1. `schema.sql`：`group_id INTEGER NOT NULL` → `group_id INTEGER`（允许 NULL）
2. `DatabaseInitConfig.java`：添加启动时迁移逻辑，重建 `group_variable` 表使 `group_id` 可 NULL

**教训**：Spring Security 的 `ExceptionTranslationFilter` 会将 Controller 层抛出的任何异常（包括数据库约束异常）映射为 403，不一定代表真正的权限问题。排查 403 时要先看完整异常栈，不要只看 HTTP 状态码。

---

### Bug 2: 原生 axios 导致 403（无 JWT Token）

**现象**：前端多个页面（VariableList、MonitorList、UserList）的 API 调用返回 403。

**根因**：这些页面直接使用 `import axios from 'axios'` 发请求，而非项目统一封装的 `api` 实例（`api/index.js` 中的 `axios.create()` 带有 JWT 拦截器）。原生 axios 不携带 `Authorization: Bearer xxx` 头，Spring Security 拒绝请求。

**修复**：
1. `api/index.js`：新增 `variableApi` 和 `userApi`
2. 所有 `.vue` 文件：移除 `import axios from 'axios'`，统一使用 `api/index.js` 导出的 API 方法

**教训**：Axios 实例的拦截器不会被原生 axios 共享。项目中应统一通过封装好的 API 模块发请求，禁止直接使用原生 axios。

---

### Bug 3: 变量赋值配置不保存 / 全局变量不更新

**现象**：在监控项中配置变量赋值（下拉选择变量），保存后重新打开配置为空；或执行监控后全局变量值未更新。

**修复**：
1. 统一使用 `variableApi.listAllGroup()`（带 JWT 拦截器）
2. 后端 `extractVariables()` 兼容旧格式：无前缀时根据 groupId 判断存入全局或任务变量

---

### Bug 4: 测试告警异常不返回前端

**现象**：测试告警失败时，前端只显示"测试失败"，无法看到具体错误原因。

**根因**：`AlertService.sendAlert()` 是 `@Async` 方法，异常被吞掉（异步线程中抛出的异常不会传播到调用方）。而测试接口需要同步反馈结果。

**修复**：新增 `sendAlertSync()` 方法（非 `@Async`），测试接口调用同步方法，异常直接抛出 → Controller 层捕获 → 返回具体错误信息给前端。

---

### Bug 5: 邮件发送端口类型错误

**现象**：邮件告警测试失败，报端口类型转换异常。

**修复**：`Integer.parseInt(config.get("smtpPort").toString())`

---

### Bug 6: 变量不存在时保留模板原文

**现象**：URL 中使用 `{{global.baseUrl}}`，但该变量未创建时，请求 URL 变为 `http://{{global.baseUrl}}/api`，导致请求失败。

**修复**：变量未找到时返回空字符串。

---

### Bug 7: 全局 axios 响应拦截器未处理业务错误

**现象**：后端返回 `{ "code": 500, "message": "xxx" }`（HTTP 200），前端 `catch` 中 `error.response` 为 undefined。

**修复**：
```js
const res = response.data
if (res.code && res.code !== 200) {
  return Promise.reject(new Error(res.message || '请求失败'))
}
return res
```

---

### Bug 8: 任务变量删除 groupId 约束

**现象**：任务变量绑定到特定任务，无法跨任务使用。

**修复**：任务变量改为全局可用（`group_id` 可 NULL），任何监控项都可写入任意任务变量。前端去掉"所属任务"选择器，任务变量只需定义变量名。

---

### Bug 9: Vue 模板解析器误解析 `{{` 字符

**现象**：模板中使用 `{{global.xxx}}` 显示变量语法提示时，Vite 报错。

**修复**：使用 `v-text` 指令替代 `{{ }}` 插值：
```html
<code v-text="'{{global.xxx}}'"></code>
```

---

### Bug 10: 响应时间告警配置不持久化

**现象**：编辑监控开启响应时间告警，保存成功但刷新后仍为关闭状态。

**修复**：
1. `Monitor.java`：4 个响应时间字段加 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`
2. `MonitorService.updateMonitor()`：追加显式 `LambdaUpdateWrapper`，强制写入 4 个响应时间字段
3. `MonitorList.vue`：`bodyType` 合并进 `form` reactive；增加 `watch` 自动同步

---

### Bug 11: 告警记录 sentAt 显示为 null

**修复**：
1. `AlertService.recordAlertLog()`：显式设置 `alertLog.setSentAt(LocalDateTime.now())`
2. `AlertLog.java`：移除无效的 `@TableField(fill = FieldFill.INSERT)` 注解

---

### Bug 12: 任务定时执行间隔变为 2 分钟

**根因**：`Duration.between().toMinutes()` 截断取整导致跳过。

**修复**：改用 `ChronoUnit.MINUTES.between(lastExec.truncatedTo(MINUTES), now.truncatedTo(MINUTES))`

---

### Bug 13: 无角色权限控制

**修复**：
1. JWT Token 内嵌 `role` claim
2. `JwtAuthenticationFilter` 从 Token 读取 role
3. 所有 Controller 写操作添加 `@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")`

---

### Bug 14: 任务失败告警永远不触发

**根因**：`consecutiveFailCounts` 跟踪连续失败次数，但 `alertConfigIds` 为空导致所有告警记录为 `NO_CHANNEL` 状态。

**修复**：确保任务的 `alertConfigIds` 设置为有效的告警通道 ID。

---

### Bug 15: SQLite 时间字段 T 分隔符导致筛选错乱

**根因**：SQLite 无原生日期类型，MyBatis-Plus 默认把 `LocalDateTime` 以 ISO 格式（`2026-09-12T11:05:52`）写入 TEXT 列；而 SQLite `datetime()` 函数返回空格分隔（`2026-09-12 11:05:52`），两者字符串比较不一致。

**修复**：
1. Migration 41 批量 `REPLACE(col, 'T', ' ')` 修复存量数据
2. 新增 `SQLiteDateTimeHandler` 统一写入格式
3. 新增 `JacksonConfig` 统一 API 响应格式

---

### Bug 16: 自定义 TypeHandler 无法覆盖 MyBatis-Plus 默认处理器

**根因**：`mybatis-plus.type-handlers-package` 只能追加注册，无法覆盖内置的 `LocalDateTimeTypeHandler`；`TypeHandlerRegistrar` 注入 `Configuration` Bean 也会因 Bean 不存在启动失败。

**修复**：自定义 `SqlSessionFactory` Bean，在 `MybatisConfiguration` 上显式调用 `getTypeHandlerRegistry().register(LocalDateTime.class, new SQLiteDateTimeHandler())`。

---

### Bug 17: 前端 toISOString 时区差 8 小时

**根因**：`new Date().toISOString()` 返回 UTC 时间，数据库存的是北京时间（UTC+8），仪表盘跳转后查询时间范围偏移 8 小时。

**修复**：统一使用 `utils/format.js` 的 `formatTime()`（`dayjs().tz('Asia/Shanghai')`），与 `el-date-picker` 的 `value-format` 保持一致。

---

### Monitor 测试按钮改进

**改进 1：加载状态 + 防重复点击**
- 测试按钮点击后显示"测试中..."并禁用
- 使用 `testingId` ref 追踪当前测试的监控 ID

**改进 2：详细验证失败信息**
- `ValidationResult` 结构化验证结果
- 失败信息细分：状态码不匹配、期望文本未找到、JSONPath 值不匹配、JSONPath 表达式无效

---

### Bug 18: JDBC URL 带查询参数导致备份接口 500

**现象**：`/backup/list` 返回 `Illegal char <?> at index ...: ./litv-monitor.db?busy_timeout=5000&...`。

**根因**：为让连接池每个连接生效，PRAGMA 通过 JDBC URL 下发（`?busy_timeout=...&journal_mode=WAL&...`）；`BackupController.extractDbPath()` 直接把 URL 去掉 `jdbc:sqlite:` 前缀当文件路径，`?` 之后的参数被当作文件名。

**修复**：`extractDbPath()` 在 `Paths.get()` 前先剥离 `?` 之后的查询参数。

---

### Bug 19: 登出接口返回 401 导致前端无法退出

**现象**：点击退出登录，`POST /api/auth/logout` 返回 401，页面停在原地不跳转登录页。

**根因**：
1. `/auth/logout` 需要认证，token 失效时（改密、重启、JWT 密钥变化）登出本身也 401；
2. `MainLayout.vue` 把 `logout()` 的异常与"用户取消"放在同一个 `catch`，401 被当成取消，`router.push('/login')` 不执行。

**修复**：
1. `SecurityConfig`：`/auth/logout` 加入 `permitAll`；`JwtAuthenticationFilter` 对 `/auth/*` 跳过会话失效校验
2. `MainLayout.vue`：确认弹窗与登出异常分离，登出后无条件清理并跳转；`stores/user.js` 的 `logout()` 改为永不 reject
3. 顺带修复 JWT `atob` 未处理 base64url 导致 `jti` 解析失败

---

### Bug 20: 预请求脚本从不执行（签名大小写不生效的根因）

**现象**：签名脚本中的 `.toLowerCase()` 不生效，实际签名始终为内置大写。

**根因**：`PreRequestScriptEngine` 创建 GraalJS `Context` 时使用了无效/实验性选项（`js_bigint`、`js.small-ic`、`js.commonjs-require`），`Context.build()` 直接抛异常，**脚本从未执行**，只有内置签名生效；且 `crypto.md5` 引用了不存在的 polyfill。

**修复**：
1. 移除无效选项，保留 `js.ecmascript-version=2023`、`js.strict=true`
2. 新增 `ScriptRuntime` 宿主类实现全部 crypto/util 方法，通过 `ProxyExecutable` 暴露（`HostAccess.UNTRUSTED` 下 `java.*` 不可用）
3. 存在自定义脚本时不再执行内置签名，避免双重签名
4. 回写请求改动时兼容数字类型
