<template>
  <div class="manual-page">
    <div class="page-header">
      <h2>使用手册</h2>
    </div>

    <el-card>
      <el-collapse v-model="activeSections">
        <!-- 快速入门 -->
        <el-collapse-item name="quickstart">
          <template #title>
            <el-icon style="margin-right: 8px; color: #409eff"><Promotion /></el-icon>
            <span style="font-weight: 600">快速入门</span>
          </template>
          <div class="section-content">
            <h4>1. 首次登录</h4>
            <p>首次启动会自动创建管理员账号 <code>admin</code>，初始密码<b>随机生成并仅在启动日志中输出一次</b>（也可用环境变量 <code>ADMIN_INIT_PASSWORD</code> 预设）。首次登录会被<b>强制要求修改密码</b>（≥8位且同时包含大写字母、小写字母和数字）。</p>
            <h4>2. 创建监控项</h4>
            <p>进入"监控项管理"，点击<b>"添加HTTP监控"</b>或<b>"添加其他监控"</b>。HTTP 监控填写目标 URL、请求方法、期望状态码等；其他协议（Ping/TCP/SSH/Telnet/FTP/VNC/MySQL/PostgreSQL/Redis/Memcached/MongoDB/ZooKeeper/AMQP/MQTT）填写主机地址和端口。点击"测试"按钮验证配置是否正确。</p>
            <el-alert type="warning" :closable="false" show-icon style="margin: 8px 0 16px">
              <template #title>
                单独创建的监控项只能手动点击"测试"执行，不会自动定时运行。如需定时自动执行，请将监控项添加到"监控任务"中并配置执行计划。
              </template>
            </el-alert>
            <h4>3. 配置告警渠道</h4>
            <p>进入"监控告警 → 告警渠道"，添加邮件或Webhook通知方式。配置完成后点击"测试"验证连通性。</p>
            <h4>4. 创建监控任务</h4>
            <p>进入"监控任务"，创建任务并添加监控项。设置排序值控制执行顺序，配置执行计划（固定间隔/每天/每周）后任务将按计划自动执行。</p>
            <h4>5. 查看结果</h4>
            <p>在"仪表盘"查看整体状态，在"执行日志"查看每次执行的详细信息。</p>
            <ul>
              <li><b>自动刷新</b>：仪表盘标题旁有刷新图标 <el-icon style="vertical-align: middle"><Refresh /></el-icon>，点击可设置自动刷新间隔（关闭 / 10秒 / 30秒 / 3分钟），设置保存在浏览器本地存储</li>
            </ul>
            <h4>6. 周期提醒（可选）</h4>
            <p>在"周期提醒"中管理服务器续费、SSL 证书续费、定期巡检等周期性任务，到期通过告警渠道通知。</p>
          </div>
        </el-collapse-item>

        <!-- 系统架构 -->
        <el-collapse-item name="architecture">
          <template #title>
            <el-icon style="margin-right: 8px; color: #67c23a"><Grid /></el-icon>
            <span style="font-weight: 600">系统架构</span>
          </template>
          <div class="section-content">
            <h4>技术栈</h4>
            <p><b>后端</b>：Java 17 + Spring Boot 3.2.5 + MyBatis-Plus + SQLite（轻量级，无需额外安装数据库）</p>
            <p><b>前端</b>：Vue 3 + Element Plus + ECharts + Vite</p>
            <h4>工作原理</h4>
            <p>系统通过<b>策略模式的多协议执行器</b>向目标发送请求/探测：HTTP 使用 OkHttp，其余协议（Ping/TCP/SSH/Telnet/FTP/VNC/MySQL/PostgreSQL/Redis/Memcached/MongoDB/ZooKeeper/AMQP/MQTT）在连接前校验地址并按协议握手。根据配置的验证规则（状态码、JSONPath、文本匹配）判断监控结果。支持变量提取和传递，实现监控间数据联动等复杂场景。</p>
            <p><b>定时任务</b>：内置调度器每分钟检查需要执行的监控任务，按执行计划触发。异步执行不阻塞HTTP请求。</p>
            <p><b>告警流程</b>：监控触发告警条件 → 按触发类型查找告警模板 → 渲染消息 → 通过配置的渠道发送 → 记录告警日志。</p>
            <p><b>安全机制</b>：JWT 认证 + 角色实时校验（降权即时生效）、BCrypt 密码、连接期 SSRF 校验（可在安全设置切换模式）、GraalVM 脚本沙箱、审计日志脱敏、登录失败锁定、IP 黑白名单。</p>
          </div>
        </el-collapse-item>

        <!-- 变量系统 -->
        <el-collapse-item name="variables">
          <template #title>
            <el-icon style="margin-right: 8px; color: #e6a23c"><Setting /></el-icon>
            <span style="font-weight: 600">变量系统</span>
          </template>
          <div class="section-content">
            <h4>变量语法</h4>
            <p>使用 <code v-text="'{{scope.name}}'"></code> 格式引用变量。</p>
            <el-table :data="variableScopes" stripe size="small" style="margin: 12px 0">
              <el-table-column prop="scope" label="作用域" width="100" />
              <el-table-column prop="example" label="示例" width="180" />
              <el-table-column prop="desc" label="说明" />
            </el-table>

            <h4>内置变量</h4>
            <p>内置变量由系统自动生成，每次请求时重新计算，无需创建：</p>
            <el-table :data="builtinVarsTable" stripe size="small" style="margin: 12px 0">
              <el-table-column prop="name" label="变量名" width="200">
                <template #default="{ row }">
                  <code v-text="'{{env.' + row.name + '}}'" />
                </template>
              </el-table-column>
              <el-table-column prop="desc" label="说明" />
              <el-table-column prop="example" label="示例" width="200" />
            </el-table>

            <h4>变量赋值</h4>
            <p>在监控项中配置变量提取规则，执行后自动将响应中的值写入变量。支持三种来源：</p>
            <ul>
              <li><b>响应体</b>：用JSONPath提取（如 <code>$.data.token</code>），留空则写入整个响应体</li>
              <li><b>响应头</b>：按Header名提取（如 <code>x-token</code>），留空则写入全部响应头JSON</li>
              <li><b>Cookie</b>：按Cookie名提取（如 <code>session_id</code>），留空则写入全部Cookie JSON</li>
            </ul>

            <h4>使用场景</h4>
            <ul>
              <li><b>监控间数据联动</b>：第一个监控提取token → 存入 <code v-text="'{{group.token}}'"></code> → 后续监控在请求头中引用</li>
              <li><b>环境切换</b>：全局变量 <code v-text="'{{global.baseUrl}}'"></code> 存储API地址，所有监控引用同一变量</li>
            </ul>
          </div>
        </el-collapse-item>

        <!-- 监控项配置 -->
        <el-collapse-item name="monitor">
          <template #title>
            <el-icon style="margin-right: 8px; color: #409eff"><Connection /></el-icon>
            <span style="font-weight: 600">监控项配置</span>
          </template>
          <div class="section-content">
            <h4>基本配置</h4>
            <ul>
              <li><b>搜索筛选</b>：支持按ID精确查询、关键词模糊搜索（名称/URL）、启用状态筛选</li>
              <li><b>URL</b>：支持变量，如 <code v-text="'{{global.baseUrl}}/api/test'"></code>。标签旁有 <el-icon style="vertical-align: middle"><QuestionFilled /></el-icon> 图标，悬停可查看变量语法和可用值</li>
              <li><b>请求方法</b>：GET / POST / PUT / DELETE</li>
              <li><b>请求头</b>：JSON 格式，支持变量。标签旁有 <el-icon style="vertical-align: middle"><QuestionFilled /></el-icon> 图标，悬停可查看示例</li>
              <li><b>期望状态码</b>：HTTP响应状态码，如 200</li>
              <li><b>超时</b>：请求超时时间（秒），默认30秒</li>
            </ul>

            <h4>响应验证（可选）</h4>
            <ul>
              <li><b>JSONPath</b>：从响应JSON中提取值，与"JSONPath期望值"比较。标签旁有 <el-icon style="vertical-align: middle"><QuestionFilled /></el-icon> 图标，悬停可查看JSONPath语法示例</li>
              <li><b>JSONPath期望值</b>：支持变量进行动态比对。标签旁有 <el-icon style="vertical-align: middle"><QuestionFilled /></el-icon> 图标</li>
              <li><b>期望文本</b>：响应体包含此文本则验证通过，支持变量。标签旁有 <el-icon style="vertical-align: middle"><QuestionFilled /></el-icon> 图标</li>
              <li><b>正则表达式</b>：整个响应体匹配此正则则验证通过（如 <code>^\d{4}-\d{2}-\d{2}$</code>）。标签旁有 <el-icon style="vertical-align: middle"><QuestionFilled /></el-icon> 图标，悬停可查看判断逻辑和示例</li>
              <li><b>响应体大小限制</b>：响应体超过指定字节则标记为失败（0=不限制）</li>
            </ul>

            <h4>POST/PUT 请求体</h4>
            <p>支持 none / JSON / Form Data / XML / Raw Text 五种类型。JSON和XML模式支持变量。</p>

            <h4>响应时间告警</h4>
            <p>开启后配置阈值（毫秒）和连续触发次数。当响应时间超过阈值且连续达到指定次数时，自动发送告警。</p>

            <h4>API签名配置</h4>
            <p>为请求自动计算签名并注入到Header、Params或Body中。支持以下签名类型：</p>
            <ul>
              <li><b>MD5签名</b>：排序参数 → key拼接 → MD5（微信支付V2等）</li>
              <li><b>HMAC-SHA256</b>：规范字符串 → HMAC-SHA256（AWS/Stripe等）</li>
              <li><b>RSA-SHA256</b>：请求体 → RSA-SHA256签名（微信支付V3等）</li>
              <li><b>自定义JS脚本</b>：完全自定义签名逻辑</li>
            </ul>
            <p>签名注入位置：Header / URL参数 / 请求体 / 多位置。</p>
            <p><b>签名转大写</b>：开启后最终签名转为全大写，适用于要求大写签名的接口（如部分 Hex 签名场景）；默认关闭即为原始大小写（通常为小写）。</p>

            <h4>预请求脚本</h4>
            <p>在HTTP请求发送前执行自定义JavaScript代码（基于GraalJS，ECMAScript 2023）。脚本可修改请求的URL、Headers、Params、Body，常用于自定义签名、动态鉴权等场景。</p>
            <el-alert type="info" :closable="false" show-icon style="margin: 8px 0 16px">
              <template #title>
                变量替换在脚本执行之前完成，脚本中直接使用原始值即可，无需关心变量语法。
              </template>
            </el-alert>

            <h4>request 对象（可读写）</h4>
            <p>脚本启动时自动注入，修改后自动生效：</p>
            <el-table :data="requestApi" stripe size="small" style="margin: 12px 0">
              <el-table-column prop="prop" label="属性" width="160" />
              <el-table-column prop="type" label="类型" width="100" />
              <el-table-column prop="desc" label="说明" />
            </el-table>

            <h4>快捷函数</h4>
            <el-table :data="helperFunctions" stripe size="small" style="margin: 12px 0">
              <el-table-column prop="func" label="函数" width="220" />
              <el-table-column prop="desc" label="说明" />
            </el-table>

            <h4>crypto 加密对象</h4>
            <el-table :data="cryptoApi" stripe size="small" style="margin: 12px 0">
              <el-table-column prop="method" label="方法" width="280" />
              <el-table-column prop="desc" label="说明" />
            </el-table>

            <h4>util 工具对象</h4>
            <el-table :data="utilApi" stripe size="small" style="margin: 12px 0">
              <el-table-column prop="method" label="方法" width="260" />
              <el-table-column prop="desc" label="说明" />
            </el-table>

            <h4>支持的 JS 语法</h4>
            <ul>
              <li>变量声明（let/const）、箭头函数、模板字符串</li>
              <li>条件判断（if/else）、循环（for/while）、解构赋值</li>
              <li><code>JSON.parse()</code> / <code>JSON.stringify()</code> 解析和序列化JSON</li>
              <li>数组方法（map/filter/reduce/forEach）</li>
              <li><code>async/await</code>、<code>Promise</code></li>
            </ul>

            <h4>代码示例</h4>

            <p><b>示例1：在Header中添加时间戳和HMAC签名</b></p>
            <pre class="code-block">const timestamp = util.timestamp();
const nonce = util.nonce();
const body = request.body || '';
const message = timestamp + '\n' + nonce + '\n' + body + '\n';
const sign = crypto.hmacSha256(message, 'my-secret-key');
request.headers['X-Timestamp'] = timestamp;
request.headers['X-Nonce'] = nonce;
request.headers['X-Sign'] = sign;</pre>

            <p><b>示例2：动态修改请求Body字段</b></p>
            <pre class="code-block">// 解析JSON Body并添加字段
const body = JSON.parse(request.body || '{}');
body.timestamp = util.timestamp();
body.nonce = util.nonce();
request.body = JSON.stringify(body);</pre>

            <p><b>示例3：使用setBodyField快捷函数</b></p>
            <pre class="code-block">// 等效于上面的写法，更简洁
setBodyField('timestamp', util.timestamp());
setBodyField('nonce', util.nonce());</pre>

            <p><b>示例4：URL参数签名（AWS风格）</b></p>
            <pre class="code-block">const timestamp = util.timestampS();
const path = new URL(request.url).pathname;
const bodyHash = crypto.sha256(request.body || '');
const canonical = request.method + '\n' + path + '\n' + timestamp + '\n' + bodyHash;
const sign = crypto.hmacSha256Base64(canonical, 'aws-secret-key');
request.params['X-Timestamp'] = timestamp;
request.params['X-Sign'] = sign;</pre>

            <p><b>示例5：Base64编码Body</b></p>
            <pre class="code-block">// 将请求体进行Base64编码后放入Header
request.headers['X-Body-Hash'] = crypto.base64(request.body || '');</pre>

            <p><b>示例6：条件判断 + JSON解析</b></p>
            <pre class="code-block">// 根据请求方法动态处理
if (request.method === 'POST') {
  const body = JSON.parse(request.body || '{}');
  body.sign = crypto.md5(body.appId + body.timestamp + 'key');
  request.body = JSON.stringify(body);
} else {
  request.params['t'] = util.timestamp();
}</pre>

            <h4>监控项复制</h4>
            <p>点击操作列的"复制"按钮，一键复制监控项配置。新监控项名称自动添加"(副本)"后缀。</p>
          </div>
        </el-collapse-item>

        <!-- 多协议监控 -->
        <el-collapse-item name="multi-protocol">
          <template #title>
            <el-icon style="margin-right: 8px; color: #e6a23c"><Monitor /></el-icon>
            <span style="font-weight: 600">多协议监控</span>
          </template>
          <div class="section-content">
            <p>系统支持 <b>15 种监控协议</b>，通过"添加HTTP监控"和"添加其他监控"按钮分别创建。所有非 HTTP 协议均为匿名/半匿名探测——即使目标服务需要认证，能收到协议响应即证明服务存活。</p>

            <h4>支持的协议</h4>
            <p><b>HTTP</b>：完整的 HTTP/HTTPS 请求监控，支持 GET/POST/PUT/DELETE、请求体、响应验证、API签名、前置脚本、变量提取。</p>

            <p><b>网络探测</b></p>
            <ul>
              <li><b>Ping (ICMP)</b>：检测主机 ICMP 可达性（<code>InetAddress.isReachable</code>），仅需主机地址</li>
              <li><b>TCP</b>：检测目标端口的 TCP 连通性，需主机地址 + 端口</li>
            </ul>

            <p><b>远程登录 / 文件服务</b></p>
            <ul>
              <li><b>SSH</b>（22）：读取 SSH Banner，如 <code>SSH-2.0-OpenSSH_8.9</code></li>
              <li><b>Telnet</b>（23）：读取 Banner；服务端不主动发 Banner 时自动发送换行再读</li>
              <li><b>FTP</b>（21）：校验服务端欢迎信息 <code>220 ...</code></li>
              <li><b>VNC</b>（5900）：校验服务端版本串 <code>RFB 003.008</code></li>
            </ul>

            <p><b>数据库 / 缓存</b></p>
            <ul>
              <li><b>MySQL</b>（3306）：解析 Initial Handshake Packet，获取协议版本和服务器版本</li>
              <li><b>PostgreSQL</b>（5432）：发送 SSLRequest，响应 <code>S</code>/<code>N</code> 均表示服务存活</li>
              <li><b>Redis</b>（6379）：RESP <code>PING</code> → <code>+PONG</code></li>
              <li><b>Memcached</b>（11211）：<code>version</code> → <code>VERSION x.y.z</code></li>
              <li><b>MongoDB</b>（27017）：OP_MSG <code>hello</code> 握手，提取 maxWireVersion</li>
              <li><b>ZooKeeper</b>（2181）：<code>ruok</code> → <code>imok</code>（未启用4lw白名单时降级为 TCP 存活）</li>
            </ul>

            <p><b>消息队列</b></p>
            <ul>
              <li><b>AMQP / RabbitMQ</b>（5672）：发送 AMQP 0-9-1 协议头，校验协议头回显或 connection.start 帧</li>
              <li><b>MQTT</b>（1883）：发送无认证 CONNECT，解析 CONNACK 返回码</li>
            </ul>

            <h4>非 HTTP 监控配置</h4>
            <ul>
              <li><b>主机地址</b>：IP 地址或域名</li>
              <li><b>端口</b>：各协议有默认端口，可自定义；Ping 无需端口</li>
              <li><b>超时</b>：检测超时时间（秒），默认 30 秒</li>
              <li><b>启用</b>：控制监控项是否激活</li>
              <li><b>公开状态页展示</b>：是否在公开状态页显示</li>
              <li><b>监控项告警</b>：连续失败次数阈值 + 告警通道选择</li>
            </ul>

            <h4>执行结果</h4>
            <p>所有协议的执行结果统一存储在"执行日志"中，包含：</p>
            <ul>
              <li><b>状态</b>：SUCCESS / FAIL / ERROR</li>
              <li><b>响应时间</b>：从发起到收到响应的耗时（毫秒）</li>
              <li><b>状态码</b>：HTTP 为 HTTP 状态码，其他协议为握手特征码</li>
              <li><b>IP 地址</b>：解析到的目标 IP</li>
              <li><b>响应体</b>：HTTP 为完整响应，其他协议为 Banner 或握手信息</li>
            </ul>

            <el-alert type="info" :closable="false" show-icon style="margin: 8px 0 16px">
              <template #title>
                <b>注意</b>：Ping 在 Windows 系统上可能受限于 <code>InetAddress.isReachable()</code> 的实现，建议在 Linux 部署以获得最佳 ICMP 支持。非 HTTP 监控同样支持响应时间告警、连续失败告警，并可加入监控任务和巡检计划。
              </template>
            </el-alert>
          </div>
        </el-collapse-item>

        <!-- 监控任务 -->
        <el-collapse-item name="group">
          <template #title>
            <el-icon style="margin-right: 8px; color: #67c23a"><Folder /></el-icon>
            <span style="font-weight: 600">监控任务</span>
          </template>
          <div class="section-content">
            <h4>用途</h4>
            <p>将多个监控项组合成有序执行链。适用于接口依赖链、数据联动等场景。</p>

            <h4>执行计划</h4>
            <ul>
              <li><b>固定间隔</b>：每N分钟执行一次</li>
              <li><b>每天</b>：指定时间点执行</li>
              <li><b>每周</b>：指定星期+时间点执行</li>
            </ul>

            <h4>监控项配置</h4>
            <ul>
              <li><b>排序</b>：数值越小越先执行</li>
              <li><b>失败继续</b>：勾选后某项失败仍继续执行后续项</li>
              <li><b>禁用状态</b>：任务执行时自动跳过禁用的监控项</li>
            </ul>

            <h4>并发控制</h4>
            <p>同一任务同一时刻只允许一个执行实例运行。如果任务正在执行中，再次触发会被自动跳过，前端显示"执行中"状态。</p>

            <h4>变量传递</h4>
            <p>任务开始前自动清空所有任务变量（<code v-text="'{{group.xxx}}'"></code>）。每个监控项执行后提取的变量值可被后续项使用。</p>
          </div>
        </el-collapse-item>

        <!-- 告警系统 -->
        <el-collapse-item name="alert">
          <template #title>
            <el-icon style="margin-right: 8px; color: #f56c6c"><Bell /></el-icon>
            <span style="font-weight: 600">告警系统</span>
          </template>
          <div class="section-content">
            <h4>告警处理流程（优先级从高到低）</h4>
            <p>每次告警触发时，系统按以下顺序依次判断：</p>
            <el-table :data="alertPriority" stripe size="small" style="margin: 12px 0">
              <el-table-column prop="priority" label="优先级" width="80" />
              <el-table-column prop="name" label="判断" width="120" />
              <el-table-column prop="desc" label="说明" />
              <el-table-column prop="action" label="结果" width="160" />
            </el-table>
            <el-alert type="info" :closable="false" show-icon style="margin: 8px 0 16px">
              <template #title>
                关键点：恢复通知优先级最高，恢复时发送后重置限频计数。静默和冷却期间的告警不计入限频配额，只有实际发送的告警才累计限频次数。
              </template>
            </el-alert>

            <h4>告警模板</h4>
            <p>按触发类型配置消息模板，支持预定义变量（如 <code v-text="'{{monitorName}}'"></code>、<code v-text="'{{url}}'"></code>）。同一触发类型只能有一个启用的模板。</p>
            <ul>
              <li><b>系统预置模板</b>：初始创建的6个模板（监控项失败、响应超时、任务失败、SSL证书、通用、API Schema变更），标记为系统预置类型，<b>不可删除</b>，可查看、启用或停用</li>
              <li><b>自定义模板</b>：用户手动添加的模板，可编辑、启用、停用、删除</li>
            </ul>
            <el-table :data="triggerTypes" stripe size="small" style="margin: 12px 0">
              <el-table-column prop="type" label="触发类型" width="130" />
              <el-table-column prop="desc" label="触发条件" />
            </el-table>

            <h4>告警模板变量</h4>
            <p>自定义模板内容时可使用以下变量：</p>
            <el-table :data="templateVars" stripe size="small" style="margin: 12px 0">
              <el-table-column prop="variable" label="变量" width="160" />
              <el-table-column prop="desc" label="说明" />
            </el-table>

            <h4>告警限频</h4>
            <p>在告警模板中配置，用于限制相同告警的发送频率，避免告警风暴。</p>
            <ul>
              <li><b>最大告警次数</b>：相同监控项/任务/域名的告警累计达到上限后，后续告警抑制通知（日志仍记录为 <code>SUPPRESSED</code>）</li>
              <li><b>恢复通知</b>：开启后，当告警恢复（从失败变为成功）时自动发送恢复通知，优先级最高，发送后重置限频计数。恢复通知独立于限频功能，无需启用限频即可使用</li>
              <li><b>连续正常次数</b>：可配置连续成功 N 次后才发送恢复通知，避免偶然恢复导致误通知</li>
              <li><b>计数维度</b>：FAIL/RESPONSE_TIME 按 monitorId+groupId，GROUP_FAIL 按 groupId，SSL_CERT 按 domain</li>
              <li><b>恢复检测</b>：当监控项从失败变为成功，且之前累计告警次数 > 0 时，判定为恢复</li>
              <li><b>计数规则</b>：只有实际发送的告警才累计限频次数，静默和冷却期间的告警不计入</li>
            </ul>

            <h4>告警渠道</h4>
            <ul>
              <li><b>邮件</b>：通过SMTP发送告警邮件，支持SSL加密</li>
              <li><b>Webhook</b>：向指定URL发送HTTP POST/PUT请求</li>
              <li><b>钉钉</b>：自定义机器人Webhook，支持关键词安全设置</li>
              <li><b>企业微信</b>：自定义机器人Webhook，支持Markdown消息</li>
              <li><b>飞书</b>：自定义机器人Webhook，支持富文本消息</li>
            </ul>

            <h4>冷却时间</h4>
            <p>每个模板独立配置冷却时间（默认30分钟），同类型告警在此时间内只发送一次。冷却期间的告警不计入限频配额。</p>

            <h4>SSL证书告警</h4>
            <ul>
              <li><b>定时检查</b>：每天9:00自动检查所有启用了SSL告警的域名</li>
              <li><b>执行时告警</b>：开启后，监控执行中发现证书过期或证书错误立即触发</li>
              <li>检查条件：已过期、即将过期（≤N天）、证书与域名不匹配</li>
            </ul>

            <h4>告警静默（维护窗口）</h4>
            <p>在维护期间暂停告警通知，避免产生大量无意义告警。静默期间不消耗限频配额。</p>
            <ul>
              <li><b>一次性静默（每天时段）</b>：指定起止日期，期间每天的静默时段都生效</li>
              <li><b>周期性静默</b>：支持每天/每周/每月的指定时间段</li>
              <li><b>每天</b>：可配置多个时间段，如 02:00~06:00</li>
              <li><b>每周</b>：指定星期几 + 多个时间段</li>
              <li><b>每月</b>：指定几号 + 多个时间段</li>
              <li><b>适用范围</b>：全部 / 指定监控项 / 指定任务</li>
              <li>静默期间告警记录标记为 <code>SILENCED</code></li>
            </ul>

            <h4>告警记录状态说明</h4>
            <el-table :data="alertStatuses" stripe size="small" style="margin: 12px 0">
              <el-table-column prop="status" label="状态" width="120" />
              <el-table-column prop="desc" label="说明" />
            </el-table>
          </div>
        </el-collapse-item>

        <!-- 域名证书 -->
        <el-collapse-item name="domain">
          <template #title>
            <el-icon style="margin-right: 8px; color: #909399"><OfficeBuilding /></el-icon>
            <span style="font-weight: 600">域名证书</span>
          </template>
          <div class="section-content">
            <h4>自动汇集</h4>
            <p>无需手动添加。监控项执行测试时，系统自动记录目标域名、解析IP及SSL证书信息。</p>

            <h4>证书检测机制</h4>
            <p>系统通过两种方式检测证书状态：</p>
            <ul>
              <li><b>监控执行时检测</b>：每次 HTTPS 监控项执行时，自动进行 SSL 握手并获取证书信息。检测频率取决于监控任务的执行计划（如每5分钟、每小时等）</li>
              <li><b>每日定时告警检查</b>：每天 <b>9:00 AM</b>（北京时间）自动检查所有已启用告警的域名证书，评估是否需要发送告警通知</li>
            </ul>
            <el-alert type="info" :closable="false" show-icon style="margin: 8px 0 16px">
              <template #title>
                检测内容：域名匹配校验（支持通配符 *.example.com）、证书有效期检查、剩余天数计算。证书过期或域名不匹配时触发告警。
              </template>
            </el-alert>

            <h4>证书状态</h4>
            <ul>
              <li><b>有效 (VALID)</b>：证书有效且域名匹配</li>
              <li><b>已过期 (EXPIRED)</b>：证书已过期</li>
              <li><b>证书错误 (MISMATCH)</b>：证书与域名不匹配（通过SAN校验，支持通配符）</li>
            </ul>

            <h4>SSL告警配置</h4>
            <ul>
              <li><b>启用告警</b>：开启后配置告警渠道、提前告警天数（默认30天）</li>
              <li><b>执行时告警</b>：监控执行中发现异常立即触发，无需等待每日定时检查</li>
              <li><b>到期前1天提醒</b>：默认开启，证书明天过期时发送提醒</li>
            </ul>
          </div>
        </el-collapse-item>

        <!-- 用户与权限 -->
        <el-collapse-item name="user">
          <template #title>
            <el-icon style="margin-right: 8px; color: #909399"><User /></el-icon>
            <span style="font-weight: 600">用户与权限</span>
          </template>
          <div class="section-content">
            <h4>角色说明</h4>
            <el-table :data="roles" stripe size="small" style="margin: 12px 0">
              <el-table-column prop="role" label="角色" width="100" />
              <el-table-column prop="desc" label="权限" />
            </el-table>

            <h4>安全机制</h4>
            <ul>
              <li>JWT Token认证，24小时有效期</li>
              <li>禁用的用户无法登录（即使Token未过期也会被拒绝）</li>
              <li>删除的用户无法登录</li>
              <li>写操作需要ADMIN或OPERATOR角色</li>
              <li>默认管理员：admin，初始密码随机生成（首次登录强制修改）</li>
            </ul>
          </div>
        </el-collapse-item>

        <!-- 执行日志与告警记录 -->
        <el-collapse-item name="logs">
          <template #title>
            <el-icon style="margin-right: 8px; color: #409eff"><Document /></el-icon>
            <span style="font-weight: 600">执行日志与告警记录</span>
          </template>
          <div class="section-content">
            <h4>执行日志</h4>
            <ul>
              <li>记录每次执行的完整信息：URL、状态码、响应时间、域名、IP</li>
              <li><b>执行ID</b>：任务执行时所有子监控项共享同一执行ID，便于关联分析</li>
              <li><b>变量追踪</b>：记录变量替换前后的值和提取的变量值</li>
              <li><b>关联告警</b>：详情弹窗中展示该执行ID触发的所有告警记录</li>
              <li><b>ID跳转</b>：点击「监控ID」列跳转到监控项管理（按ID查询），点击「任务ID」列跳转到监控任务（按ID查询）</li>
            </ul>

            <h4>告警记录</h4>
            <ul>
              <li>所有触发的告警均记录，无论是否配置告警渠道</li>
              <li>来源字段标识告警来源：监控项名称 / 任务名称 / 告警域名</li>
              <li>支持按触发类型、渠道、状态、时间范围、执行ID筛选</li>
              <li><b>ID跳转</b>：点击「监控名称」列跳转到监控项管理（按ID查询），点击「任务名称」列跳转到监控任务（按ID查询）；若告警记录无对应监控项/任务ID则显示纯文本</li>
            </ul>
          </div>
        </el-collapse-item>

        <!-- 系统管理 -->
        <el-collapse-item name="system">
          <template #title>
            <el-icon style="margin-right: 8px; color: #606266"><Setting /></el-icon>
            <span style="font-weight: 600">系统管理</span>
          </template>
          <div class="section-content">
            <h4>变量管理</h4>
            <p>管理全局变量和任务变量的名称和默认值。全局变量持久存储，所有任务共享。</p>

            <h4>代理设置（仅ADMIN）</h4>
            <p>为所有监控项的HTTP请求配置统一代理。支持HTTP和SOCKS5两种代理类型。</p>
            <ul>
              <li><b>多代理配置</b>：可配置多个代理服务器，同时只能启用一个，也可停用全部</li>
              <li><b>启用代理</b>：点击"启用"按钮激活该代理，其他代理自动停用</li>
              <li><b>停用全部</b>：点击"停用"按钮取消所有代理，请求直连</li>
              <li><b>代理认证</b>：支持带用户名/密码的代理服务器（可选）</li>
              <li><b>启用/禁用</b>：禁用代理不会删除配置，只是暂时不使用</li>
              <li><b>连通性测试</b>：点击"测试"按钮验证代理是否可用（通过 httpbin.org 验证）</li>
            </ul>
            <p>配置示例：在代理设置页面添加代理 → 填写主机地址和端口 → 点击"启用" → 所有监控项请求自动通过该代理发送。</p>

            <h4>用户管理（ADMIN）</h4>
            <p>管理员可创建、编辑、删除用户账号，启用/禁用用户。禁用的用户即使Token未过期也无法登录。</p>

            <h4>数据备份</h4>
            <ul>
              <li><b>创建备份</b>：一键复制数据库文件（先执行 WAL checkpoint 保证完整性），备份存储在 <code>backups/</code> 目录</li>
              <li><b>下载备份</b>：生成一次性下载令牌（5 分钟有效）后下载；也可直接以管理员身份下载</li>
              <li><b>恢复备份</b>：选择历史备份文件恢复（需重启应用）</li>
              <li><b>上传恢复</b>：上传 <code>.db</code> 文件并恢复</li>
              <li><b>删除备份</b>：清理不需要的备份文件</li>
              <li><b>数据清理</b>：按天数清理历史数据，支持独立配置执行日志、告警记录、巡检记录的保留天数（默认 30 天），仅管理员可操作</li>
              <li><b>安全限制</b>：仅允许操作 <code>backups/</code> 目录下、后缀为 <code>.db</code> 的文件，并做了路径规范化、符号链接与真实路径校验，防止路径穿透读取其他文件</li>
            </ul>

            <h4>审计日志</h4>
            <ul>
              <li>自动记录所有写操作：创建/更新/删除监控项、任务、用户等</li>
              <li><b>登录审计</b>：记录登录成功/失败，密码字段完全脱敏为 <code>***</code></li>
              <li>记录操作人、操作类型、对象、请求详情、客户端IP</li>
              <li>支持按用户名和操作类型筛选</li>
            </ul>
          </div>
        </el-collapse-item>

        <!-- 安全设置 -->
        <el-collapse-item name="security">
          <template #title>
            <el-icon style="margin-right: 8px; color: #f56c6c"><Lock /></el-icon>
            <span style="font-weight: 600">安全设置（仅ADMIN）</span>
          </template>
          <div class="section-content">
            <h4>个人资料修改</h4>
            <p>点击右上角用户名 → "修改资料"，可修改昵称、邮箱、密码。修改密码需验证旧密码，新密码至少8位，需包含大写字母、小写字母和数字。</p>

            <h4>IP 访问控制</h4>
            <ul>
              <li><b>IP 白名单</b>：启用后仅允许白名单中的 IP 访问后端接口</li>
              <li><b>IP 黑名单</b>：优先级最高，被匹配的 IP 直接返回 403 拒绝访问</li>
              <li>支持 CIDR 格式（如 <code>192.168.1.0/24</code>），每行一个 IP</li>
              <li>黑名单优先于白名单：先检查黑名单，再检查白名单</li>
            </ul>

            <h4>IP 来源配置</h4>
            <ul>
              <li><b>IP来源请求头</b>：从指定请求头获取客户端真实IP（默认 <code>X-Real-IP</code>），多个头逗号分隔</li>
              <li><b>严格IP模式</b>：开启后，X-Forwarded-For 包含多个IP时拒绝请求</li>
              <li><b>可信代理IP</b>：逗号分隔的可信代理IP列表，非可信代理的XFF头不读取</li>
              <li>配置修改后需重启后端服务生效（缓存60秒）</li>
            </ul>

            <h4>监控请求安全（SSRF 防护）</h4>
            <p>控制监控项/告警请求能否访问内网、回环、云元数据等地址（连接前校验实际解析的 IP，缓解 DNS rebinding）。可在「安全设置」中切换，最多 5 秒生效、无需重启：</p>
            <ul>
              <li><b>严格（strict）</b>：禁止 回环、内网(10 / 172.16 / 192.168)、链路本地、CGNAT、云元数据(169.254.169.254)、IPv6 ULA</li>
              <li><b>允许内网（allow_internal）</b>：允许访问内网地址，但仍禁止 回环、链路本地(含云元数据)、CGNAT 等</li>
              <li><b>关闭（off）</b>：不做任何限制，适用于系统本身部署在内网、需要监控内网地址的场景</li>
            </ul>
            <p>环境变量 <code>SSRF_PROTECTION</code> 仅作为首次初始化的默认值。</p>

            <h4>登录失败锁定</h4>
            <ul>
              <li>可配置连续登录失败 N 次后自动锁定账号（默认5次）</li>
              <li>锁定时长可配置（默认30分钟），到期自动解锁</li>
              <li>被锁定的账号在"用户管理"中显示锁图标，鼠标悬浮提示"账号被锁定，请联系管理员解锁！"</li>
              <li>管理员可在"安全设置"中解锁所有账号或指定账号</li>
              <li><b>admin 账号被锁只能重启后端服务解锁</b>（重启仅解锁 admin，不解锁其他账号）</li>
            </ul>

            <h4>同时在线人数限制</h4>
            <ul>
              <li>可配置同一账号最大同时在线人数（默认3人）</li>
              <li>超过限制时自动挤掉最早登录但仍在线的会话</li>
              <li>在"用户管理"中可查看每个账号的在线会话数，点击可查看登录IP、浏览器、登录时间</li>
              <li>支持踢出指定会话（当前登录会话标有"当前"标签，不可被踢出）</li>
            </ul>

            <h4>用户管理增强</h4>
            <ul>
              <li><b>Admin 账号保护</b>：admin 账号不能被删除、禁用或修改角色</li>
              <li><b>在线状态</b>：用户列表显示每个账号的当前在线会话数</li>
              <li><b>锁定状态</b>：被锁定的账号显示红色锁图标</li>
            </ul>
          </div>
        </el-collapse-item>

        <!-- 告警渠道扩展 -->
        <el-collapse-item name="alert-channels">
          <template #title>
            <el-icon style="margin-right: 8px; color: #409eff"><Bell /></el-icon>
            <span style="font-weight: 600">告警渠道（钉钉/企业微信/飞书）</span>
          </template>
          <div class="section-content">
            <h4>支持的告警渠道</h4>
            <p>除邮件和Webhook外，系统还支持以下即时通讯平台的告警通知：</p>
            <ul>
              <li><b>邮件</b>：通过SMTP发送告警邮件，支持SSL加密（端口465）</li>
              <li><b>Webhook</b>：向指定URL发送HTTP POST/PUT请求</li>
              <li><b>钉钉</b>：通过自定义机器人Webhook发送告警，支持关键词安全设置</li>
              <li><b>企业微信</b>：通过自定义机器人Webhook发送告警，支持Markdown消息格式</li>
              <li><b>飞书</b>：通过自定义机器人Webhook发送告警，支持富文本消息格式</li>
            </ul>

            <h4>配置步骤（钉钉/企业微信/飞书）</h4>
            <ul>
              <li>1. 在对应平台创建自定义机器人，获取Webhook地址</li>
              <li>2. 进入"监控告警 → 告警渠道"，点击"添加渠道"</li>
              <li>3. 选择对应的平台类型，填写Webhook地址和相关配置</li>
              <li>4. 点击"测试"按钮验证连通性</li>
            </ul>

            <h4>注意事项</h4>
            <ul>
              <li>钉钉安全设置选择"自定义关键词"时，告警消息中需包含该关键词（系统通知含"任务通知"和"告警通知"）</li>
              <li>企业微信群机器人最多关联200个群，建议按环境分群</li>
              <li>飞书机器人需在目标群中添加，且群设置中需开启"群webhook"功能</li>
              <li>所有渠道在告警模版中配置冷却时间和限频规则</li>
            </ul>
          </div>
        </el-collapse-item>

        <!-- 周期提醒 -->
        <el-collapse-item name="reminder">
          <template #title>
            <el-icon style="margin-right: 8px; color: #e6a23c"><AlarmClock /></el-icon>
            <span style="font-weight: 600">周期提醒</span>
          </template>
          <div class="section-content">
            <h4>功能说明</h4>
            <p>周期提醒用于管理服务器续费、SSL证书续费、机房费用、定期巡检、事务提醒等周期性任务。支持一次性提醒和周期性重复提醒，到期时通过配置的告警渠道发送通知。</p>

            <h4>分类与默认提前天数</h4>
            <el-table :data="reminderCategories" stripe size="small" style="margin: 12px 0">
              <el-table-column prop="category" label="分类" width="140" />
              <el-table-column prop="defaultDays" label="默认提前天数" width="120" />
              <el-table-column prop="desc" label="适用场景" />
            </el-table>

            <h4>重复类型</h4>
            <el-table :data="recurrenceTypes" stripe size="small" style="margin: 12px 0">
              <el-table-column prop="type" label="类型" width="100" />
              <el-table-column prop="config" label="配置项" width="200" />
              <el-table-column prop="desc" label="说明" />
            </el-table>

            <h4>提前提醒</h4>
            <p>每个提醒可配置提前提醒，在到期前 N 天 + N 分钟发送通知。例如：服务器续费默认提前30天提醒，SSL证书续费默认提前30天提醒。</p>

            <h4>到期处理逻辑</h4>
            <p>到期时系统<b>只发送通知，不会自动完成</b>：</p>
            <ul>
              <li>未手动处理的任务会一直保持「<b>已过期</b>」状态，并在仪表盘角标中持续计数</li>
              <li><b>一次性任务</b>：点击「完成」后标记为已完成</li>
              <li><b>周期性任务</b>：点击「完成」后推进到<b>下一次</b>到期时间；若已错过多次，会直接跳到下一个未来时间点（不会逐次补发）</li>
              <li>同一个到期时间点只通知一次（24 小时后仍未处理会再次提醒）</li>
            </ul>

            <h4>仪表盘角标</h4>
            <p>仪表盘顶部「周期提醒」按钮显示角标，数字 = <b>已过期</b> + <b>今天到期</b> + <b>近7天到期</b>的提醒合计数量。点击可跳转到提醒列表。</p>

            <h4>操作说明</h4>
            <ul>
              <li><b>完成</b>：一次性任务标记为已完成；周期性任务推进到下一次到期时间</li>
              <li><b>延迟</b>：将提醒推迟到指定时间，仅对今天到期的任务显示。可选 5分钟/15分钟/30分钟/1小时/明天/自定义</li>
              <li><b>预览</b>：新建/编辑周期性提醒时，可预览接下来的提醒时间线</li>
            </ul>

            <h4>通知渠道</h4>
            <p>创建提醒时可选择通知渠道（复用告警渠道配置）。到期时系统通过选定渠道发送通知，通知内容包含"任务通知"关键词（兼容钉钉安全设置）。</p>

            <h4>权限</h4>
            <ul>
              <li>ADMIN / OPERATOR：可创建、编辑、删除、完成、延迟提醒</li>
              <li>VIEWER：仅可查看提醒列表</li>
            </ul>
          </div>
        </el-collapse-item>

        <!-- Uptime与状态页 -->
        <el-collapse-item name="uptime-status">
          <template #title>
            <el-icon style="margin-right: 8px; color: #67c23a"><CircleCheck /></el-icon>
            <span style="font-weight: 600">Uptime统计与公开状态页</span>
          </template>
          <div class="section-content">
            <h4>Uptime 统计</h4>
            <p>仪表盘中的"可用性统计"卡片显示各时间窗口内所有监控项的综合可用率。</p>
            <ul>
              <li><b>时间窗口</b>：支持 1小时、6小时、24小时、7天、30天</li>
              <li><b>计算方式</b>：成功执行次数 / 总执行次数 × 100%</li>
              <li><b>颜色标识</b>：≥99% 绿色（正常）、95%~99% 橙色（警告）、&lt;95% 红色（异常）</li>
            </ul>

            <h4>公开状态页</h4>
            <p>无需登录即可访问的监控状态公示页面，适合对外展示服务可用性。</p>
            <ul>
              <li><b>访问方式</b>：点击侧边栏"仪表盘"旁边的"状态公示页"链接</li>
              <li><b>直接访问</b>：浏览器打开 <code>/status</code> 路径（无需登录）</li>
              <li><b>展示内容</b>：所有监控项的当前状态、SSL证书状态、最近告警</li>
              <li><b>分享方式</b>：可将 <code>/status</code> 页面链接分享给外部用户</li>
            </ul>
          </div>
        </el-collapse-item>

        <!-- API Schema 变更检测 -->
        <el-collapse-item name="api-schema">
          <template #title>
            <el-icon style="margin-right: 8px; color: #e6a23c"><Document /></el-icon>
            <span style="font-weight: 600">API Schema 变更检测</span>
          </template>
          <div class="section-content">
            <h4>独立管理 Schema</h4>
            <p>在"API Schema"菜单中统一管理 JSON Schema 定义，与监控项解耦。每个 Schema 包含名称、描述和 JSON Schema 内容。</p>
            <p>编辑 Schema 时提供<b>可视化编辑器</b>和<b>JSON 编辑</b>两种模式，可随时切换。可视化模式下支持：</p>
            <ul>
              <li>拖拽排序字段顺序</li>
              <li>点击 <code>*</code> 星号设置必填字段</li>
              <li>object/array 类型可折叠/展开子字段</li>
              <li>「快速添加」一键插入常用模板（分页响应、RESTful、用户信息等）</li>
              <li>「JSON预览」实时查看生成的 JSON Schema</li>
            </ul>

            <h4>监控项集成</h4>
            <p>在监控项的添加/编辑弹窗中，开启"API Schema告警"后可配置：</p>
            <ul>
              <li><b>期望Schema</b>：使用可视化编辑器构建，或从 Schema 列表中选择后自动填充</li>
              <li><b>变更类型</b>：多选需要告警的变更类型（新增/删除/修改/破坏性）</li>
              <li><b>告警通道</b>：选择告警通知渠道（复用现有告警通道配置）</li>
            </ul>

            <h4>执行时校验</h4>
            <p>监控项执行时，如果响应内容是合法 JSON，系统会自动用期望 Schema 进行结构校验和变更检测。非 JSON 响应将跳过校验。</p>

            <h4>变更检测能力</h4>
            <ul>
              <li><b>ADDED</b>：响应中存在但 Schema 中未定义的字段</li>
              <li><b>REMOVED</b>：Schema 中定义但响应中不存在的字段</li>
              <li><b>MODIFIED</b>：同名字段类型发生变化（如 string → integer）</li>
              <li><b>BREAKING</b>：必填字段被删除等不兼容变更</li>
              <li>支持嵌套对象的递归检测</li>
              <li>一次检测可同时发现多种变更类型</li>
            </ul>

            <h4>历史版本对比</h4>
            <p>Schema 的每次修改都会记录历史版本。在 Schema 列表点击"历史"按钮可查看版本列表，选择任意历史版本与当前版本进行左右对照，差异字段高亮标记。</p>

            <h4>告警与记录</h4>
            <ul>
              <li>检测到匹配的变更时，通过配置的告警通道发送通知</li>
              <li>告警模版触发类型：<b>SCHEMA_CHANGE</b>（API Schema变更）</li>
              <li>告警记录写入告警日志，可在"告警记录"页面查看</li>
            </ul>

            <h4>JSON Schema 案例</h4>

            <p style="margin: 12px 0 8px; color: #909399; font-size: 13px">使用可视化编辑器时，可通过工具栏「快速添加」按钮一键插入以下模板，也可手动从零构建。以下是常见场景的 JSON Schema 模板：</p>

            <el-collapse>
              <el-collapse-item name="example-basic">
                <template #title>
                  <span style="font-weight: 500">案例一：基础用户信息接口</span>
                </template>
                <div style="background: #f5f7fa; padding: 16px; border-radius: 6px; margin-bottom: 8px">
                  <p style="margin: 0 0 8px; font-size: 13px; color: #909399">适用于：用户详情、个人信息查询等接口</p>
                  <pre style="margin: 0; font-size: 12px; line-height: 1.6; white-space: pre-wrap; background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 4px">{
  "type": "object",                    <span style="color:#6a9955">// 根节点必须是对象</span>
  "required": ["code", "data"],         <span style="color:#6a9955">// code 和 data 必须存在</span>
  "properties": {
    "code": {
      "type": "integer"                 <span style="color:#6a9955">// 状态码：整数</span>
    },
    "message": {
      "type": "string"                  <span style="color:#6a9955">// 提示信息：字符串</span>
    },
    "data": {
      "type": "object",                 <span style="color:#6a9955">// 业务数据：对象</span>
      "required": ["id", "username"],
      "properties": {
        "id": {
          "type": "integer"             <span style="color:#6a9955">// 用户ID：整数</span>
        },
        "username": {
          "type": "string"              <span style="color:#6a9955">// 用户名：字符串</span>
        },
        "email": {
          "type": ["string", "null"]    <span style="color:#6a9955">// 邮箱：可为 null</span>
        },
        "avatar": {
          "type": "string"              <span style="color:#6a9955">// 头像URL</span>
        },
        "createdAt": {
          "type": "string"              <span style="color:#6a9955">// 创建时间（ISO 8601 格式）</span>
        }
      }
    }
  }
}</pre>
                </div>
              </el-collapse-item>

              <el-collapse-item name="example-list">
                <template #title>
                  <span style="font-weight: 500">案例二：分页列表接口</span>
                </template>
                <div style="background: #f5f7fa; padding: 16px; border-radius: 6px; margin-bottom: 8px">
                  <p style="margin: 0 0 8px; font-size: 13px; color: #909399">适用于：分页查询、列表搜索等接口</p>
                  <pre style="margin: 0; font-size: 12px; line-height: 1.6; white-space: pre-wrap; background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 4px">{
  "type": "object",
  "required": ["code", "data"],
  "properties": {
    "code": { "type": "integer" },
    "data": {
      "type": "object",
      "required": ["records", "total", "page", "size"],
      "properties": {
        "records": {
          "type": "array",              <span style="color:#6a9955">// 数据列表：数组</span>
          "items": {
            "type": "object",
            "required": ["id", "name", "status"],
            "properties": {
              "id": { "type": "integer" },
              "name": { "type": "string" },
              "status": {
                "type": "string",
                "enum": ["active", "inactive", "pending"]  <span style="color:#6a9955">// 状态枚举</span>
              },
              "createdAt": { "type": "string" }
            }
          }
        },
        "total": { "type": "integer" },  <span style="color:#6a9955">// 总记录数</span>
        "page": { "type": "integer" },   <span style="color:#6a9955">// 当前页码</span>
        "size": { "type": "integer" }    <span style="color:#6a9955">// 每页大小</span>
      }
    }
  }
}</pre>
                </div>
              </el-collapse-item>

              <el-collapse-item name="example-nested">
                <template #title>
                  <span style="font-weight: 500">案例三：嵌套复杂结构接口</span>
                </template>
                <div style="background: #f5f7fa; padding: 16px; border-radius: 6px; margin-bottom: 8px">
                  <p style="margin: 0 0 8px; font-size: 13px; color: #909399">适用于：订单详情、商品信息等多层嵌套接口</p>
                  <pre style="margin: 0; font-size: 12px; line-height: 1.6; white-space: pre-wrap; background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 4px">{
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
              "quantity": { "type": "integer", "minimum": 1 },  <span style="color:#6a9955">// 最小值约束</span>
              "price": { "type": "number" },                     <span style="color:#6a9955">// 价格：浮点数</span>
              "tags": { "type": "array", "items": { "type": "string" } }
            }
          }
        },
        "payment": {
          "type": "object",
          "required": ["method", "amount"],
          "properties": {
            "method": {
              "type": "string",
              "enum": ["credit_card", "alipay", "wechat"]  <span style="color:#6a9955">// 支付方式枚举</span>
            },
            "amount": { "type": "number" },
            "currency": { "type": "string", "default": "CNY" }  <span style="color:#6a9955">// 默认值</span>
          }
        }
      }
    }
  }
}</pre>
                </div>
              </el-collapse-item>

              <el-collapse-item name="example-api">
                <template #title>
                  <span style="font-weight: 500">案例四：RESTful API 通用响应</span>
                </template>
                <div style="background: #f5f7fa; padding: 16px; border-radius: 6px; margin-bottom: 8px">
                  <p style="margin: 0 0 8px; font-size: 13px; color: #909399">适用于：标准化 API 响应格式，兼容成功和错误场景</p>
                  <pre style="margin: 0; font-size: 12px; line-height: 1.6; white-space: pre-wrap; background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 4px">{
  "type": "object",
  "required": ["code", "success", "message"],
  "properties": {
    "code": {
      "type": "integer",
      "description": "业务状态码，0=成功，非0=失败"  <span style="color:#6a9955">// 字段描述</span>
    },
    "success": {
      "type": "boolean"                 <span style="color:#6a9955">// 是否成功</span>
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
}</pre>
                </div>
              </el-collapse-item>

              <el-collapse-item name="example-tips">
                <template #title>
                  <span style="font-weight: 500">编写技巧与注意事项</span>
                </template>
                <div style="padding: 8px 0">
                  <ul style="margin: 0; padding-left: 20px; line-height: 2">
                    <li><b>required 字段</b>：建议只标记业务必须存在的字段，避免过度严格导致频繁告警</li>
                    <li><b>type 数组</b>：用 <code>"type": ["string", "null"]</code> 表示可空字段</li>
                    <li><b>嵌套检测</b>：系统会递归检测嵌套对象和数组元素的结构变更</li>
                    <li><b>BREAKING 判定</b>：当必填字段（required）被删除时自动判定为破坏性变更</li>
                    <li><b>安全删除</b>：不需要的字段不要从 Schema 中删除，而是保留旧定义，避免误触 BREAKING 告警</li>
                    <li><b>建议做法</b>：Schema 应反映 API 的<b>稳定契约</b>，而非临时的数据格式</li>
                    <li><b>版本对比</b>：修改 Schema 前，先通过"历史"按钮查看上一版本，确认变更合理性</li>
                  </ul>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>
        </el-collapse-item>

        <!-- 巡检模式 -->
        <el-collapse-item name="report-inspection">
          <template #title>
            <el-icon style="margin-right: 8px; color: #409eff"><Monitor /></el-icon>
            <span style="font-weight: 600">巡检模式</span>
          </template>
          <div class="section-content">
            <p>批量执行一组监控项并生成巡检报告，适用于定期健康检查场景。</p>
            <ul>
              <li><b>创建巡检配置</b>：选择要巡检的监控项列表（或选择全部）</li>
              <li><b>执行巡检</b>：点击"执行巡检"按钮，可选择单个/多个/全部配置执行</li>
              <li><b>定时执行</b>：配置Cron定时策略，系统自动按时执行</li>
              <li><b>巡检报告</b>：显示每个监控项的执行结果（状态、响应时间、错误信息）</li>
              <li><b>状态标识</b>：COMPLETED（完成）/ RUNNING（执行中）/ FAILED（失败）</li>
            </ul>
          </div>
        </el-collapse-item>

        <!-- 缓存与数据保留 -->
        <el-collapse-item name="cache-retention">
          <template #title>
            <el-icon style="margin-right: 8px; color: #909399"><Clock /></el-icon>
            <span style="font-weight: 600">缓存与数据保留</span>
          </template>
          <div class="section-content">
            <p>为降低高频监控对数据库的压力，系统对部分数据采用了缓存/节流策略，<b>修改配置后不一定立即反映在已缓存的数据上</b>，请知悉。</p>
            <el-table :data="cacheItems" stripe size="small" style="margin: 12px 0">
              <el-table-column prop="item" label="项目" width="180" />
              <el-table-column prop="rule" label="策略" width="220" />
              <el-table-column prop="effect" label="影响" />
            </el-table>
            <h4>可调整配置（application.yml）</h4>
            <ul>
              <li><code>monitor.ssl-check-interval-minutes</code>：同一域名 SSL 证书两次检查的最小间隔（默认 360 分钟）</li>
              <li><code>monitor.domain-asset-update-interval-minutes</code>：同一域名资产/IP 记录的最小更新间隔（默认 10 分钟）</li>
              <li><code>monitor.log-body-max-size</code>：单条日志请求/响应体入库最大字节数（默认 262144，即 256KB）</li>
              <li><code>monitor.log-retention-days</code>：执行/告警日志自动保留天数（默认 30 天）</li>
            </ul>
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const activeSections = ref(['quickstart'])

const variableScopes = [
  { scope: 'global', example: '{{global.baseUrl}}', desc: '全局变量，持久存储，所有任务共享' },
  { scope: 'group', example: '{{group.token}}', desc: '任务变量，每次任务运行时重置' },
  { scope: 'env', example: '{{env.timestamp}}', desc: '内置变量，无需创建' }
]

const cacheItems = [
  { item: 'SSL 证书检查', rule: '按域名缓存，默认 6 小时', effect: '证书信息不会每次执行都刷新，可能短暂滞后；告警仍按每日 9:00 检查' },
  { item: '域名/IP 记录', rule: '按域名节流，默认 10 分钟', effect: 'last_seen、IP 变化最多延迟 10 分钟更新' },
  { item: '告警静默规则', rule: '缓存 30 秒', effect: '在页面增删改即时生效；直接改库最多延迟 30 秒' },
  { item: '执行/告警日志', rule: '每日 03:30 自动清理，默认保留 30 天', effect: '超期日志自动删除，手动清理可立即释放' },
  { item: '请求/响应体', rule: '超过 256KB 截断保存', effect: '详情中可能显示 ...[truncated ...]' }
]

const builtinVarsTable = ref([
  { name: 'timestamp', desc: '当前时间戳（毫秒）', example: '1757270400000' },
  { name: 'timestamp_s', desc: '当前时间戳（秒）', example: '1757270400' },
  { name: 'time', desc: '北京时间 HH:mm:ss', example: '16:30:45' },
  { name: 'date', desc: '北京时间 yyyy-MM-dd', example: '2026-09-07' },
  { name: 'datetime', desc: '北京时间 yyyy-MM-dd HH:mm:ss', example: '2026-09-07 16:30:45' },
  { name: 'datetime_ms', desc: '北京时间带毫秒', example: '2026-09-07 16:30:45.123' },
  { name: 'unix', desc: 'Unix时间戳（秒）', example: '1757270400' },
  { name: 'uuid', desc: '随机UUID', example: '550e8400-e29b-...' },
  { name: 'uuid_short', desc: '短UUID（8位）', example: 'a3f2b8c1' },
  { name: 'random', desc: '0~999999 随机数', example: '48291' },
  { name: 'nonce', desc: '32位随机字符串', example: 'k8j2h5g9d2m7...' }
])

const triggerTypes = [
  { type: 'FAIL', desc: '监控项执行失败（状态码不匹配或请求异常）' },
  { type: 'RESPONSE_TIME', desc: '响应时间超过阈值且连续触发达到指定次数' },
  { type: 'GROUP_FAIL', desc: '监控任务中所有执行的监控项均失败' },
  { type: 'SSL_CERT', desc: 'SSL证书已过期或证书与域名不匹配' },
  { type: 'SCHEMA_CHANGE', desc: 'API Schema检测到匹配的变更类型（新增/删除/修改/破坏性）' },
  { type: 'REMINDER', desc: '周期提醒到期或提前提醒触发' },
  { type: 'ALL', desc: '通用模板，未匹配到专用模板时的兜底' }
]

const templateVars = [
  { variable: '{{monitorId}}', desc: '监控项ID' },
  { variable: '{{monitorName}}', desc: '监控项名称' },
  { variable: '{{groupId}}', desc: '监控任务ID' },
  { variable: '{{url}}', desc: '请求URL' },
  { variable: '{{status}}', desc: '执行状态（SUCCESS/FAIL）' },
  { variable: '{{statusCode}}', desc: 'HTTP状态码' },
  { variable: '{{errorMessage}}', desc: '错误信息' },
  { variable: '{{executedAt}}', desc: '执行时间' },
  { variable: '{{responseTime}}', desc: '响应时间（毫秒）' },
  { variable: '{{domain}}', desc: '域名' }
]

const alertPriority = [
  { priority: '1', name: '恢复通知', desc: '监控项从失败变为成功，且之前有告警记录', action: '直接发送恢复通知，重置限频计数' },
  { priority: '2', name: '静默检查', desc: '当前时间在静默规则的时间窗口内', action: '抑制通知，不计入限频计数，记录 SILENCED' },
  { priority: '3', name: '冷却检查', desc: '距上次发送未超过模板冷却时间', action: '抑制通知，不计入限频计数，记录 SUPPRESSED' },
  { priority: '4', name: '限频检查', desc: '相同告警累计次数达到模板配置的上限', action: '抑制通知，记录 SUPPRESSED' },
  { priority: '5', name: '正常发送', desc: '通过所有检查', action: '计数+1，通过渠道发送告警' }
]

const alertStatuses = [
  { status: 'SENT', desc: '已发送，告警通过渠道成功发出' },
  { status: 'FAILED', desc: '发送失败，渠道返回错误' },
  { status: 'NO_CHANNEL', desc: '无告警渠道，仅记录日志' },
  { status: 'SUPPRESSED', desc: '被限频抑制，未发送通知' },
  { status: 'SILENCED', desc: '被静默抑制，未发送通知' },
  { status: 'CONFIG_UNAVAILABLE', desc: '告警渠道配置不存在或已禁用' }
]

const reminderCategories = [
  { category: '服务器续费', defaultDays: '30天', desc: '云服务器、VPS等到期续费提醒' },
  { category: 'SSL证书续费', defaultDays: '30天', desc: 'SSL证书到期续费提醒' },
  { category: '机房费用', defaultDays: '7天', desc: '机房托管、带宽等费用到期提醒' },
  { category: '定期巡检', defaultDays: '3天', desc: '设备巡检、安全检查等定期任务提醒' },
  { category: '事务提醒', defaultDays: '1天', desc: '日常事务、工作待办等提醒' },
  { category: '其他', defaultDays: '1天', desc: '其他需要提醒的事项' }
]

const recurrenceTypes = [
  { type: '不重复', config: '无', desc: '仅在首次提醒时间触发一次' },
  { type: '每天', config: '无', desc: '每天同一时间触发' },
  { type: '每周', config: '选择星期几', desc: '每周指定星期触发（可多选）' },
  { type: '每月', config: '选择几号', desc: '每月指定日期触发（1-28号）' },
  { type: '每年', config: '选择月-日', desc: '每年同月同日触发' }
]

const roles = [
  { role: 'ADMIN', desc: '管理员，所有操作权限，包括用户管理' },
  { role: 'OPERATOR', desc: '运维，监控/任务/变量/告警的增删改查，不可管理用户' },
  { role: 'VIEWER', desc: '仅查看，所有页面只读，不可执行任何修改操作' }
]

const requestApi = [
  { prop: 'request.method', type: 'String', desc: 'HTTP方法（GET/POST/PUT/DELETE），可修改' },
  { prop: 'request.url', type: 'String', desc: '完整请求URL，可修改' },
  { prop: 'request.body', type: 'String', desc: '请求体字符串，需JSON.parse()解析，可修改' },
  { prop: 'request.headers', type: 'Object', desc: '请求头键值对对象，可读写' },
  { prop: 'request.params', type: 'Object', desc: 'URL查询参数键值对对象，可读写' }
]

const helperFunctions = [
  { func: 'setHeader(key, value)', desc: '设置请求头' },
  { func: 'setParam(key, value)', desc: '设置URL查询参数' },
  { func: 'setBody(str)', desc: '替换整个请求体' },
  { func: 'setBodyField(key, value)', desc: '解析JSON Body并设置字段（自动序列化）' }
]

const cryptoApi = [
  { method: 'crypto.md5(str)', desc: 'MD5哈希，返回十六进制字符串' },
  { method: 'crypto.sha1(str)', desc: 'SHA-1哈希，返回十六进制字符串' },
  { method: 'crypto.sha256(str)', desc: 'SHA-256哈希，返回十六进制字符串' },
  { method: 'crypto.hmacSha256(str, key)', desc: 'HMAC-SHA256，返回十六进制字符串' },
  { method: 'crypto.hmacSha256Base64(str, key)', desc: 'HMAC-SHA256，返回Base64字符串' },
  { method: 'crypto.hmacMd5(str, key)', desc: 'HMAC-MD5，返回十六进制字符串' },
  { method: 'crypto.base64(str)', desc: 'Base64编码' },
  { method: 'crypto.base64Decode(str)', desc: 'Base64解码' },
  { method: 'crypto.rsaSha256Sign(str, privateKeyPem)', desc: 'RSA-SHA256签名，返回Base64字符串' },
  { method: 'crypto.rsaSha256SignHex(str, privateKeyPem)', desc: 'RSA-SHA256签名，返回十六进制字符串' }
]

const utilApi = [
  { method: 'util.timestamp()', desc: '当前毫秒时间戳' },
  { method: 'util.timestampS()', desc: '当前秒时间戳' },
  { method: 'util.datetime()', desc: '当前时间 yyyy-MM-dd HH:mm:ss（北京时间）' },
  { method: 'util.date()', desc: '当前日期 yyyy-MM-dd' },
  { method: 'util.time()', desc: '当前时间 HH:mm:ss' },
  { method: 'util.nonce(len?)', desc: '随机字符串，默认32位，可指定长度' },
  { method: 'util.uuid()', desc: '完整UUID v4' },
  { method: 'util.uuidShort()', desc: '短UUID（前8位）' },
  { method: 'util.random(max?)', desc: '随机整数 [0, max)，默认1000000' }
]
</script>

<style lang="scss" scoped>
.manual-page {
  .section-content {
    h4 {
      margin: 16px 0 8px 0;
      color: #303133;
      font-size: 15px;

      &:first-child {
        margin-top: 0;
      }
    }

    p {
      color: #606266;
      line-height: 1.8;
      margin: 4px 0;
    }

    ul {
      margin: 8px 0;
      padding-left: 20px;

      li {
        color: #606266;
        line-height: 1.8;
      }
    }

    code {
      background: #f5f7fa;
      padding: 1px 6px;
      border-radius: 3px;
      color: #e6a23c;
      font-size: 13px;
    }

    .code-block {
      background: #1e1e1e;
      color: #d4d4d4;
      padding: 12px 16px;
      border-radius: 6px;
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 13px;
      line-height: 1.6;
      overflow-x: auto;
      margin: 8px 0;
    }
  }

  :deep(.el-collapse-item__header) {
    font-size: 15px;
  }
}
</style>
