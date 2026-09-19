<template>
  <div class="json-schema-editor">
    <div class="editor-toolbar">
      <div class="toolbar-left">
        <el-radio-group v-model="mode" size="small" @change="onModeChange">
          <el-radio-button value="visual">可视化编辑</el-radio-button>
          <el-radio-button value="json">JSON 编辑</el-radio-button>
        </el-radio-group>
      </div>
      <div class="toolbar-right">
        <el-dropdown v-if="mode === 'visual'" trigger="click" @command="applyTemplate" size="small">
          <el-button size="small">
            <el-icon><DocumentCopy /></el-icon> 快速添加 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="pagination">
                <div class="template-item">
                  <span class="template-name">分页响应</span>
                  <span class="template-desc">page, pageSize, total, data[]</span>
                </div>
              </el-dropdown-item>
              <el-dropdown-item command="restful">
                <div class="template-item">
                  <span class="template-name">RESTful 响应</span>
                  <span class="template-desc">code, message, data</span>
                </div>
              </el-dropdown-item>
              <el-dropdown-item command="user">
                <div class="template-item">
                  <span class="template-name">用户信息</span>
                  <span class="template-desc">id, name, email, phone</span>
                </div>
              </el-dropdown-item>
              <el-dropdown-item command="timestamps">
                <div class="template-item">
                  <span class="template-name">时间字段</span>
                  <span class="template-desc">created_at, updated_at</span>
                </div>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button v-if="mode === 'json'" size="small" @click="formatJson">格式化</el-button>
        <el-button size="small" @click="clearAll">清空</el-button>
      </div>
    </div>

    <div class="editor-body">
      <!-- Visual mode -->
      <div v-if="mode === 'visual'" class="visual-mode">
        <div class="root-type-bar">
          <span class="label">根类型</span>
          <el-select v-model="rootType" size="small" style="width: 120px" @change="syncVisualToJson">
            <el-option label="object" value="object" />
            <el-option label="array" value="array" />
            <el-option label="string" value="string" />
            <el-option label="integer" value="integer" />
            <el-option label="number" value="number" />
            <el-option label="boolean" value="boolean" />
            <el-option label="null" value="null" />
          </el-select>
          <span v-if="rootType === 'object'" class="field-count">{{ fields.length }} 个字段</span>
        </div>

        <div v-if="rootType === 'object'" class="fields-container">
          <div v-if="fields.length === 0" class="empty-root-fields">
            <el-icon class="empty-icon"><DocumentAdd /></el-icon>
            <p>尚未添加字段</p>
            <el-button size="small" type="primary" plain @click="addField">
              <el-icon><Plus /></el-icon> 添加第一个字段
            </el-button>
            <p class="empty-hint">或使用上方「快速添加」从模板创建</p>
          </div>
          <draggable
            v-else
            :list="fields"
            item-key="_uid"
            handle=".drag-handle"
            ghost-class="field-ghost"
            animation="200"
            @change="syncVisualToJson"
          >
            <template #item="{ element: field, index }">
              <div class="field-item">
                <SchemaFieldRow
                  :field="field"
                  :required-fields="rootRequired"
                  :depth="0"
                  @update="onFieldUpdate(index, $event)"
                  @remove="removeField(index)"
                  @toggle-required="toggleRequired(field.name)"
                />
              </div>
            </template>
          </draggable>
          <el-button size="small" type="primary" plain @click="addField" class="add-root-btn">
            <el-icon><Plus /></el-icon> 添加字段
          </el-button>
        </div>

        <div v-else-if="rootType === 'array'" class="array-items-section">
          <div class="section-label">数组元素类型</div>
          <div class="field-item">
            <SchemaFieldRow
              :field="arrayItems"
              :required-fields="[]"
              :depth="0"
              @update="Object.assign(arrayItems, $event); debouncedSync()"
              @remove="Object.assign(arrayItems, defaultField()); debouncedSync()"
            />
          </div>
        </div>

        <div v-else class="primitive-type-section">
          <div class="section-label">根节点为原始类型: <el-tag size="small">{{ rootType }}</el-tag></div>
        </div>
      </div>

      <!-- JSON mode -->
      <div v-if="mode === 'json'" class="json-mode">
        <el-input
          v-model="jsonText"
          type="textarea"
          :rows="16"
          :class="{ 'json-textarea-error': jsonError }"
          placeholder='{"type":"object","properties":{}}'
          @input="validateJsonText"
          spellcheck="false"
          style="font-family: 'Courier New', monospace; font-size: 13px"
        />
        <div v-if="jsonError" class="json-error-msg">
          <el-icon><WarningFilled /></el-icon> {{ jsonError }}
        </div>
        <div v-else-if="jsonText" class="json-valid-msg">
          <el-icon><SuccessFilled /></el-icon> JSON 格式正确
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { Plus, WarningFilled, SuccessFilled, DocumentCopy, ArrowDown, DocumentAdd } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import draggable from 'vuedraggable'
import SchemaFieldRow from './SchemaFieldRow.vue'

const props = defineProps({
  modelValue: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'validate'])

const mode = ref('visual')
const rootType = ref('object')
const rootRequired = ref([])
const fields = ref([])
let _fieldUid = 0
const arrayItems = ref(defaultField())
const jsonText = ref('')
const jsonError = ref('')

const templates = {
  pagination: {
    type: 'object',
    required: ['code', 'message', 'data'],
    properties: {
      code: { type: 'integer', description: '状态码' },
      message: { type: 'string', description: '提示信息' },
      data: {
        type: 'object',
        required: ['page', 'pageSize', 'total', 'list'],
        properties: {
          page: { type: 'integer', description: '当前页码' },
          pageSize: { type: 'integer', description: '每页条数' },
          total: { type: 'integer', description: '总记录数' },
          list: { type: 'array', items: { type: 'object', description: '数据项' }, description: '数据列表' }
        }
      }
    }
  },
  restful: {
    type: 'object',
    required: ['code', 'message', 'data'],
    properties: {
      code: { type: 'integer', description: '状态码 (0=成功)' },
      message: { type: 'string', description: '响应消息' },
      data: { type: 'object', description: '响应数据' }
    }
  },
  user: {
    type: 'object',
    required: ['id', 'name'],
    properties: {
      id: { type: 'integer', description: '用户ID' },
      name: { type: 'string', description: '用户名' },
      email: { type: 'string', description: '邮箱' },
      phone: { type: 'string', description: '手机号' }
    }
  },
  timestamps: {
    type: 'object',
    properties: {
      created_at: { type: 'string', description: '创建时间' },
      updated_at: { type: 'string', description: '更新时间' }
    }
  }
}

function defaultField() {
  return {
    _uid: ++_fieldUid,
    name: '', type: 'string', required: false, description: '',
    properties: [], items: null, enumValues: '', minimum: null, maximum: null
  }
}

function addField() {
  fields.value.push(defaultField())
  debouncedSync()
}

function removeField(index) {
  const name = fields.value[index].name
  fields.value.splice(index, 1)
  if (name) rootRequired.value = rootRequired.value.filter(n => n !== name)
  debouncedSync()
}

function toggleRequired(fieldName) {
  if (!fieldName) return
  const idx = rootRequired.value.indexOf(fieldName)
  if (idx >= 0) rootRequired.value.splice(idx, 1)
  else rootRequired.value.push(fieldName)
  debouncedSync()
}

let _syncTimer = null
function debouncedSync() {
  if (_syncTimer) clearTimeout(_syncTimer)
  _syncTimer = setTimeout(() => { syncVisualToJson() }, 300)
}

function onFieldUpdate(index, patch) {
  const field = fields.value[index]
  if (!field) return
  const oldName = field.name
  const newName = patch.name
  if (oldName && oldName !== newName) {
    const rIdx = rootRequired.value.indexOf(oldName)
    if (rIdx >= 0) rootRequired.value[rIdx] = newName
  }
  Object.assign(field, patch)
  debouncedSync()
}

function applyTemplate(command) {
  const tpl = templates[command]
  if (!tpl) return
  const result = jsonToVisual(tpl)
  rootType.value = result.rootType
  fields.value = result.fields
  rootRequired.value = result.rootRequired || []
  if (result.items) arrayItems.value = result.items
  syncVisualToJson()
  ElMessage.success('模板已应用')
}

// JSON -> Visual
function jsonToVisual(obj) {
  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) {
    return { rootType: 'object', fields: [], rootRequired: [] }
  }
  const type = obj.type || 'object'
  if (type === 'array') {
    return {
      rootType: 'array', fields: [], rootRequired: [],
      items: obj.items ? parseFieldNode('', obj.items) : defaultField()
    }
  }
  if (type !== 'object') {
    return { rootType: type, fields: [], rootRequired: [] }
  }
  const requiredArr = obj.required || []
  const fieldList = []
  if (obj.properties) {
    Object.entries(obj.properties).forEach(([name, schema]) => {
      const f = parseFieldNode(name, schema)
      f.required = requiredArr.includes(name)
      fieldList.push(f)
    })
  }
  return { rootType: 'object', fields: fieldList, rootRequired: [...requiredArr] }
}

function parseFieldNode(name, schema) {
  if (!schema || typeof schema !== 'object') return { ...defaultField(), name }
  const type = schema.type || 'string'
  const field = {
    _uid: ++_fieldUid,
    name, type, required: false,
    description: schema.description || '',
    properties: [], items: null,
    enumValues: Array.isArray(schema.enum) ? schema.enum.join(', ') : '',
    minimum: schema.minimum !== undefined ? schema.minimum : null,
    maximum: schema.maximum !== undefined ? schema.maximum : null
  }
  if (type === 'object' && schema.properties) {
    const reqArr = schema.required || []
    Object.entries(schema.properties).forEach(([k, v]) => {
      const sub = parseFieldNode(k, v)
      sub.required = reqArr.includes(k)
      field.properties.push(sub)
    })
  } else if (type === 'array' && schema.items) {
    field.items = parseFieldNode('', schema.items)
  }
  return field
}

// Visual -> JSON
function visualToJson() {
  const obj = buildRootSchema()
  return JSON.stringify(obj, null, 2)
}

function buildRootSchema() {
  if (rootType.value === 'object') {
    const properties = {}
    const required = rootRequired.value.filter(name => fields.value.some(f => f.name === name))
    fields.value.forEach(f => {
      if (f.name) properties[f.name] = buildFieldSchema(f)
    })
    const schema = { type: 'object' }
    if (required.length > 0) schema.required = required
    if (Object.keys(properties).length > 0) schema.properties = properties
    return schema
  } else if (rootType.value === 'array') {
    return { type: 'array', items: buildFieldSchema(arrayItems.value) }
  }
  return { type: rootType.value }
}

function buildFieldSchema(field) {
  const schema = { type: field.type }
  if (field.description) schema.description = field.description
  if (field.type === 'object') {
    const props = {}
    const req = []
    field.properties.forEach(p => {
      if (p.name) { props[p.name] = buildFieldSchema(p); if (p.required) req.push(p.name) }
    })
    if (Object.keys(props).length > 0) schema.properties = props
    if (req.length > 0) schema.required = req
  } else if (field.type === 'array' && field.items) {
    schema.items = buildFieldSchema(field.items)
  }
  if (field.enumValues) {
    const vals = field.enumValues.split(',').map(v => v.trim()).filter(Boolean)
    if (vals.length > 0) schema.enum = vals
  }
  if (field.minimum !== null && field.minimum !== '' && !isNaN(Number(field.minimum)))
    schema.minimum = Number(field.minimum)
  if (field.maximum !== null && field.maximum !== '' && !isNaN(Number(field.maximum)))
    schema.maximum = Number(field.maximum)
  return schema
}

// Sync
function syncVisualToJson() {
  const json = visualToJson()
  jsonText.value = json
  emit('update:modelValue', json)
}

function syncJsonToVisual() {
  if (!jsonText.value || !jsonText.value.trim()) {
    rootType.value = 'object'; fields.value = []; rootRequired.value = []
    arrayItems.value = defaultField(); return
  }
  try {
    const obj = JSON.parse(jsonText.value)
    const result = jsonToVisual(obj)
    rootType.value = result.rootType
    fields.value = result.fields
    rootRequired.value = result.rootRequired || []
    if (result.items) arrayItems.value = result.items
    jsonError.value = ''
  } catch (e) {
    jsonError.value = 'JSON 解析失败: ' + e.message
  }
}

function onModeChange(val) {
  if (val === 'json') syncVisualToJson()
  else syncJsonToVisual()
}

function formatJson() {
  try {
    jsonText.value = JSON.stringify(JSON.parse(jsonText.value), null, 2)
    jsonError.value = ''
  } catch (e) { jsonError.value = 'JSON 格式错误: ' + e.message }
}

function validateJsonText() {
  if (!jsonText.value || !jsonText.value.trim()) {
    jsonError.value = ''; emit('validate', { valid: true, errors: [] }); return
  }
  try {
    const obj = JSON.parse(jsonText.value)
    const errors = validateSchemaStructure(obj)
    jsonError.value = errors.length > 0 ? errors[0] : ''
    emit('validate', { valid: errors.length === 0, errors })
  } catch (e) {
    jsonError.value = 'JSON 格式错误: ' + e.message
    emit('validate', { valid: false, errors: [e.message] })
  }
}

function validateSchemaStructure(obj) {
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
      else validateSchemaStructure(schema).forEach(e => errors.push(name + '.' + e))
    })
  }
  if (obj.type === 'array' && obj.items) {
    if (typeof obj.items !== 'object') errors.push('items 必须是对象')
    else validateSchemaStructure(obj.items).forEach(e => errors.push('items.' + e))
  }
  return errors
}

function clearAll() {
  if (mode.value === 'visual') {
    rootType.value = 'object'; fields.value = []; rootRequired.value = []
    arrayItems.value = defaultField()
  } else { jsonText.value = ''; jsonError.value = '' }
  emit('update:modelValue', '')
}

function validate() {
  if (mode.value === 'json') {
    validateJsonText()
    return jsonError.value === ''
  } else {
    const json = visualToJson()
    try {
      const obj = JSON.parse(json)
      const errors = validateSchemaStructure(obj)
      if (errors.length > 0) { emit('validate', { valid: false, errors }); return false }
      return true
    } catch (e) {
      emit('validate', { valid: false, errors: [e.message] }); return false
    }
  }
}

function flush() {
  if (_syncTimer) { clearTimeout(_syncTimer); _syncTimer = null }
  if (mode.value === 'visual') syncVisualToJson()
}

defineExpose({ validate, flush })

onMounted(() => {
  if (props.modelValue) { jsonText.value = props.modelValue; syncJsonToVisual() }
})

onUnmounted(() => {
  if (_syncTimer) { clearTimeout(_syncTimer); _syncTimer = null }
})

watch(() => props.modelValue, (val) => {
  if (val !== jsonText.value && val !== visualToJson()) {
    jsonText.value = val || ''
    if (mode.value === 'visual') syncJsonToVisual()
  }
})
</script>

<style lang="scss" scoped>
.json-schema-editor {
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  overflow: hidden;
}

.editor-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

.toolbar-left, .toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.editor-body {
  min-height: 100px;
}

.visual-mode {
  flex: 1;
  padding: 12px;
  min-width: 0;
}

.root-type-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  .label { font-size: 13px; color: #606266; font-weight: 500; }
  .field-count { font-size: 12px; color: #909399; }
}

.fields-container { min-height: 60px; }
.field-item { margin-bottom: 2px; }
.json-mode { padding: 0; flex: 1; }
.json-textarea-error :deep(.el-textarea__inner) { border-color: #f56c6c; }

.json-error-msg {
  padding: 8px 12px; font-size: 12px; color: #f56c6c; background: #fef0f0;
  display: flex; align-items: center; gap: 4px;
}

.json-valid-msg {
  padding: 8px 12px; font-size: 12px; color: #67c23a; background: #f0f9eb;
  display: flex; align-items: center; gap: 4px;
}

.section-label { font-size: 13px; color: #909399; margin-bottom: 8px; }

.add-root-btn {
  margin-top: 8px;
}

.empty-root-fields {
  text-align: center;
  padding: 32px 16px;
  color: #909399;
  .empty-icon { font-size: 36px; color: #c0c4cc; margin-bottom: 8px; }
  p { margin: 4px 0; font-size: 13px; }
  .empty-hint { font-size: 11px; color: #c0c4cc; margin-top: 8px; }
}

.template-item {
  display: flex;
  flex-direction: column;
  .template-name { font-size: 13px; font-weight: 500; }
  .template-desc { font-size: 11px; color: #909399; margin-top: 2px; }
}

:deep(.field-ghost) {
  opacity: 0.4;
  background: #ecf5ff;
  border: 1px dashed #409eff;
  border-radius: 4px;
}
</style>
