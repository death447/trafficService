<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>权限管理</h1>
        <p class="subtitle">维护模块与操作权限树，支撑动态授权</p>
      </div>
      <button v-auth="'permission:add'" type="button" @click="openCreate">新增权限</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading" class="loading-text">加载中…</p>
    <div v-else class="panel tree">
      <div v-for="group in permissionGroups" :key="group.id" class="tree-group">
        <div class="tree-row">
          <strong>{{ group.permissionName }}</strong>
          <span class="badge badge-info">{{ group.permissionType }}</span>
          <span class="muted">{{ group.permissionCode }}</span>
          <span class="actions">
            <button v-auth="'permission:edit'" type="button" @click="openEdit(group)">编辑</button>
            <button v-auth="'permission:delete'" type="button" class="danger" @click="onDelete(group)">删除</button>
          </span>
        </div>
        <div v-for="child in group.children" :key="child.id" class="tree-row nested">
          <span>{{ child.permissionName }}</span>
          <span class="badge badge-muted">{{ child.permissionType }}</span>
          <span class="muted">{{ child.permissionCode }}</span>
          <span class="actions">
            <button v-auth="'permission:edit'" type="button" @click="openEdit(child)">编辑</button>
            <button v-auth="'permission:delete'" type="button" class="danger" @click="onDelete(child)">删除</button>
          </span>
        </div>
      </div>
    </div>

    <div v-if="formVisible" class="modal" @click.self="formVisible = false">
      <form class="modal-card" @submit.prevent="onSubmit">
        <h2>{{ editingId ? '编辑权限' : '新增权限' }}</h2>
        <label>
          权限名称
          <input v-model.trim="form.permissionName" required />
        </label>
        <label>
          权限编码
          <input v-model.trim="form.permissionCode" required />
        </label>
        <label>
          类型
          <select v-model="form.permissionType">
            <option value="MODULE">MODULE</option>
            <option value="BUTTON">BUTTON</option>
            <option value="API">API</option>
          </select>
        </label>
        <label>
          父权限
          <select v-model.number="form.parentId">
            <option :value="0">无（顶级）</option>
            <option v-for="p in parentOptions" :key="p.id" :value="p.id">
              {{ p.permissionName }} ({{ p.permissionCode }})
            </option>
          </select>
        </label>
        <label>
          描述
          <input v-model.trim="form.description" />
        </label>
        <label>
          排序
          <input v-model.number="form.sortOrder" type="number" />
        </label>
        <p v-if="formError" class="error">{{ formError }}</p>
        <div class="modal-actions">
          <button type="button" class="secondary" @click="formVisible = false">取消</button>
          <button type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  getPermissionList,
  createPermission,
  updatePermission,
  deletePermission
} from '../../api/permission'

const permissions = ref([])
const loading = ref(false)
const error = ref('')
const formVisible = ref(false)
const formError = ref('')
const saving = ref(false)
const editingId = ref(null)

const form = reactive({
  permissionName: '',
  permissionCode: '',
  permissionType: 'BUTTON',
  parentId: 0,
  description: '',
  sortOrder: 0
})

const permissionGroups = computed(() => {
  const list = permissions.value
  const byParent = new Map()
  list.forEach((p) => {
    const key = p.parentId || 0
    if (!byParent.has(key)) byParent.set(key, [])
    byParent.get(key).push(p)
  })
  const roots = byParent.get(0) || []
  return roots.map((root) => ({
    ...root,
    children: byParent.get(root.id) || []
  }))
})

const parentOptions = computed(() =>
  permissions.value.filter((p) => p.id !== editingId.value)
)

function resetForm() {
  form.permissionName = ''
  form.permissionCode = ''
  form.permissionType = 'BUTTON'
  form.parentId = 0
  form.description = ''
  form.sortOrder = 0
  formError.value = ''
}

async function loadPermissions() {
  loading.value = true
  error.value = ''
  try {
    const res = await getPermissionList()
    permissions.value = res.data?.list || []
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载权限失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  resetForm()
  formVisible.value = true
}

function openEdit(item) {
  editingId.value = item.id
  resetForm()
  form.permissionName = item.permissionName || ''
  form.permissionCode = item.permissionCode || ''
  form.permissionType = item.permissionType || 'BUTTON'
  form.parentId = item.parentId ?? 0
  form.description = item.description || ''
  form.sortOrder = item.sortOrder ?? 0
  formVisible.value = true
}

async function onSubmit() {
  formError.value = ''
  saving.value = true
  const body = {
    permissionName: form.permissionName,
    permissionCode: form.permissionCode,
    permissionType: form.permissionType,
    parentId: Number(form.parentId) || 0,
    description: form.description,
    sortOrder: Number(form.sortOrder) || 0
  }
  try {
    if (editingId.value) {
      await updatePermission(editingId.value, body)
    } else {
      await createPermission(body)
    }
    formVisible.value = false
    await loadPermissions()
  } catch (e) {
    formError.value = e.response?.data?.message || e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function onDelete(item) {
  if (!confirm(`确认删除权限「${item.permissionName}」？`)) return
  error.value = ''
  try {
    await deletePermission(item.id)
    await loadPermissions()
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '删除失败'
  }
}

onMounted(() => {
  loadPermissions()
})
</script>
