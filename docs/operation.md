# 操作说明

## 快速启动

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- npm

### 后端启动

```bash
cd litv-monitor-server
mvn spring-boot:run
```

后端启动后：
- 监听端口：`8088`
- 上下文路径：`/api`
- SQLite 数据库文件：`litv-monitor-server/litv-monitor.db`（WAL 模式，自动创建）
- ⚠️ 必须在 `litv-monitor-server` 目录下启动（数据库路径为相对路径，否则会生成空库）
- 首次启动自动创建管理员账户 `admin`，初始密码**随机生成并仅在启动日志输出一次**（可用 `ADMIN_INIT_PASSWORD` 预设）；首次登录强制改密

### 前端启动

```bash
cd litv-monitor-web
npm install
npm run dev
```

前端启动后：
- 监听端口：`5173`
- API 代理：`/api` → `http://localhost:8088`
- 访问：`http://localhost:5173`

---

## 配置说明

### application.yml

```yaml
server:
  port: 8088
  servlet:
    context-path: /api

app:
  version: 1.3.1

spring:
  datasource:
    # PRAGMA 通过 JDBC URL 下发，确保连接池中每个连接都生效
    url: jdbc:sqlite:./litv-monitor.db?busy_timeout=5000&journal_mode=WAL&synchronous=NORMAL&foreign_keys=on
  mail:
    host: ${MAIL_HOST:smtp.gmail.com}
    port: ${MAIL_PORT:587}

jwt:
  secret: ${JWT_SECRET:}            # 至少 32 字符；未设置时自动生成随机密钥
  secret-file: ${JWT_SECRET_FILE:./.jwt-secret}  # 随机密钥持久化位置
  expiration: 86400000  # 24小时

monitor:
  default-timeout: 30
  max-concurrent: 10
  retry-interval: 60
  ssl-check-days-warning: 30
  ssl-check-interval-minutes: 360   # 同一域名 SSL 证书两次检查最小间隔（分钟）
  domain-asset-update-interval-minutes: 10  # 同一域名资产/IP 记录最小更新间隔（分钟）
  log-body-max-size: 262144         # 执行日志请求/响应体入库最大字节数（0=不限制）
  log-retention-days: 30            # 执行/告警日志自动保留天数（0=不自动清理）
  ssl-verify-disabled: ${SSL_VERIFY_DISABLED:true}  # 是否跳过 SSL 证书校验（true 兼容自签证书）
  ssrf-protection: ${SSRF_PROTECTION:true}          # 是否禁止访问内网/回环/云元数据地址

# 代理配置存储在 proxy_config 表中
# 通过"代理设置"菜单管理（仅ADMIN）
# 支持 HTTP / SOCKS5 代理，含认证
```

> SQLite 通过 JDBC URL 下发 `busy_timeout=5000&journal_mode=WAL&synchronous=NORMAL&foreign_keys=on`，连接池每个连接均生效。执行日志与告警日志每日 03:30 自动清理超期数据。

### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `ADMIN_INIT_PASSWORD` | 随机生成 | 首次创建 `admin` 时使用的初始密码 |
| `JWT_SECRET` | 自动生成并持久化到 `.jwt-secret` | JWT 签名密钥，设置时**至少 32 字符** |
| `JWT_SECRET_FILE` | `./.jwt-secret` | 未设置 `JWT_SECRET` 时随机密钥的存放路径 |
| `SSL_VERIFY_DISABLED` | `true` | 是否跳过监控请求的 SSL 证书校验 |
| `SSRF_PROTECTION` | `true` | SSRF 防护的初始默认值（实际模式在「安全设置 → 监控请求安全」配置：strict/allow_internal/off） |
| `MAIL_HOST` | smtp.gmail.com | SMTP 服务器 |
| `MAIL_PORT` | 587 | SMTP 端口 |
| `MAIL_USERNAME` | - | SMTP 用户名 |
| `MAIL_PASSWORD` | - | SMTP 密码 |
| `CORS_ORIGINS` | http://localhost:5173,http://localhost:3000 | 允许的跨域来源 |

---

## 变量系统

### 变量语法

```
{{scope.name}}
```

> 变量名支持字母、数字、下划线和连字符，如 `{{global.applet-test-host}}`、`{{group.my-token}}`

| Scope | 示例 | 说明 |
|-------|------|------|
| `global` | `{{global.baseUrl}}` | 全局变量，持久存储，所有监控共享 |
| `group` | `{{group.token}}` | 任务变量，每次任务运行时重置 |
| `env` | `{{env.timestamp}}` | 即时变量，每次请求重新生成，北京时间 |

### 使用场景

1. **监控间数据联动**：第一个监控提取 token → 存入任务变量 → 后续监控在请求头中使用 `{{group.token}}`
2. **环境切换**：全局变量 `baseUrl` 存储 API 地址，所有监控引用 `{{global.baseUrl}}`
3. **动态参数**：使用 `{{env.timestamp}}` 生成唯一请求参数，`{{env.uuid}}` 生成唯一标识，`{{env.nonce}}` 生成随机字符串
4. **预请求脚本**：脚本中直接使用变量（如 `{{global.secretKey}}`），变量在脚本执行前已替换为实际值

### 变量赋值（监控项配置）

每个监控项可配置变量提取规则，支持三种来源：

```
变量名（下拉选择）  →  来源      →  路径                    →  默认值（可选）
global.token          响应体      →  $.data.token              null
group.sessionId       响应头      →  x-session-id              null
global.cookie         Cookie     →  session_id                null
```

- **响应体**：从 JSON 响应中用 JSONPath 提取（如 `$.data.token`）
- **响应头**：按 Header 名称提取（大小写不敏感，如 `x-token`）
- **Cookie**：按 Cookie 名称提取（从 `Set-Cookie` 头解析）

执行监控后，从对应来源提取值，自动写入对应的全局/任务变量。

### 变量追踪

执行日志中记录变量的引用和设置信息：

**变量引用**（替换前 → 替换后）：
```json
{
  "global.baseUrl": "https://httpbin.org",
  "group.token": "abc123"
}
```

**变量设置**（来源:路径 => 写入值）：
```json
{
  "global.token": "body:$.url => https://httpbin.org/get",
  "group.sessionId": "header:x-session-id => sess_xyz",
  "global.cookie": "cookie:session_id => abc123"
}
```

---

## 执行流程

### 单个监控执行

```
1. 加载监控配置
2. VariableEngine 替换 URL/请求头/请求体中的变量
   → 记录变量引用 (variableReferences)
3. PreRequestScriptEngine 执行预请求脚本（变量替换之后）
   → 脚本中的变量已被替换为实际值（如 {{global.secretKey}} → 真实密钥）
   → 执行内置签名函数（如有）
   → 执行自定义 JS 脚本（如有）
   → 可修改 method/url/headers/params/body
4. OkHttp 发送 HTTP 请求（经过启用的代理）
5. 验证响应：状态码 → 包含文本 → JSONPath 表达式 → 正则表达式 → 响应体大小
5. 如果配置了变量提取 → 执行 extractVariablesWithTrace()（无论验证是否通过）
   - global.xxx → setGlobalVariable() → 写入 global_variable 表
   - group.xxx  → setGroupVariable()  → 写入 group_variable 表
   → 记录变量设置 (variableSettings)
6. 记录域名证书 + SSL 证书信息（URL、域名、IP 写入 execution_log）
   → 若域名开启了"执行时告警"且证书已过期/证书错误 → 立即告警
7. 保存 execution_log (含 url, executionId, variableReferences, variableSettings)
8. ResponseTimeAlertService.checkAndAlert() 检查响应时间告警
   → 连续超阈值达到指定次数 → 触发告警
9. checkSchemaAlert() 检查 API Schema 变更
   → 响应是 JSON → 用 expectedSchemaJson 递归对比
   → 过滤匹配的变更类型 → 通过告警通道发送通知（triggerType=SCHEMA_CHANGE）
10. 返回 ExecutionLog
```

### 任务执行

```
1. 生成 executionId (UUID)，清空所有任务变量 (clearGroupVariables)
2. 按 sortOrder 排序遍历监控项，逐个执行 executeMonitor()
3. 每个监控项的执行日志共享同一 executionId
4. 如果某监控失败 且 continueOnFail=false → 停止执行
5. 统计失败数量，超过阈值 → 触发告警（记录任务来源 + executionId）
   → 无论是否配置告警通道，均写入 alert_log
   → 有通道则逐通道记录发送结果
6. 异步返回 CompletableFuture
```

### Cron 调度

```
MonitorScheduler 每分钟检查:
1. 查询所有 enabled=true 且 cronExpression 不为空的任务
2. 根据 scheduleType 判断是否该执行:
   - interval: 上次执行距今 >= N 分钟（截断到分钟边界比较，避免亚秒级精度导致跳过）
   - daily: 当前时分匹配 cronExpression
   - weekly: 当前星期+时分匹配 cronExpression
3. 匹配则触发 executeGroup()
4. 记录上次执行时间，避免同一分钟内重复执行
```

### SSL 证书检查

```
每日 9:00 定时任务 + 监控执行时（若开启执行时告警）:
1. 查询域名证书（含 sslAlertEnabled=true 的域名）
2. 连接域名获取 SSL 证书，校验证书 SAN 是否匹配域名（含通配符）
3. 计算剩余天数，判定状态：VALID / EXPIRED / MISMATCH
4. 告警条件：
   - 证书错误 (MISMATCH) → 告警
   - 剩余天数 <= 0（已过期） → 告警
   - 剩余天数 <= sslAlertDaysBefore → 告警
   - 剩余天数 == 1 且 sslAlertOneDayBefore=true → 告警
5. 优先使用新 AlertChannel + 告警模板发送，找不到则回退到旧 AlertConfig
6. 无渠道时写入 NO_CHANNEL 告警记录
```

**SSL 证书检测机制（两种方式）**：
- **监控执行时检测**：每次 HTTPS 监控项执行时，自动进行 SSL 握手并获取证书信息。检测频率取决于监控任务的执行计划（如每5分钟、每小时等）
- **每日定时告警检查**：每天 9:00 AM（北京时间）自动检查所有已启用告警的域名证书，评估是否需要发送告警通知
- **检测内容**：域名匹配校验（支持通配符 `*.example.com`）、证书有效期检查、剩余天数计算。证书过期或域名不匹配时触发告警

**证书状态说明**：
- **有效 (VALID)**：证书有效且域名匹配
- **已过期 (EXPIRED)**：证书已过期
- **证书错误 (MISMATCH)**：证书与域名不匹配（通过SAN校验，支持通配符）

---

## API Schema 变更检测

### 概述

API Schema 用于定义 API 响应的 JSON 结构契约。系统在监控项执行时自动用 Schema 校验实际响应，检测结构变更并触发告警。Schema 独立管理，可被多个监控项引用。

### 操作流程

```
1. 进入「API Schema」菜单 → 创建 Schema（填写名称、描述，使用可视化编辑器构建或粘贴 JSON Schema 内容）
2. 进入「监控项管理」→ 编辑监控项 → 开启「API Schema 告警」
3. 选择期望 Schema（从列表选择自动填充，或使用可视化编辑器构建）
4. 选择需要告警的变更类型（新增/删除/修改/破坏性）
5. 选择告警通道
6. 保存。监控执行时自动校验并检测变更
```

### 历史版本对比

每次修改 Schema 内容时，系统自动记录历史版本。操作步骤：

```
1. 在 Schema 列表点击「历史」按钮
2. 弹窗显示所有历史版本（类型/描述/时间）
3. 选择一条历史记录，点击「对比」
4. 左右对照显示旧版本和新版本，差异字段高亮标记
5. 底部变更摘要表格列出所有差异详情
```

**高亮规则**：
- 绿色背景：新增字段（ADDED）
- 红色背景 + 删除线：删除字段（REMOVED）
- 黄色背景：修改字段（MODIFIED）

### 监控项集成配置

在监控项编辑弹窗的「基本设置」标签页中，找到「API Schema 告警」区域：

| 配置项 | 说明 |
|--------|------|
| 启用开关 | 开启后才进行 Schema 校验 |
| 期望 Schema | 使用可视化编辑器构建 JSON Schema（支持拖拽排序、快速模板、折叠展开），或从 Schema 列表选择后自动填充 |
| 变更类型 | 多选：新增(ADDED)、删除(REMOVED)、修改(MODIFIED)、破坏性(BREAKING) |
| 告警通道 | 选择接收告警通知的通道（复用告警渠道配置） |

### 执行时校验逻辑

```
监控项执行完成 → 响应内容是否为合法 JSON？
  ├─ 否 → 跳过（非 JSON 响应不做 Schema 校验）
  └─ 是 → 用 expectedSchemaJson 做 detectChanges()
           → 递归对比 Schema 与实际响应的每个字段
           → 过滤匹配用户选择的变更类型
           → 有匹配变更 → AlertService.sendAlertByIds() 发送告警
           → 无匹配变更 → 正常结束
```

### JSON Schema 案例

#### 案例一：基础用户信息接口

适用：用户详情、个人信息查询等接口

```json
{
  "type": "object",
  "required": ["code", "data"],
  "properties": {
    "code": {
      "type": "integer"
    },
    "message": {
      "type": "string"
    },
    "data": {
      "type": "object",
      "required": ["id", "username"],
      "properties": {
        "id": {
          "type": "integer"
        },
        "username": {
          "type": "string"
        },
        "email": {
          "type": ["string", "null"]
        },
        "avatar": {
          "type": "string"
        },
        "createdAt": {
          "type": "string"
        }
      }
    }
  }
}
```

**要点**：
- `required: ["code", "data"]` — 根节点必须包含 code 和 data
- `"type": ["string", "null"]` — email 字段可为 null
- 嵌套 `properties` — data 对象内再定义子字段

#### 案例二：分页列表接口

适用：分页查询、列表搜索等接口

```json
{
  "type": "object",
  "required": ["code", "data"],
  "properties": {
    "code": { "type": "integer" },
    "data": {
      "type": "object",
      "required": ["records", "total", "page", "size"],
      "properties": {
        "records": {
          "type": "array",
          "items": {
            "type": "object",
            "required": ["id", "name", "status"],
            "properties": {
              "id": { "type": "integer" },
              "name": { "type": "string" },
              "status": {
                "type": "string",
                "enum": ["active", "inactive", "pending"]
              },
              "createdAt": { "type": "string" }
            }
          }
        },
        "total": { "type": "integer" },
        "page": { "type": "integer" },
        "size": { "type": "integer" }
      }
    }
  }
}
```

**要点**：
- `"type": "array"` + `"items"` — 定义数组及元素结构
- `"enum"` — 限定字段取值范围（status 只能是三个值之一）

#### 案例三：嵌套复杂结构接口

适用：订单详情、商品信息等多层嵌套接口

```json
{
  "type": "object",
  "required": ["code", "data"],
  "properties": {
    "code": { "type": "integer" },
    "data": {
      "type": "object",
      "required": ["orderId", "items", "payment"],
      "properties": {
        "orderId": { "type": "string" },
        "items": {
          "type": "array",
          "items": {
            "type": "object",
            "required": ["sku", "quantity", "price"],
            "properties": {
              "sku": { "type": "string" },
              "productName": { "type": "string" },
              "quantity": { "type": "integer", "minimum": 1 },
              "price": { "type": "number" },
              "tags": {
                "type": "array",
                "items": { "type": "string" }
              }
            }
          }
        },
        "payment": {
          "type": "object",
          "required": ["method", "amount"],
          "properties": {
            "method": {
              "type": "string",
              "enum": ["credit_card", "alipay", "wechat"]
            },
            "amount": { "type": "number" },
            "currency": { "type": "string", "default": "CNY" }
          }
        }
      }
    }
  }
}
```

**要点**：
- `"minimum": 1` — 数值最小值约束
- `"default": "CNY"` — 默认值声明
- 三层嵌套：data → items[] → properties

#### 案例四：RESTful API 通用响应

适用：标准化 API 响应格式，兼容成功和错误场景

```json
{
  "type": "object",
  "required": ["code", "success", "message"],
  "properties": {
    "code": {
      "type": "integer",
      "description": "业务状态码，0=成功，非0=失败"
    },
    "success": {
      "type": "boolean"
    },
    "message": {
      "type": "string"
    },
    "data": {},
    "errors": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "field": { "type": "string" },
          "message": { "type": "string" },
          "code": { "type": "string" }
        }
      }
    },
    "timestamp": { "type": "string" },
    "path": { "type": "string" }
  }
}
```

**要点**：
- `"data": {}` — 空对象表示 data 可以是任意结构（不约束）
- `"description"` — 字段描述（仅供文档阅读，不影响校验）
- errors 数组用于批量校验错误返回

### 编写技巧

- **required 字段**：建议只标记业务必须存在的字段，避免过度严格导致频繁告警
- **type 数组**：用 `"type": ["string", "null"]` 表示可空字段
- **嵌套检测**：系统会递归检测嵌套对象和数组元素的结构变更
- **BREAKING 判定**：当必填字段（required）被删除时自动判定为破坏性变更
- **安全删除**：不需要的字段不要从 Schema 中删除，保留旧定义避免误触 BREAKING 告警
- **建议做法**：Schema 应反映 API 的稳定契约，而非临时的数据格式
- **版本对比**：修改 Schema 前，先通过「历史」按钮查看上一版本，确认变更合理性

---

## 前端路由与页面

### 侧边栏菜单结构

```
仪表盘（含备忘录按钮）
监控项管理
监控任务
域名证书
监控告警（子菜单）
  ├── 告警模板
  ├── 告警渠道
  ├── 告警记录
  └── 告警静默
API Schema
巡检管理
执行日志
系统管理（子菜单）
  ├── 变量管理
  ├── 代理设置（ADMIN）
  ├── 用户管理（ADMIN）
  ├── 安全设置（ADMIN）
  ├── 数据备份
  └── 审计日志
使用手册
```

### 路由表

| 路由 | 页面 | 权限 | 功能 |
|------|------|------|------|
| `/login` | Login.vue | 公开 | 登录表单 |
| `/status` | StatusPage.vue | 可配置 | 公开状态页（默认匿名不可访问，需在安全设置中开启） |
| `/dashboard` | Dashboard.vue | 登录 | 统计卡片（监控项/监控任务启用禁用、响应超时、SSL异常、Uptime、Schema检测）+ 可点击跳转 + 时间窗口持久化 + ECharts 图表 + 最近日志 + 备忘录（弹窗编辑器，Ctrl+S 保存） |
| `/monitor` | MonitorList.vue | 登录 | 监控项 CRUD + Postman风格请求配置 + API签名配置 + 预请求脚本 + 变量赋值 + 响应时间告警 + 正则验证 + 响应体大小限制 + 手动测试 + 监控项复制 |
| `/group` | GroupList.vue | 登录 | 任务 CRUD + 关联监控项数量 + 监控项详情弹窗（名称/排序/失败继续）+ Cron调度配置 + 并发控制 + 执行 |
| `/variable` | VariableList.vue | 登录 | 全局变量 CRUD + 任务变量管理 + 内置变量查看 |
| `/domain` | DomainList.vue | 登录 | 域名证书 + 证书状态 + IP 历史 + SSL 详情 + SSL 告警配置（含执行时告警） |
| `/alert-template` | AlertTemplateList.vue | 登录 | 告警模板管理（按触发类型配置 + 预定义变量 + 冷却时间 + 唯一启用规则） |
| `/alert-channel` | AlertChannelList.vue | 登录 | 告警渠道管理（邮件/Webhook/钉钉/企业微信/飞书）+ 渠道测试 |
| `/alert-log` | AlertLogList.vue | 登录 | 告警记录查看 + 筛选（含发送状态） + 详情 |
| `/alert-silence` | AlertSilenceList.vue | 登录 | 告警静默规则管理（一次性/周期性 + 多时间段 + 适用范围） |
| `/api-schema` | ApiSchemaList.vue | 登录 | API Schema 管理（独立 CRUD + 历史版本对比 + 差异高亮） |
| `/inspection` | InspectionList.vue | 登录 | 巡检管理（巡检配置 + 立即执行 + 历史 + 详情弹窗） |
| `/log` | LogList.vue | 登录 | 执行日志查看 + 变量追踪 + 筛选（状态/Schema/时间范围）+ 清理 |
| `/audit-log` | AuditLogList.vue | 登录 | 审计日志查看 + 按用户/操作类型筛选 |
| `/backup` | BackupList.vue | 登录 | 数据备份管理（创建/恢复/删除，下载仅ADMIN） |
| `/proxy` | ProxyList.vue | ADMIN | 代理设置管理（CRUD + 启用/停用 + 连通性测试） |
| `/user` | UserList.vue | ADMIN | 用户管理 |
| `/manual` | Manual.vue | 登录 | 使用手册（折叠章节：快速入门、架构、变量、监控、任务、告警、域名、权限、日志） |

### 布局

`MainLayout.vue` 提供统一布局：
- **侧边栏**：主题色（随换肤变化），可折叠，图标 + 文字菜单；Logo 下方显示版本号（如 `1.3.1 / 1.3.1`）
- **顶栏**：折叠按钮 + 面包屑 + 换肤入口（用户信息左侧）+ 用户下拉菜单（登出）
- **换肤**：画笔图标弹出 6 套主题（默认/深色/翠绿/极光紫/中国红/活力橙），选择持久化到 `localStorage`
- **告警菜单**：使用 `el-sub-menu` 分组，包含"告警模板"、"告警渠道"、"告警记录"、"告警静默"
- **独立菜单项**：API Schema（Schema 管理 + 历史版本对比 + JSON Schema 案例）、巡检管理（独立一级菜单）
- **系统管理菜单**：使用 `el-sub-menu` 分组，包含"变量管理"、"代理设置"（ADMIN）、"用户管理"（ADMIN）、"安全设置"（ADMIN）、"数据备份"、"审计日志"

### 工具函数

`utils/format.js` 提供时间格式化：
- `formatTime(date)` → `YYYY-MM-DD HH:mm:ss`（北京时间）
- `formatTimeShort(date)` → `MM-DD HH:mm`
- `formatTimeFull(date)` → `YYYY-MM-DD HH:mm:ss`

---

## 预请求脚本 API 参考

### request 对象（可读写）

脚本启动时自动注入，修改后自动生效：

| 属性 | 类型 | 说明 |
|------|------|------|
| `request.method` | String | HTTP方法（GET/POST/PUT/DELETE），可修改 |
| `request.url` | String | 完整请求URL，可修改 |
| `request.body` | String | 请求体字符串，需JSON.parse()解析，可修改 |
| `request.headers` | Object | 请求头键值对对象，可读写 |
| `request.params` | Object | URL查询参数键值对对象，可读写 |

### 快捷函数

| 函数 | 说明 |
|------|------|
| `setHeader(key, value)` | 设置请求头 |
| `setParam(key, value)` | 设置URL查询参数 |
| `setBody(str)` | 替换整个请求体 |
| `setBodyField(key, value)` | 解析JSON Body并设置字段（自动序列化） |

### crypto 加密对象

| 方法 | 说明 |
|------|------|
| `crypto.md5(str)` | MD5哈希，返回十六进制字符串 |
| `crypto.sha1(str)` | SHA-1哈希，返回十六进制字符串 |
| `crypto.sha256(str)` | SHA-256哈希，返回十六进制字符串 |
| `crypto.hmacSha256(str, key)` | HMAC-SHA256，返回十六进制字符串 |
| `crypto.hmacSha256Base64(str, key)` | HMAC-SHA256，返回Base64字符串 |
| `crypto.hmacMd5(str, key)` | HMAC-MD5，返回十六进制字符串 |
| `crypto.base64(str)` | Base64编码 |
| `crypto.base64Decode(str)` | Base64解码 |
| `crypto.rsaSha256Sign(str, privateKeyPem)` | RSA-SHA256签名，返回Base64字符串 |
| `crypto.rsaSha256SignHex(str, privateKeyPem)` | RSA-SHA256签名，返回十六进制字符串 |

### util 工具对象

| 方法 | 说明 |
|------|------|
| `util.timestamp()` | 当前毫秒时间戳 |
| `util.timestampS()` | 当前秒时间戳 |
| `util.datetime()` | 当前时间 yyyy-MM-dd HH:mm:ss（北京时间） |
| `util.date()` | 当前日期 yyyy-MM-dd |
| `util.time()` | 当前时间 HH:mm:ss |
| `util.nonce(len?)` | 随机字符串，默认32位，可指定长度 |
| `util.uuid()` | 完整UUID v4 |
| `util.uuidShort()` | 短UUID（前8位） |
| `util.random(max?)` | 随机整数 [0, max)，默认1000000 |

### 支持的 JS 语法

- 变量声明（let/const）、箭头函数、模板字符串
- 条件判断（if/else）、循环（for/while）、解构赋值
- `JSON.parse()` / `JSON.stringify()` 解析和序列化JSON
- 数组方法（map/filter/reduce/forEach）
- `async/await`、`Promise`

### 代码示例

**示例1：在Header中添加时间戳和HMAC签名**
```js
const timestamp = util.timestamp();
const nonce = util.nonce();
const body = request.body || '';
const message = timestamp + '\n' + nonce + '\n' + body + '\n';
const sign = crypto.hmacSha256(message, 'my-secret-key');
request.headers['X-Timestamp'] = timestamp;
request.headers['X-Nonce'] = nonce;
request.headers['X-Sign'] = sign;
```

**示例2：动态修改请求Body字段**
```js
// 解析JSON Body并添加字段
const body = JSON.parse(request.body || '{}');
body.timestamp = util.timestamp();
body.nonce = util.nonce();
request.body = JSON.stringify(body);
```

**示例3：使用setBodyField快捷函数**
```js
// 等效于上面的写法，更简洁
setBodyField('timestamp', util.timestamp());
setBodyField('nonce', util.nonce());
```

**示例4：URL参数签名（AWS风格）**
```js
const timestamp = util.timestampS();
const path = new URL(request.url).pathname;
const bodyHash = crypto.sha256(request.body || '');
const canonical = request.method + '\n' + path + '\n' + timestamp + '\n' + bodyHash;
const sign = crypto.hmacSha256Base64(canonical, 'aws-secret-key');
request.params['X-Timestamp'] = timestamp;
request.params['X-Sign'] = sign;
```

**示例5：Base64编码Body**
```js
// 将请求体进行Base64编码后放入Header
request.headers['X-Body-Hash'] = crypto.base64(request.body || '');
```

**示例6：条件判断 + JSON解析**
```js
// 根据请求方法动态处理
if (request.method === 'POST') {
  const body = JSON.parse(request.body || '{}');
  body.sign = crypto.md5(body.appId + body.timestamp + 'key');
  request.body = JSON.stringify(body);
} else {
  request.params['t'] = util.timestamp();
}
```
