<template>
  <div class="schema-field-row" :class="{ 'is-nested': depth > 0 }">
    <!-- Row 1: Main controls -->
    <div class="field-row-main">
      <span v-if="!isArrayItem" class="drag-handle" title="拖拽排序">
        <el-icon><Rank /></el-icon>
      </span>

      <template v-if="isArrayItem">
        <span class="array-item-badge">元素</span>
      </template>
      <el-input
        v-else
        :modelValue="field.name"
        @update:modelValue="updateField({ name: $event })"
        placeholder="字段名"
        size="small"
        class="field-name"
      />

      <el-select
        :modelValue="field.type"
        @update:modelValue="onTypeChange"
        size="small"
        class="field-type"
      >
        <el-option label="string" value="string" />
        <el-option label="integer" value="integer" />
        <el-option label="number" value="number" />
        <el-option label="boolean" value="boolean" />
        <el-option label="object" value="object" />
        <el-option label="array" value="array" />
        <el-option label="null" value="null" />
      </el-select>

      <span
        v-if="!isArrayItem"
        class="required-star"
        :class="{ active: isRequired }"
        :title="isRequired ? '点击取消必填' : '点击设为必填'"
        @click="$emit('toggle-required')"
      >*</span>

      <el-input
        :modelValue="field.description"
        @update:modelValue="updateField({ description: $event })"
        placeholder="描述 (可选)"
        size="small"
        class="field-desc"
      />

      <el-button v-if="!isArrayItem" size="small" text type="primary" class="action-btn" @click="handleAdd" title="添加子字段">
        <el-icon><Plus /></el-icon>
      </el-button>

      <el-button v-if="!isArrayItem" size="small" text type="danger" class="action-btn" @click="$emit('remove')" title="删除字段">
        <el-icon><Delete /></el-icon>
      </el-button>
    </div>

    <!-- Row 2: Extra options -->
    <div v-if="field.type === 'string' && field.enumValues" class="field-row-extra">
      <span class="extra-label">枚举</span>
      <el-input
        :modelValue="field.enumValues"
        @update:modelValue="updateField({ enumValues: $event })"
        placeholder="用逗号分隔，如: active, inactive"
        size="small"
        class="extra-input"
      />
    </div>

    <div v-if="(field.type === 'integer' || field.type === 'number')" class="field-row-extra">
      <span class="extra-label">范围</span>
      <el-input
        :modelValue="field.minimum"
        @update:modelValue="updateField({ minimum: $event })"
        size="small"
        placeholder="min"
        class="extra-number"
      />
      <span class="extra-sep">~</span>
      <el-input
        :modelValue="field.maximum"
        @update:modelValue="updateField({ maximum: $event })"
        size="small"
        placeholder="max"
        class="extra-number"
      />
    </div>

    <!-- Object: Collapsible children -->
    <div v-if="field.type === 'object'" class="field-children-section">
      <div class="children-toggle" @click="expanded = !expanded">
        <el-icon class="toggle-arrow" :class="{ expanded }"><ArrowRight /></el-icon>
        <span class="children-count">
          <el-tag size="small" type="info" round>{{ field.properties.length }}</el-tag>
          个子字段
        </span>
        <span v-if="requiredCount > 0" class="children-required">
          ({{ requiredCount }} 个必填)
        </span>
      </div>

      <div v-show="expanded" class="field-children">
        <div v-if="field.properties.length === 0" class="empty-children">
          <el-icon><InfoFilled /></el-icon>
          暂无子字段，点击下方添加
        </div>
        <draggable
          :list="field.properties"
          item-key="_uid"
          handle=".drag-handle"
          ghost-class="field-ghost"
          animation="200"
          @change="onSubFieldsChange"
        >
          <template #item="{ element: sub, index }">
            <div class="field-item">
              <SchemaFieldRow
                :field="sub"
                :required-fields="field.properties.filter(p => p.required).map(p => p.name)"
                :depth="depth + 1"
                @update="updateSubField(index, $event)"
                @remove="removeSubField(index)"
                @toggle-required="toggleSubRequired(index)"
              />
            </div>
          </template>
        </draggable>
        <el-button size="small" text type="primary" @click="addSubField" class="add-child-btn">
          <el-icon><Plus /></el-icon> 添加子字段
        </el-button>
      </div>
    </div>

    <!-- Array: Collapsible items -->
    <div v-if="field.type === 'array'" class="field-children-section">
      <div class="children-toggle" @click="expanded = !expanded">
        <el-icon class="toggle-arrow" :class="{ expanded }"><ArrowRight /></el-icon>
        <span class="children-count">元素类型</span>
      </div>

      <div v-show="expanded" class="field-children">
        <div class="field-item">
          <SchemaFieldRow
            :field="field.items || emptyField()"
            :required-fields="[]"
            :depth="depth + 1"
            :is-array-item="true"
            @update="updateItems($event)"
            @remove="updateItems(emptyField())"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Rank, Plus, Delete, ArrowRight, InfoFilled } from '@element-plus/icons-vue'
import draggable from 'vuedraggable'

const props = defineProps({
  field: { type: Object, required: true },
  requiredFields: { type: Array, default: () => [] },
  depth: { type: Number, default: 0 },
  isArrayItem: { type: Boolean, default: false }
})

const emit = defineEmits(['update', 'remove', 'toggle-required'])

const expanded = ref(props.depth < 2)

const isRequired = computed(() => props.requiredFields.includes(props.field.name))

const requiredCount = computed(() => {
  if (!props.field.properties) return 0
  return props.field.properties.filter(p => p.required).length
})

let _uidCounter = 0

function emptyField() {
  return {
    _uid: ++_uidCounter,
    name: '', type: 'string', required: false, description: '',
    properties: [], items: null, enumValues: '', minimum: null, maximum: null
  }
}

function updateField(patch) {
  emit('update', { ...props.field, ...patch })
}

function onTypeChange(newType) {
  const patch = { type: newType }
  if (newType === 'object' && !props.field.properties) {
    patch.properties = []
  } else if (newType === 'array' && !props.field.items) {
    patch.items = emptyField()
  }
  updateField(patch)
  expanded.value = true
}

function handleAdd() {
  if (props.field.type === 'object') {
    addSubField()
  }
}

function updateSubField(index, newField) {
  const props_arr = [...props.field.properties]
  props_arr[index] = newField
  updateField({ properties: props_arr })
}

function removeSubField(index) {
  const props_arr = [...props.field.properties]
  props_arr.splice(index, 1)
  updateField({ properties: props_arr })
}

function addSubField() {
  const props_arr = [...props.field.properties, emptyField()]
  updateField({ properties: props_arr })
  expanded.value = true
}

function toggleSubRequired(index) {
  const props_arr = [...props.field.properties]
  props_arr[index] = { ...props_arr[index], required: !props_arr[index].required }
  updateField({ properties: props_arr })
}

function updateItems(newItems) {
  updateField({ items: newItems })
}

function onSubFieldsChange() {
  updateField({ properties: [...props.field.properties] })
}
</script>

<script>
export default { name: 'SchemaFieldRow' }
</script>

<style lang="scss" scoped>
.schema-field-row {
  padding: 6px 0;

  &.is-nested {
    margin-left: 0;
  }
}

.field-row-main {
  display: flex;
  align-items: center;
  gap: 6px;
}

.drag-handle {
  cursor: grab;
  color: #c0c4cc;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  transition: color 0.2s;
  &:hover { color: #909399; }
  &:active { cursor: grabbing; }
}

.array-item-badge {
  flex-shrink: 0;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 500;
  color: #909399;
  background: #f0f2f5;
  border-radius: 4px;
  line-height: 1.4;
}

.field-name {
  width: 140px;
  flex-shrink: 0;
}

.field-type {
  width: 105px;
  flex-shrink: 0;
  :deep(.el-input__inner) {
    font-weight: 500;
    font-size: 12px;
  }
}

.required-star {
  flex-shrink: 0;
  width: 16px;
  text-align: center;
  font-size: 16px;
  font-weight: 700;
  color: #dcdfe6;
  cursor: pointer;
  user-select: none;
  transition: color 0.2s;
  line-height: 1;
  &.active { color: #f56c6c; }
  &:hover { color: #f89898; }
}

.field-desc {
  flex: 1;
  min-width: 0;
}

.action-btn {
  flex-shrink: 0;
  padding: 4px !important;
}

.field-row-extra {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0 4px 30px;
  .extra-label {
    font-size: 12px;
    color: #909399;
    flex-shrink: 0;
    width: 30px;
  }
  .extra-input {
    flex: 1;
    min-width: 0;
  }
  .extra-number {
    width: 90px;
    flex-shrink: 0;
  }
  .extra-sep {
    color: #c0c4cc;
    flex-shrink: 0;
  }
}

.field-children-section {
  margin-top: 4px;
}

.children-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  margin-left: 26px;
  border-radius: 4px;
  cursor: pointer;
  user-select: none;
  transition: background 0.2s;
  &:hover { background: #f5f7fa; }
}

.toggle-arrow {
  font-size: 12px;
  color: #909399;
  transition: transform 0.25s ease;
  &.expanded { transform: rotate(90deg); }
}

.children-count {
  font-size: 12px;
  color: #606266;
}

.children-required {
  font-size: 11px;
  color: #f56c6c;
}

.field-children {
  margin-left: 26px;
  padding-left: 10px;
  border-left: 2px solid #e4e7ed;
}

.empty-children {
  padding: 12px;
  font-size: 12px;
  color: #c0c4cc;
  text-align: center;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.add-child-btn {
  margin-top: 4px;
  margin-left: 0;
}

:deep(.field-ghost) {
  opacity: 0.4;
  background: #ecf5ff;
  border: 1px dashed #409eff;
  border-radius: 4px;
}
</style>
