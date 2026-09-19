<template>
  <el-dialog
    :model-value="modelValue"
    :title="editingRow ? '编辑监控' : '添加监控'"
    width="1100px"
    top="3vh"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="150px">
      <el-tabs v-model="activeDialogTab" class="monitor-dialog-tabs">
        <!-- Tab 1: 常规配置 -->
        <el-tab-pane label="常规配置" name="basic">
          <!-- 行1: 名称 + 启用 -->
          <el-row :gutter="12">
            <el-col :span="16">
              <el-form-item label="名称" prop="name">
                <el-input v-model="form.name" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="启用">
                <el-switch v-model="form.enabled" />
              </el-form-item>
            </el-col>
          </el-row>
          <!-- 行2: URL + 请求方法 -->
          <el-row :gutter="12">
            <el-col :span="16">
              <el-form-item prop="url">
                <template #label>
                  <span>URL</span>
                  <el-tooltip placement="top" :width="360">
                    <template #content>
                      <div style="line-height: 1.8">
                        <div><b>变量语法:</b> <code>&#123;&#123;scope.name&#125;&#125;</code></div>
                        <div><b>global.xxx</b> — 全局变量，所有监控共享</div>
                        <div><b>group.xxx</b> — 任务变量，每次任务运行时重置</div>
                        <div><b>env.xxx</b> — 即时变量，每次请求重新生成</div>
                        <div style="margin-top: 6px"><b>env 可用值:</b></div>
                        <div><code>timestamp</code> 毫秒时间戳 | <code>timestamp_s</code> 秒时间戳</div>
                        <div><code>datetime</code> 2024-01-01 12:00:00 | <code>date</code> 2024-01-01</div>
                        <div><code>uuid</code> UUID | <code>uuid_short</code> 8位短UUID</div>
                        <div><code>nonce</code> 32位随机串 | <code>random</code> 0~999999</div>
                        <div style="margin-top: 6px"><b>示例:</b></div>
                        <div><code>https://api.example.com/&#123;&#123;global.version&#125;&#125;/users</code></div>
                        <div><code>https://api.example.com/users?t=&#123;&#123;env.timestamp&#125;&#125;</code></div>
                      </div>
                    </template>
                    <el-icon class="label-help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </template>
                <el-input v-model="form.url" placeholder="https://example.com/api/test" />
                <!--<div class="var-hint-inline">
                  <span class="var-hint-label"><el-icon class="var-hint-icon"><InfoFilled /></el-icon> 支持变量:</span>
                  <code v-text="'{{global.baseUrl}}'"></code>
                  <code v-text="'{{group.token}}'"></code>
                  <code v-text="'{{env.timestamp}}'"></code>
                </div>-->
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="请求方法">
                <el-select v-model="form.method" style="width: 100%">
                  <el-option label="GET" value="GET" />
                  <el-option label="POST" value="POST" />
                  <el-option label="PUT" value="PUT" />
                  <el-option label="DELETE" value="DELETE" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <!-- 行3: 请求头 -->
          <el-form-item>
            <template #label>
              <span>请求头</span>
              <el-tooltip placement="top" :width="320">
                <template #content>
                  <div style="line-height: 1.8">
                    <div><b>JSON 格式</b>，Key-Value 对</div>
                    <div style="margin-top: 4px"><b>示例:</b></div>
                    <div><code>&#123;"Authorization": "Bearer &#123;&#123;group.token&#125;&#125;"&#125;</code></div>
                    <div><code>&#123;"X-Request-Id": "&#123;&#123;env.uuid&#125;&#125;"&#125;</code></div>
                    <div><code>&#123;"X-Timestamp": "&#123;&#123;env.timestamp&#125;&#125;"&#125;</code></div>
                  </div>
                </template>
                <el-icon class="label-help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
            <el-input v-model="form.headers" type="textarea" :rows="2" placeholder='{"Content-Type": "application/json"}' />
            <!--<div class="var-hint-inline">
              <span class="var-hint-label"><el-icon class="var-hint-icon"><InfoFilled /></el-icon> 支持变量:</span>
              <code v-text="'{{global.baseUrl}}'"></code>
              <code v-text="'{{group.token}}'"></code>
            </div>-->
          </el-form-item>
          <!-- POST/PUT 请求体配置 -->
          <template v-if="form.method === 'POST' || form.method === 'PUT'">
            <el-divider content-position="left" style="margin: 6px 0">请求体</el-divider>
            <el-form-item label="Body类型">
              <el-radio-group v-model="form.bodyType" @change="onBodyTypeChange">
                <el-radio value="none">none</el-radio>
                <el-radio value="json">JSON</el-radio>
                <el-radio value="form">Form Data</el-radio>
                <el-radio value="xml">XML</el-radio>
                <el-radio value="text">Raw Text</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="form.bodyType === 'json'" label="JSON">
              <el-input v-model="form.body" type="textarea" :rows="6" placeholder='{"key": "value"}' class="body-editor" />
              <div class="var-hint-inline">
                <span class="var-hint-label"><el-icon class="var-hint-icon"><InfoFilled /></el-icon> 支持变量:</span>
                <code v-text="'{{global.baseUrl}}'"></code>
                <code v-text="'{{group.token}}'"></code>
                <code v-text="'{{env.timestamp}}'"></code>
              </div>
            </el-form-item>
            <el-form-item v-if="form.bodyType === 'form'" label="Form参数">
              <div v-for="(param, index) in formParams" :key="index" class="form-param-row">
                <el-input v-model="param.key" placeholder="Key" style="width: 35%" />
                <el-input v-model="param.value" placeholder="Value (支持变量)" style="width: 35%" />
                <el-button type="danger" link @click="removeFormParam(index)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <el-button type="primary" link @click="addFormParam">
                <el-icon><Plus /></el-icon>
                添加参数
              </el-button>
            </el-form-item>
            <el-form-item v-if="form.bodyType === 'xml'" label="XML">
              <el-input v-model="form.body" type="textarea" :rows="6" placeholder='<root><token>value</token></root>' class="body-editor" />
              <div class="var-hint-inline">
                <span class="var-hint-label"><el-icon class="var-hint-icon"><InfoFilled /></el-icon> 支持变量:</span>
                <code v-text="'{{global.baseUrl}}'"></code>
                <code v-text="'{{group.token}}'"></code>
              </div>
            </el-form-item>
            <el-form-item v-if="form.bodyType === 'text'" label="Raw Text">
              <el-input v-model="form.body" type="textarea" :rows="6" class="body-editor" />
              <div class="var-hint-inline">
                <span class="var-hint-label"><el-icon class="var-hint-icon"><InfoFilled /></el-icon> 支持变量:</span>
                <code v-text="'{{global.baseUrl}}'"></code>
                <code v-text="'{{group.token}}'"></code>
              </div>
            </el-form-item>
          </template>
          <!-- 行4: 超时 + 状态页展示 -->
          <el-row :gutter="12">
            <el-col :span="8">
              <el-form-item label="超时">
                <el-input-number v-model="form.timeout" :min="1" :max="300" style="width: 150px" />
                <span style="color: #909399; font-size: 12px; margin-left: 4px">秒</span>
              </el-form-item>
            </el-col>
            <el-col :span="16">
              <el-form-item label="公开状态页展示">
                <el-switch v-model="form.showOnStatusPage" />
                <el-tooltip placement="right" :width="300">
                  <template #content>
                    <div style="line-height: 1.8">
                      <div>开启后，此监控项将显示在<strong>公开状态页</strong>中。</div>
                      <div style="margin-top: 4px">前提是系统「安全设置」中已开启<strong>「公开状态页」</strong>开关。</div>
                      <div style="margin-top: 4px;color:#E6A23C">关闭后，即使系统允许公开访问，此监控项也不会出现在状态页。</div>
                    </div>
                  </template>
                  <el-icon style="margin-left: 6px; color: #909399; cursor: pointer"><QuestionFilled /></el-icon>
                </el-tooltip>
              </el-form-item>
            </el-col>
          </el-row>
          <!-- 判断条件区块 -->
          <div class="section-block section-block--condition">
            <div class="section-block__header">
              <span class="section-block__title">监控项判断条件</span>
              <el-tooltip placement="right" :width="360">
                <template #content>
                  <div style="line-height: 1.8">
                    <div>满足以下条件才判定监控<b>成功</b>，否则告警:</div>
                    <div style="margin-top: 4px"><b>状态码</b> — HTTP 响应状态码必须等于设定值</div>
                    <div><b>响应体大小</b> — 响应体字节数不能超过设定值（0=不限）</div>
                    <div><b>JSONPath</b> — 从响应体提取值，必须等于「JSONPath期望值」</div>
                    <div><b>期望文本</b> — 响应体必须包含此文本</div>
                    <div><b>正则表达式</b> — 响应体必须匹配此正则</div>
                    <div style="margin-top: 4px;color:#909399">以上条件可任意组合，全部满足才算成功</div>
                  </div>
                </template>
                <el-icon class="section-block__help"><QuestionFilled /></el-icon>
              </el-tooltip>
            </div>
            <div class="section-block__body">
              <el-row :gutter="12">
                <el-col :span="12">
                  <el-form-item label="状态码">
                    <el-input-number v-model="form.expectedStatus" :min="100" :max="599" style="width: 150px" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="响应体大小">
                    <el-input-number v-model="form.maxResponseBodySize" :min="0" :step="1024" style="width: 150px" />
                    <span style="color: #909399; font-size: 12px; margin-left: 4px">B, 0=不限</span>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="12">
                <el-col :span="12">
                  <el-form-item>
                    <template #label>
                      <span>JSONPath</span>
                      <el-tooltip placement="top" :width="360">
                        <template #content>
                          <div style="line-height: 1.8">
                            <div>从 JSON 响应体中提取值，使用 <b>JSONPath</b> 表达式</div>
                            <div style="margin-top: 4px"><b>常用语法:</b></div>
                            <div><code>$.data</code> — 根节点下的 data 字段</div>
                            <div><code>$.data.token</code> — 嵌套取值</div>
                            <div><code>$.data.items[0].id</code> — 数组取第一个元素</div>
                            <div><code>$.data.items[*].name</code> — 数组所有元素的 name</div>
                            <div style="margin-top: 4px"><b>配合使用:</b></div>
                            <div>提取值后可在「JSONPath期望值」中填写期望值或变量进行比对</div>
                          </div>
                        </template>
                        <el-icon class="label-help-icon"><QuestionFilled /></el-icon>
                      </el-tooltip>
                    </template>
                    <el-input v-model="form.jsonPath" placeholder="$.data.token" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item>
                    <template #label>
                      <span>JSONPath期望值</span>
                      <el-tooltip placement="top" :width="280">
                        <template #content>
                          <div style="line-height: 1.8">
                            <div>可使用变量进行动态比对:</div>
                            <div><code>&#123;&#123;group.expectedToken&#125;&#125;</code></div>
                            <div><code>&#123;&#123;global.userId&#125;&#125;</code></div>
                            <div style="margin-top: 4px">先从登录响应提取值，再在后续请求中比对</div>
                          </div>
                        </template>
                        <el-icon class="label-help-icon"><QuestionFilled /></el-icon>
                      </el-tooltip>
                    </template>
                    <el-input v-model="form.jsonExpected" placeholder="支持变量" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="12">
                <el-col :span="12">
                  <el-form-item>
                    <template #label>
                      <span>期望文本</span>
                      <el-tooltip placement="top" :width="280">
                        <template #content>
                          <div style="line-height: 1.8">
                            <div>可使用变量作为期望文本:</div>
                            <div><code>&#123;&#123;group.loginSuccess&#125;&#125;</code></div>
                            <div style="margin-top: 4px">先从登录响应提取成功标识，再在后续请求中验证</div>
                          </div>
                        </template>
                        <el-icon class="label-help-icon"><QuestionFilled /></el-icon>
                      </el-tooltip>
                    </template>
                    <el-input v-model="form.expectedText" placeholder="响应体包含此文本" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item>
                    <template #label>
                      <span>正则表达式</span>
                      <el-tooltip placement="top" :width="360">
                        <template #content>
                          <div style="line-height: 1.8">
                            <div>用正则表达式匹配<b>响应体内容</b>，匹配成功则监控通过</div>
                            <div style="margin-top: 4px"><b>判断逻辑:</b></div>
                            <div>• 设置了正则 → 响应体必须匹配该正则，否则告警</div>
                            <div>• 未设置正则 → 跳过正则校验</div>
                            <div style="margin-top: 4px"><b>示例:</b></div>
                            <div><code>^\d&#123;4&#125;-\d&#123;2&#125;-\d&#123;2&#125;$</code> — 验证返回日期格式</div>
                            <div><code>"code":\s*0</code> — 验证返回码为0</div>
                            <div><code>success|ok|true</code> — 包含成功标识</div>
                          </div>
                        </template>
                        <el-icon class="label-help-icon"><QuestionFilled /></el-icon>
                      </el-tooltip>
                    </template>
                    <el-input v-model="form.expectedRegex" placeholder="如: ^\d{4}-\d{2}-\d{2}$" />
                  </el-form-item>
                </el-col>
              </el-row>
            </div>
          </div>
          <!-- 监控项告警区 -->
          <el-form-item label="监控项告警">
            <el-switch v-model="form.alertEnabled" />
            <span style="margin-left: 8px; font-size: 12px; color: #909399">连续失败达到阈值后触发告警</span>
          </el-form-item>
          <template v-if="form.alertEnabled">
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="连续失败">
                  <div style="display: flex; gap: 8px; align-items: center">
                    <el-input-number v-model="form.alertConsecutiveCount" :min="1" :max="100" style="flex: 1" />
                    <span style="color: #909399">次后告警</span>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="告警通道">
                  <el-select v-model="form.alertConfigIds" multiple placeholder="选择告警通道" style="width: 100%">
                    <el-option v-for="c in allAlertConfigs" :key="c.id" :label="c.name" :value="String(c.id)">
                      <span>{{ c.name }}</span>
                      <el-tag :type="c.enabled ? 'success' : 'info'" size="small" style="margin-left: 8px; float: right">
                        {{ c.enabled ? '启用' : '禁用' }}
                      </el-tag>
                    </el-option>
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </template>

          <!-- 行7: 响应时间告警区 -->
          <el-form-item label="响应时间告警">
            <el-switch v-model="form.responseTimeAlertEnabled" />
          </el-form-item>
          <template v-if="form.responseTimeAlertEnabled">
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="阈值 / 次数">
                  <div style="display: flex; gap: 8px; width: 100%">
                    <el-input-number v-model="form.responseTimeThreshold" :min="1" placeholder="阈值ms" style="flex: 1" />
                    <span style="line-height: 32px; color: #909399">ms /</span>
                    <el-input-number v-model="form.responseTimeConsecutiveCount" :min="1" :max="100" style="flex: 1" />
                    <span style="line-height: 32px; color: #909399">次</span>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="告警通道">
                  <el-select v-model="form.responseTimeAlertConfigIds" multiple placeholder="选择告警通道" style="width: 100%">
                    <el-option v-for="c in allAlertConfigs" :key="c.id" :label="c.name" :value="String(c.id)">
                      <span>{{ c.name }}</span>
                      <el-tag :type="c.enabled ? 'success' : 'info'" size="small" style="margin-left: 8px; float: right">
                        {{ c.enabled ? '启用' : '禁用' }}
                      </el-tag>
                    </el-option>
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </template>

          <!-- API Schema 告警区 -->
          <el-form-item label="API Schema告警">
            <el-switch v-model="form.schemaAlertEnabled" />
          </el-form-item>
          <template v-if="form.schemaAlertEnabled">
            <el-row :gutter="12">
              <el-col :span="24">
                <el-form-item label="期望Schema">
                  <div style="width: 100%">
                    <el-select v-model="selectedSchemaId" clearable placeholder="从 Schema 列表选择（自动填充）" style="width: 100%; margin-bottom: 8px" @change="onSchemaSelect">
                      <el-option v-for="s in schemaOptions" :key="s.id" :label="s.name" :value="s.id" />
                    </el-select>
                    <JsonSchemaEditor ref="monitorSchemaEditorRef" v-model="form.expectedSchemaJson" />
                    <div v-if="monitorSchemaValidationError" class="schema-validation-error">
                      <el-icon><WarningFilled /></el-icon> {{ monitorSchemaValidationError }}
                    </div>
                  </div>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="变更类型">
                  <el-checkbox-group v-model="form.schemaAlertChangeTypes">
                    <el-checkbox value="ADDED">新增</el-checkbox>
                    <el-checkbox value="REMOVED">删除</el-checkbox>
                    <el-checkbox value="MODIFIED">修改</el-checkbox>
                    <el-checkbox value="BREAKING">破坏性</el-checkbox>
                  </el-checkbox-group>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="告警通道">
                  <el-select v-model="form.schemaAlertChannelIds" multiple placeholder="选择告警通道" style="width: 100%">
                    <el-option v-for="c in allAlertConfigs" :key="c.id" :label="c.name" :value="String(c.id)">
                      <span>{{ c.name }}</span>
                      <el-tag :type="c.enabled ? 'success' : 'info'" size="small" style="margin-left: 8px; float: right">
                        {{ c.enabled ? '启用' : '禁用' }}
                      </el-tag>
                    </el-option>
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </template>

        </el-tab-pane>

        <!-- Tab 2: API签名 & 预请求脚本 -->
        <el-tab-pane label="更多配置项" name="sign">
          <!-- 变量赋值区块 -->
          <div class="section-block section-block--variable">
            <div class="section-block__header">
              <span class="section-block__title">变量赋值</span>
              <el-tooltip placement="right" :width="400">
                <template #content>
                  <div style="line-height: 1.8">
                    <div><b>从响应中提取值</b>并写入变量，供后续请求使用。</div>
                    <div style="margin-top: 6px"><b>三种来源示例:</b></div>
                    <div style="margin-top: 2px"><b>响应体</b> — 选择「响应体」，填写 JSONPath:</div>
                    <div><code>$.data.token</code> → 提取 token 字段</div>
                    <div><code>$.list[0].id</code> → 提取数组第一项的 id</div>
                    <div style="margin-top: 4px"><b>响应头</b> — 选择「响应头」，填写 Header 名:</div>
                    <div><code>x-token</code> → 提取响应头 x-token 的值</div>
                    <div><code>Set-Cookie</code> → 提取完整 Cookie 字符串</div>
                    <div style="margin-top: 4px"><b>Cookie</b> — 选择「Cookie」，填写 Cookie 名:</div>
                    <div><code>session_id</code> → 提取指定 Cookie 的值</div>
                    <div style="margin-top: 6px;color:#909399">变量名格式: <code>global.xxx</code> 或 <code>group.xxx</code></div>
                  </div>
                </template>
                <el-icon class="section-block__help"><QuestionFilled /></el-icon>
              </el-tooltip>
            </div>
            <div class="section-block__body">
              <div v-for="(item, index) in variableExtractList" :key="index" class="variable-extract-item">
                <el-select v-model="item.name" filterable allow-create placeholder="选择变量" style="width: 170px">
                  <el-option-group label="全局变量">
                    <el-option v-for="v in allGlobalVars" :key="'global.' + v.name" :label="'{{global.' + v.name + '}}'" :value="'global.' + v.name" />
                  </el-option-group>
                  <el-option-group label="任务变量">
                    <el-option v-for="v in allGroupVars" :key="'group.' + v.name" :label="'{{group.' + v.name + '}}'" :value="'group.' + v.name" />
                  </el-option-group>
                </el-select>
                <el-select v-model="item.source" style="width: 90px">
                  <el-option label="响应体" value="body" />
                  <el-option label="响应头" value="header" />
                  <el-option label="Cookie" value="cookie" />
                </el-select>
                <el-input v-model="item.jsonPath" :placeholder="item.source === 'body' ? 'JSONPath' : item.source === 'header' ? 'Header名' : 'Cookie名'" style="width: 160px" />
                <el-input v-model="item.defaultValue" placeholder="默认值" style="width: 90px" />
                <el-button type="danger" link @click="removeVariableExtract(index)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <el-button type="primary" link @click="addVariableExtract">
                <el-icon><Plus /></el-icon>
                添加变量赋值
              </el-button>
            </div>
          </div>

          <el-divider content-position="left" style="margin: 26px 0 12px">
            API签名 & 预请求脚本
            <el-tooltip placement="right" :width="400">
              <template #content>
                <div style="line-height: 1.8">
                  <div><b>左侧</b>：签名配置表单，选择签名类型后自动在右侧生成脚本</div>
                  <div><b>右侧</b>：预请求脚本（可直接编辑），保存时仅存储脚本内容</div>
                  <div style="margin-top: 6px"><b>变量支持</b>：脚本中可使用变量（变量替换在脚本执行之前完成）</div>
                </div>
              </template>
              <el-icon style="margin-left: 6px; cursor: pointer; color: #909399; vertical-align: middle;"><QuestionFilled /></el-icon>
            </el-tooltip>
          </el-divider>
          <el-row :gutter="16">
            <!-- 左: 签名配置 -->
            <el-col :span="10">
              <div class="sign-panel">
                <div class="sign-panel-title">签名配置 <span class="sign-panel-subtitle">(自动生成右侧脚本)</span></div>
                <el-form-item label="签名类型">
                  <el-select v-model="form.signType" style="width: 100%" @change="onSignTypeChange">
                    <el-option label="不签名" value="NONE" />
                    <el-option label="MD5 签名 (微信支付V2等)" value="MD5_SIGN" />
                    <el-option label="HMAC-SHA256 (AWS/Stripe等)" value="HMAC_SHA256" />
                    <el-option label="RSA-SHA256 (微信支付V3等)" value="RSA_SHA256" />
                  </el-select>
                </el-form-item>
                <template v-if="form.signType && form.signType !== 'NONE'">
                  <el-form-item label="签名注入位置">
                    <el-radio-group v-model="form.signTarget" @change="generateScriptFromConfig">
                      <el-radio value="HEADER">Header</el-radio>
                      <el-radio value="PARAMS">URL参数</el-radio>
                      <el-radio value="BODY">请求体</el-radio>
                    </el-radio-group>
                  </el-form-item>
                  <el-form-item label="签名字段名">
                    <el-input v-model="form.signFieldName" placeholder="如 sign / X-Signature" style="width: 100%" @input="generateScriptFromConfig" />
                  </el-form-item>

                  <!-- MD5_SIGN -->
                  <template v-if="form.signType === 'MD5_SIGN'">
                    <el-form-item label="密钥">
                      <el-input v-model="signConfigForm.secretKey" placeholder="签名密钥 (支持变量)" show-password @input="generateScriptFromConfig" />
                    </el-form-item>
                    <el-form-item label="包含时间戳">
                      <el-switch v-model="signConfigForm.includeTimestamp" @change="generateScriptFromConfig" />
                    </el-form-item>
                    <el-form-item v-if="signConfigForm.includeTimestamp" label="时间戳参数名">
                      <el-input v-model="signConfigForm.timestampParam" placeholder="timestamp" style="width: 160px" @input="generateScriptFromConfig" />
                    </el-form-item>
                    <el-form-item label="签名转大写">
                      <el-switch v-model="signConfigForm.uppercaseSign" @change="generateScriptFromConfig" />
                      <span style="margin-left:8px;color:#909399;font-size:12px">开启后签名转为全大写</span>
                    </el-form-item>
                    <el-form-item label="排除字段">
                      <el-input v-model="signConfigForm.excludeKeys" placeholder="逗号分隔: sign,empty" @input="generateScriptFromConfig" />
                    </el-form-item>
                  </template>

                  <!-- HMAC_SHA256 -->
                  <template v-if="form.signType === 'HMAC_SHA256'">
                    <el-form-item label="密钥">
                      <el-input v-model="signConfigForm.secretKey" placeholder="签名密钥 (支持变量)" show-password @input="generateScriptFromConfig" />
                    </el-form-item>
                    <el-form-item label="签名风格">
                      <el-select v-model="signConfigForm.canonicalStyle" style="width: 100%" @change="generateScriptFromConfig">
                        <el-option label="排序拼接 (key=value&...)" value="sorted" />
                        <el-option label="AWS 风格 (换行分隔)" value="aws" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="签名编码">
                      <el-radio-group v-model="signConfigForm.signEncoding" @change="generateScriptFromConfig">
                        <el-radio value="base64">Base64</el-radio>
                        <el-radio value="hex">Hex</el-radio>
                      </el-radio-group>
                    </el-form-item>
                    <el-form-item label="包含时间戳">
                      <el-switch v-model="signConfigForm.includeTimestamp" @change="generateScriptFromConfig" />
                    </el-form-item>
                    <el-form-item label="签名转大写">
                      <el-switch v-model="signConfigForm.uppercaseSign" @change="generateScriptFromConfig" />
                      <span style="margin-left:8px;color:#909399;font-size:12px">开启后签名转为全大写</span>
                    </el-form-item>
                  </template>

                  <!-- RSA_SHA256 -->
                  <template v-if="form.signType === 'RSA_SHA256'">
                    <el-form-item label="私钥 (PEM)">
                      <el-input v-model="signConfigForm.privateKey" type="textarea" :rows="3" placeholder="-----BEGIN PRIVATE KEY-----" class="body-editor" @input="onPrivateKeyInput" />
                    </el-form-item>
                    <el-form-item label="签名编码">
                      <el-radio-group v-model="signConfigForm.signEncoding" @change="generateScriptFromConfig">
                        <el-radio value="base64">Base64</el-radio>
                        <el-radio value="hex">Hex</el-radio>
                      </el-radio-group>
                    </el-form-item>
                    <el-form-item label="签名转大写">
                      <el-switch v-model="signConfigForm.uppercaseSign" @change="generateScriptFromConfig" />
                      <span style="margin-left:8px;color:#909399;font-size:12px">开启后签名转为全大写</span>
                    </el-form-item>
                  </template>
                </template>
              </div>
            </el-col>
            <!-- 右: 预请求脚本 -->
            <el-col :span="14">
              <div class="sign-panel">
                <div class="sign-panel-title">预请求脚本 <span class="sign-panel-subtitle">(可直接编辑，保存时仅存储此内容)</span></div>
                <el-input v-model="form.preRequestScript" type="textarea" :rows="16" placeholder="// 选择左侧签名类型自动生成，也可直接编写&#10;// 可用对象: request, crypto, util&#10;// 变量在脚本执行前已替换&#10;&#10;request.headers['X-Timestamp'] = util.timestamp();&#10;request.headers['X-Sign'] = crypto.hmacSha256(request.body, 'key');" class="body-editor script-editor" />
                <div class="script-reference">
                  <div class="script-ref-title">可用对象</div>
                  <div class="script-ref-row"><span class="script-ref-obj">request</span> <span class="script-ref-desc">method / url / headers / params / body</span></div>
                  <div class="script-ref-row"><span class="script-ref-obj">crypto</span> <span class="script-ref-desc">md5 / sha256 / hmacSha256 / hmacSha256Base64 / rsaSha256Sign / rsaSha256SignHex / base64 / sha1 / hmacMd5</span></div>
                  <div class="script-ref-row"><span class="script-ref-obj">util</span> <span class="script-ref-desc">timestamp / timestampS / datetime / time / date / nonce / uuid / uuidShort / random</span></div>
                  <div class="script-ref-row"><span class="script-ref-obj">变量</span> <span class="script-ref-desc"><code v-text="'{{global.xxx}}'"></code> <code v-text="'{{group.xxx}}'"></code> <code v-text="'{{env.timestamp}}'"></code> — 脚本执行前已替换</span></div>
                </div>
              </div>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="submitForm" :loading="submitting"
        v-permission="editingRow ? 'monitor:edit' : 'monitor:create'">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { monitorApi, variableApi, alertChannelApi, schemaApi } from '@/api'
import JsonSchemaEditor from '@/components/JsonSchemaEditor.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  editingRow: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const submitting = ref(false)
const formRef = ref(null)
const activeDialogTab = ref('basic')
const editingId = ref(null)
const allGlobalVars = ref([])
const allGroupVars = ref([])
const allAlertConfigs = ref([])
const formParams = ref([])
const selectedSchemaId = ref(null)
const schemaOptions = ref([])
const monitorSchemaEditorRef = ref(null)
const monitorSchemaValidationError = ref('')
const variableExtractList = ref([])

const form = reactive({
  name: '',
  url: '',
  method: 'GET',
  headers: '',
  body: '',
  bodyType: 'json',
  expectedStatus: 200,
  expectedText: '',
  expectedRegex: '',
  jsonPath: '',
  jsonExpected: '',
  timeout: 30,
  enabled: true,
  maxResponseBodySize: 0,
  variableExtractConfig: '',
  responseTimeThreshold: null,
  responseTimeConsecutiveCount: 1,
  responseTimeAlertEnabled: false,
  responseTimeAlertConfigIds: '',
  signType: 'NONE',
  signTarget: 'HEADER',
  signFieldName: 'sign',
  signConfig: '',
  preRequestScript: '',
  schemaAlertEnabled: false,
  expectedSchemaJson: '',
  showOnStatusPage: false,
  schemaAlertChangeTypes: ['ADDED', 'REMOVED', 'MODIFIED', 'BREAKING'],
  schemaAlertChannelIds: '',
  alertEnabled: false,
  alertConsecutiveCount: 3,
  alertConfigIds: ''
})

const signConfigForm = reactive({
  secretKey: '',
  timestampParam: 'timestamp',
  includeTimestamp: true,
  excludeKeys: '',
  canonicalStyle: 'aws',
  signEncoding: 'base64',
  uppercaseSign: false,
  privateKey: ''
})

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  url: [{ required: true, message: '请输入URL', trigger: 'blur' }]
}

const BODY_TYPE_CONTENT_TYPES = {
  json: 'application/json',
  form: 'application/x-www-form-urlencoded',
  xml: 'application/xml',
  text: 'text/plain'
}
const AUTO_CONTENT_TYPES = ['application/json', 'application/x-www-form-urlencoded', 'application/xml', 'text/xml', 'text/plain']

const loadAllVariables = async () => {
  try {
    const [gRes, grRes] = await Promise.all([
      variableApi.listAllGlobal(),
      variableApi.listAllGroup()
    ])
    allGlobalVars.value = gRes.data || []
    allGroupVars.value = grRes.data || []
  } catch (error) {
    console.error('Failed to load variables:', error)
  }
}

const loadAlertConfigs = async () => {
  try {
    const res = await alertChannelApi.list()
    allAlertConfigs.value = res.data || []
  } catch (error) {
    console.error('Failed to load alert channels:', error)
  }
}

const loadSchemas = async () => {
  try {
    const res = await schemaApi.listAll()
    schemaOptions.value = res.data || []
  } catch (e) {
    console.error(e)
  }
}

const onSchemaSelect = (schemaId) => {
  if (!schemaId) return
  const schema = schemaOptions.value.find(s => s.id === schemaId)
  if (schema && schema.schemaJson) {
    form.expectedSchemaJson = schema.schemaJson
    monitorSchemaValidationError.value = ''
  }
}

function validateMonitorSchema(obj) {
  const errors = []
  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) {
    errors.push('根节点必须是对象'); return errors
  }
  const validTypes = ['object', 'array', 'string', 'integer', 'number', 'boolean', 'null']
  if (!obj.type) errors.push('必须定义 "type" 字段')
  else if (!validTypes.includes(obj.type)) errors.push('"type" 值无效，允许: ' + validTypes.join(', '))
  if (obj.required && !Array.isArray(obj.required)) errors.push('"required" 必须是数组')
  if (obj.properties && (typeof obj.properties !== 'object' || Array.isArray(obj.properties)))
    errors.push('"properties" 必须是对象')
  if (obj.type === 'object' && obj.properties) {
    Object.entries(obj.properties).forEach(([name, schema]) => {
      if (!name.trim()) errors.push('字段名不能为空')
      if (typeof schema !== 'object' || schema === null)
        errors.push('字段 "' + name + '" 的 schema 必须是对象')
      else validateMonitorSchema(schema).forEach(e => errors.push(name + '.' + e))
    })
  }
  if (obj.type === 'array' && obj.items) {
    if (typeof obj.items !== 'object') errors.push('items 必须是对象')
    else validateMonitorSchema(obj.items).forEach(e => errors.push('items.' + e))
  }
  return errors
}

watch(() => form.responseTimeAlertEnabled, (val) => {
  if (!val) {
    form.responseTimeThreshold = null
    form.responseTimeConsecutiveCount = 1
    form.responseTimeAlertConfigIds = ''
  }
})

watch(() => form.method, (val) => {
  if (val !== 'POST' && val !== 'PUT') {
    form.bodyType = 'none'
  } else if (!form.bodyType || form.bodyType === 'none') {
    form.bodyType = 'json'
  }
})

watch(() => signConfigForm.privateKey, () => {
  if (form.signType === 'RSA_SHA256') {
    generateScriptFromConfig()
  }
})

let privateKeyTimer = null
const onPrivateKeyInput = () => {
  clearTimeout(privateKeyTimer)
  privateKeyTimer = setTimeout(() => {
    if (form.signType === 'RSA_SHA256') {
      generateScriptFromConfig()
    }
  }, 300)
}

const onSignTypeChange = () => {
  if (form.signType === 'NONE') {
    form.signTarget = 'HEADER'
    form.signFieldName = 'sign'
    form.signConfig = ''
    Object.assign(signConfigForm, {
      secretKey: '', timestampParam: 'timestamp', includeTimestamp: true,
      excludeKeys: '', canonicalStyle: 'aws', signEncoding: 'base64', uppercaseSign: false, privateKey: ''
    })
    form.preRequestScript = ''
  } else {
    generateScriptFromConfig()
  }
}

const generateScriptFromConfig = () => {
  if (!form.signType || form.signType === 'NONE') {
    form.preRequestScript = ''
    return
  }
  const field = form.signFieldName || 'sign'
  const target = form.signTarget || 'HEADER'
  const upper = signConfigForm.uppercaseSign ? '.toUpperCase()' : ''
  const lines = []
  if (form.signType === 'MD5_SIGN') {
    const secret = signConfigForm.secretKey || 'your_secret_key'
    const excludeKeys = signConfigForm.excludeKeys || ''
    lines.push('// MD5 签名 (微信支付V2等)')
    lines.push(`const params = { ...request.params };`)
    if (signConfigForm.includeTimestamp) {
      lines.push(`params['${signConfigForm.timestampParam || 'timestamp'}'] = util.timestamp();`)
    }
    if (excludeKeys) {
      excludeKeys.split(',').map(s => s.trim()).filter(Boolean).forEach(k => {
        lines.push(`delete params['${k}'];`)
      })
    }
    lines.push(`const sorted = Object.keys(params).sort().map(k => k + '=' + params[k]).join('&');`)
    lines.push(`const signStr = sorted + '&key=${secret}';`)
    lines.push(`const sign = crypto.md5(signStr)${upper};`)
    if (target === 'HEADER') {
      lines.push(`request.headers['${field}'] = sign;`)
    } else if (target === 'PARAMS') {
      lines.push(`request.params['${field}'] = sign;`)
    } else if (target === 'BODY') {
      lines.push(`request.body = request.body ? request.body + '&${field}=' + sign : '${field}=' + sign;`)
    }
  } else if (form.signType === 'HMAC_SHA256') {
    const secret = signConfigForm.secretKey || 'your_secret_key'
    const style = signConfigForm.canonicalStyle || 'sorted'
    const encoding = signConfigForm.signEncoding || 'base64'
    lines.push('// HMAC-SHA256 签名 (AWS/Stripe等)')
    if (style === 'aws') {
      lines.push(`const body = request.body || '';`)
      if (signConfigForm.includeTimestamp) {
        lines.push(`const timestamp = util.timestamp();`)
        lines.push(`const signStr = timestamp + body;`)
      } else {
        lines.push(`const signStr = body;`)
      }
      if (encoding === 'base64') {
        lines.push(`const sign = crypto.hmacSha256Base64(signStr, '${secret}')${upper};`)
      } else {
        lines.push(`const sign = crypto.hmacSha256(signStr, '${secret}')${upper};`)
      }
      if (target === 'HEADER') {
        lines.push(`request.headers['${field}'] = sign;`)
        if (signConfigForm.includeTimestamp) lines.push(`request.headers['X-Timestamp'] = timestamp;`)
      } else if (target === 'PARAMS') {
        lines.push(`request.params['${field}'] = sign;`)
        if (signConfigForm.includeTimestamp) lines.push(`request.params['X-Timestamp'] = timestamp;`)
      } else if (target === 'BODY') {
        lines.push(`const bodyObj = JSON.parse(request.body || '{}');`)
        lines.push(`bodyObj['${field}'] = sign;`)
        if (signConfigForm.includeTimestamp) lines.push(`bodyObj['X-Timestamp'] = timestamp;`)
        lines.push(`request.body = JSON.stringify(bodyObj);`)
      }
    } else {
      lines.push(`const params = { ...request.params };`)
      if (signConfigForm.includeTimestamp) {
        lines.push(`params['${signConfigForm.timestampParam || 'timestamp'}'] = util.timestamp();`)
      }
      lines.push(`const sorted = Object.keys(params).sort().map(k => k + '=' + params[k]).join('&');`)
      if (encoding === 'base64') {
        lines.push(`const sign = crypto.hmacSha256Base64(sorted, '${secret}')${upper};`)
      } else {
        lines.push(`const sign = crypto.hmacSha256(sorted, '${secret}')${upper};`)
      }
      if (target === 'HEADER') {
        lines.push(`request.headers['${field}'] = sign;`)
      } else if (target === 'PARAMS') {
        lines.push(`request.params['${field}'] = sign;`)
      } else if (target === 'BODY') {
        lines.push(`request.body = request.body ? request.body + '&${field}=' + sign : '${field}=' + sign;`)
      }
    }
  } else if (form.signType === 'RSA_SHA256') {
    const encoding = signConfigForm.signEncoding || 'base64'
    const pk = signConfigForm.privateKey || 'YOUR_PRIVATE_KEY'
    lines.push('// RSA-SHA256 签名 (微信支付V3等)')
    lines.push(`const privateKey = \`${pk}\`;`)
    lines.push(`const timestamp = util.timestamp();`)
    lines.push(`const nonce = util.nonce();`)
    lines.push(`const body = request.body || '';`)
    lines.push(`const message = timestamp + '\\n' + nonce + '\\n' + body + '\\n';`)
    if (encoding === 'base64') {
      lines.push(`const sign = crypto.rsaSha256Sign(message, privateKey)${upper};`)
    } else {
      lines.push(`const sign = crypto.rsaSha256SignHex(message, privateKey)${upper};`)
    }
    if (target === 'HEADER') {
      lines.push(`request.headers['${field}'] = sign;`)
      lines.push(`request.headers['X-Timestamp'] = timestamp;`)
      lines.push(`request.headers['X-Nonce'] = nonce;`)
    } else if (target === 'PARAMS') {
      lines.push(`request.params['${field}'] = sign;`)
      lines.push(`request.params['X-Timestamp'] = timestamp;`)
      lines.push(`request.params['X-Nonce'] = nonce;`)
    } else if (target === 'BODY') {
      lines.push(`const bodyObj = JSON.parse(request.body || '{}');`)
      lines.push(`bodyObj['${field}'] = sign;`)
      lines.push(`bodyObj['X-Timestamp'] = timestamp;`)
      lines.push(`bodyObj['X-Nonce'] = nonce;`)
      lines.push(`request.body = JSON.stringify(bodyObj);`)
    }
  }
  form.preRequestScript = lines.join('\n')
}

const addVariableExtract = () => {
  variableExtractList.value.push({ name: '', source: 'body', jsonPath: '', defaultValue: '' })
}

const removeVariableExtract = (index) => {
  variableExtractList.value.splice(index, 1)
}

const syncContentTypeHeader = (bodyType) => {
  const target = BODY_TYPE_CONTENT_TYPES[bodyType]
  let headersObj = {}
  if (form.headers && form.headers.trim()) {
    try {
      headersObj = JSON.parse(form.headers)
    } catch {
      return
    }
  }
  const key = Object.keys(headersObj).find(k => k.toLowerCase() === 'content-type')
  const current = key ? String(headersObj[key]).split(';')[0].trim().toLowerCase() : null
  if (target) {
    if (key) {
      if (current && AUTO_CONTENT_TYPES.includes(current)) headersObj[key] = target
    } else {
      headersObj['Content-Type'] = target
    }
  } else if (key && current && AUTO_CONTENT_TYPES.includes(current)) {
    delete headersObj[key]
  }
  form.headers = Object.keys(headersObj).length ? JSON.stringify(headersObj) : ''
}

const onBodyTypeChange = (type) => {
  form.bodyType = type
  syncContentTypeHeader(type)
  if (type === 'form') {
    if (form.body) {
      try {
        const obj = JSON.parse(form.body)
        formParams.value = Object.entries(obj).map(([key, value]) => ({ key, value: String(value) }))
      } catch {
        formParams.value = [{ key: '', value: '' }]
      }
    } else {
      formParams.value = [{ key: '', value: '' }]
    }
  } else if (type === 'json') {
    if (formParams.value.length > 0) {
      const obj = {}
      formParams.value.forEach(p => { if (p.key) obj[p.key] = p.value })
      form.body = JSON.stringify(obj, null, 2)
    }
  }
}

const addFormParam = () => {
  formParams.value.push({ key: '', value: '' })
}

const removeFormParam = (index) => {
  formParams.value.splice(index, 1)
}

const fillForm = (row) => {
  if (row) {
    editingId.value = row.id
    Object.assign(form, row)
    if (form.responseTimeAlertConfigIds && typeof form.responseTimeAlertConfigIds === 'string') {
      form.responseTimeAlertConfigIds = form.responseTimeAlertConfigIds.split(',').filter(Boolean)
    } else if (!form.responseTimeAlertConfigIds) {
      form.responseTimeAlertConfigIds = []
    }
    if (form.schemaAlertChangeTypes && typeof form.schemaAlertChangeTypes === 'string') {
      form.schemaAlertChangeTypes = form.schemaAlertChangeTypes.split(',').filter(Boolean)
    } else if (!form.schemaAlertChangeTypes) {
      form.schemaAlertChangeTypes = ['ADDED', 'REMOVED', 'MODIFIED', 'BREAKING']
    }
    if (form.schemaAlertChannelIds && typeof form.schemaAlertChannelIds === 'string') {
      form.schemaAlertChannelIds = form.schemaAlertChannelIds.split(',').filter(Boolean)
    } else if (!form.schemaAlertChannelIds) {
      form.schemaAlertChannelIds = []
    }
    if (form.alertConfigIds && typeof form.alertConfigIds === 'string') {
      form.alertConfigIds = form.alertConfigIds.split(',').filter(Boolean)
    } else if (!form.alertConfigIds) {
      form.alertConfigIds = []
    }
    selectedSchemaId.value = null
    if (row.signConfig) {
      try {
        const sc = JSON.parse(row.signConfig)
        Object.assign(signConfigForm, { uppercaseSign: false }, sc)
      } catch (e) {
        console.error('Failed to parse signConfig', e)
      }
    } else {
      Object.assign(signConfigForm, {
        secretKey: '', timestampParam: 'timestamp', includeTimestamp: true,
        excludeKeys: '', canonicalStyle: 'aws', signEncoding: 'base64', uppercaseSign: false, privateKey: ''
      })
    }
    variableExtractList.value = []
    if (row.variableExtractConfig) {
      try {
        const config = JSON.parse(row.variableExtractConfig)
        variableExtractList.value = Object.entries(config).map(([name, val]) => ({
          name,
          source: val.source || 'body',
          jsonPath: val.jsonPath || '',
          defaultValue: val.defaultValue || ''
        }))
      } catch (e) {
        console.error('Failed to parse variableExtractConfig', e)
      }
    }
    if (form.bodyType === 'form' && form.body) {
      try {
        const obj = JSON.parse(form.body)
        formParams.value = Object.entries(obj).map(([key, value]) => ({ key, value: String(value) }))
      } catch {
        formParams.value = [{ key: '', value: '' }]
      }
    } else {
      formParams.value = []
    }
  } else {
    editingId.value = null
    Object.assign(form, {
      id: null, name: '', url: '', method: 'GET', headers: '', body: '',
      bodyType: 'json', expectedStatus: 200, expectedText: '',
      expectedRegex: '', jsonPath: '', jsonExpected: '', timeout: 30, enabled: true,
      maxResponseBodySize: 0, variableExtractConfig: '',
      showOnStatusPage: false,
      responseTimeThreshold: null, responseTimeConsecutiveCount: 1,
      responseTimeAlertEnabled: false, responseTimeAlertConfigIds: '',
      signType: 'NONE', signTarget: 'HEADER', signFieldName: 'sign',
      signConfig: '', preRequestScript: '',
      schemaAlertEnabled: false, expectedSchemaJson: '',
      schemaAlertChangeTypes: ['ADDED', 'REMOVED', 'MODIFIED', 'BREAKING'],
      schemaAlertChannelIds: '',
      alertEnabled: false, alertConsecutiveCount: 3, alertConfigIds: ''
    })
    Object.assign(signConfigForm, {
      secretKey: '', timestampParam: 'timestamp', includeTimestamp: true,
      excludeKeys: '', canonicalStyle: 'aws', signEncoding: 'base64', uppercaseSign: false, privateKey: ''
    })
    formParams.value = []
    variableExtractList.value = []
  }
}

watch(() => props.modelValue, async (v) => {
  if (!v) return
  activeDialogTab.value = 'basic'
  monitorSchemaValidationError.value = ''
  await Promise.all([loadAllVariables(), loadAlertConfigs(), loadSchemas()])
  fillForm(props.editingRow)
})

const submitForm = async () => {
  // 刷新 Schema 可视化编辑器，避免 300ms 防抖导致最后一次编辑丢失
  monitorSchemaEditorRef.value?.flush?.()
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const extractConfig = {}
  variableExtractList.value.forEach(item => {
    if (item.name && item.jsonPath) {
      extractConfig[item.name] = {
        source: item.source || 'body',
        jsonPath: item.jsonPath,
        defaultValue: item.defaultValue || null
      }
    }
  })
  form.variableExtractConfig = Object.keys(extractConfig).length > 0 ? JSON.stringify(extractConfig) : ''

  if (Array.isArray(form.responseTimeAlertConfigIds)) {
    form.responseTimeAlertConfigIds = form.responseTimeAlertConfigIds.join(',')
  }
  if (Array.isArray(form.alertConfigIds)) {
    form.alertConfigIds = form.alertConfigIds.join(',')
  }
  if (Array.isArray(form.schemaAlertChangeTypes)) {
    form.schemaAlertChangeTypes = form.schemaAlertChangeTypes.join(',')
  }
  if (Array.isArray(form.schemaAlertChannelIds)) {
    form.schemaAlertChannelIds = form.schemaAlertChannelIds.join(',')
  }

  if (form.schemaAlertEnabled && form.expectedSchemaJson && form.expectedSchemaJson.trim()) {
    try {
      const obj = JSON.parse(form.expectedSchemaJson)
      const errors = validateMonitorSchema(obj)
      if (errors.length > 0) {
        monitorSchemaValidationError.value = errors[0]
        ElMessage.error('期望Schema 格式错误: ' + errors[0])
        return
      }
      monitorSchemaValidationError.value = ''
    } catch (e) {
      monitorSchemaValidationError.value = 'JSON 格式错误: ' + e.message
      ElMessage.error('期望Schema JSON 格式错误: ' + e.message)
      return
    }
  }

  if (form.signType && form.signType !== 'NONE') {
    form.signConfig = JSON.stringify(signConfigForm)
  } else {
    form.signConfig = ''
  }

  if (form.bodyType === 'form' && formParams.value.length > 0) {
    const obj = {}
    formParams.value.forEach(p => { if (p.key) obj[p.key] = p.value })
    form.body = Object.keys(obj).length > 0 ? JSON.stringify(obj) : ''
  }

  syncContentTypeHeader(form.bodyType)

  submitting.value = true
  try {
    const payload = { ...form, monitorType: 'HTTP' }
    delete payload.id
    delete payload.createdAt
    delete payload.updatedAt
    delete payload.createdBy
    delete payload.groupCount
    if (editingId.value) {
      await monitorApi.update(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await monitorApi.create(payload)
      ElMessage.success('创建成功')
    }
    emit('update:modelValue', false)
    emit('saved')
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.var-hint {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
  color: #909399;

  code {
    background: #f5f7fa;
    padding: 1px 6px;
    border-radius: 3px;
    color: #e6a23c;
    font-family: Consolas, Monaco, monospace;
  }

  .var-hint-icon { color: #409eff; }
  .var-hint-help { color: #909399; cursor: pointer; }
}

.var-hint-inline {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
  font-size: 11px;
  color: #909399;
  line-height: 1.4;

  code {
    background: #f5f7fa;
    padding: 0 4px;
    border-radius: 3px;
    color: #e6a23c;
    font-family: Consolas, Monaco, monospace;
    font-size: 11px;
  }

  .var-hint-icon { color: #409eff; font-size: 12px; }
  .var-hint-help { color: #909399; cursor: pointer; font-size: 12px; }
  .var-hint-label { white-space: nowrap; }
}

.label-help-icon {
  color: #909399;
  cursor: pointer;
  margin-left: 4px;
  font-size: 14px;
  position: relative;
  top: -1px;
}

:deep(.el-form-item__label) {
  display: inline-flex;
  align-items: center;
}

.section-block {
  margin: 16px 0;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;

  &__header {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 12px;
    background: #f5f7fa;
  }

  &__title { font-weight: 600; font-size: 13px; color: #303133; }
  &__help { color: #909399; cursor: pointer; }
  &__body { padding: 12px; }
}

.form-param-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.variable-extract-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.sign-panel {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
}

.sign-panel-title { font-weight: 600; margin-bottom: 12px; font-size: 13px; }
.sign-panel-subtitle { color: #909399; font-weight: 400; font-size: 12px; }

.script-reference {
  margin-top: 8px;
  font-size: 12px;
  color: #606266;
}

.script-ref-title { font-weight: 600; margin-bottom: 6px; }
.script-ref-row { margin-bottom: 4px; }
.script-ref-obj { color: #409eff; font-family: Consolas, Monaco, monospace; margin-right: 6px; }
.script-ref-desc { color: #909399; }

.schema-validation-error {
  color: #f56c6c;
  font-size: 12px;
  margin-top: 6px;
}

.body-editor :deep(textarea) {
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
}

.script-editor :deep(textarea) {
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
}
</style>
