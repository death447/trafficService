<template>
  <div class="page">
    <div class="page-header">
      <h1>角色管理</h1>
      <button v-auth="'role:add'" type="button" @click="openCreate">新增角色</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading">加载中…</p>
    <table v-else class="data-table">
      <thead>
        <tr>
          <th>角色名称</th>
          <th>角色编码</th>
          <th>描述</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="role in roles" :key="role.id">
          <td>{{ role.roleName }}</td>
          <td>{{ role.roleCode }}</td>
          <td>{{ role.description }}</td>
          <td>{{ role.status === 1 ? '启用' : '停用' }}</td>
          <td class="actions">
            <button v-auth="'role:edit'" type="button" @click="openEdit(role)">编辑</button>
            <button v-auth="'role:edit'" type="button" @click="openAssign(role)">分配权限</button>
            <button v-auth="'role:delete'" type="button" class="danger" @click="onDelete(role)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-if="formVisible" class="modal" @click.self="formVisible = false">
      <form class="modal-card" @submit.prevent="onSubmit">
        <h2>{{ editingId ? '编辑角色' : '新增角色' }}</h2>
        <label>
          角色名称
          <input v-model.trim="form.roleName" required />
        </label>
        <label>
          角色编码
          <input v-model.trim="form.roleCode" required />
        </label>
        <label>
          描述
          <input v-model.trim="form.description" />
        </label>
        <label>
          状态
          <select v-model.number="form.status">
            <option :value="1">启用</option>
            <option :value="0">停用</option>
          </select>
        </label>
        <p v-if="formError" class="error">{{ formError }}</p>
        <div class="modal-actions">
          <button type="button" class="secondary" @click="formVisible = false">取消</button>
          <button type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button>
        </div>
      </form>
    </div>

    <div v-if="assignVisible" class="modal" @click.self="assignVisible = false">
      <form class="modal-card" @submit.prevent="onAssign">
        <h2>分配权限 — {{ assignRole?.roleName }}</h2>
        <div v-if="assignLoading">加载中…</div>
        <div v-else class="perm-groups">
          <div v-for="group in permissionGroups" :key="group.id || group.code" class="perm-group">
            <label class="check">
              <input
                type="checkbox"
                :checked="isGroupChecked(group)"
                @change="toggleGroup(group, $event.target.checked)"
              />
              <strong>{{ group.permissionName }}</strong>
              <span class="muted">{{ group.permissionCode }} / {{ group.permissionType }}</span>
            </label>
            <label v-for="child in group.children" :key="child.id" class="check nested">
              <input type="checkbox" :value="child.id" v-model="selectedPermissionIds" />
              {{ child.permissionName }}
              <span class="muted">{{ child.permissionCode }} / {{ child.permissionType }}</span>
            </label>
          </div>
        </div>
        <p v-if="assignError" class="error">{{ assignError }}</p>
        <div class="modal-actions">
          <button type="button" class="secondary" @click="assignVisible = false">取消</button>
          <button v-auth="'role:edit'" type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  getRoleList,
  createRole,
  updateRole,
  deleteRole,
  getRolePermissions,
  assignRolePermissions
} from '../../api/role'
import { getPermissionList } from '../../api/permission'

const roles = ref([])
const permissions = ref([])
const loading = ref(false)
const error = ref('')
const formVisible = ref(false)
const formError = ref('')
const saving = ref(false)
const editingId = ref(null)

const assignVisible = ref(false)
const assignLoading = ref(false)
const assignError = ref('')
const assignRole = ref(null)
const selectedPermissionIds = ref([])

const form = reactive({
  roleName: '',
  roleCode: '',
  description: '',
  status: 1
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

function resetForm() {
  form.roleName = ''
  form.roleCode = ''
  form.description = ''
  form.status = 1
  formError.value = ''
}

async function loadRoles() {
  loading.value = true
  error.value = ''
  try {
    const res = await getRoleList()
    roles.value = res.data?.list || []
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载角色失败'
  } finally {
    loading.value = false
  }
}

async function loadPermissions() {
  const res = await getPermissionList()
  permissions.value = res.data?.list || []
}

function openCreate() {
  editingId.value = null
  resetForm()
  formVisible.value = true
}

function openEdit(role) {
  editingId.value = role.id
  resetForm()
  form.roleName = role.roleName || ''
  form.roleCode = role.roleCode || ''
  form.description = role.description || ''
  form.status = role.status ?? 1
  formVisible.value = true
}

async function onSubmit() {
  formError.value = ''
  saving.value = true
  const body = {
    roleName: form.roleName,
    roleCode: form.roleCode,
    description: form.description,
    status: Number(form.status)
  }
  try {
    if (editingId.value) {
      await updateRole(editingId.value, body)
    } else {
      await createRole(body)
    }
    formVisible.value = false
    await loadRoles()
  } catch (e) {
    formError.value = e.response?.data?.message || e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function onDelete(role) {
  if (!confirm(`确认删除角色「${role.roleName}」？`)) return
  error.value = ''
  try {
    await deleteRole(role.id)
    await loadRoles()
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '删除失败'
  }
}

function isGroupChecked(group) {
  return selectedPermissionIds.value.includes(group.id)
}

function toggleGroup(group, checked) {
  const ids = new Set(selectedPermissionIds.value)
  const all = [group.id, ...(group.children || []).map((c) => c.id)]
  all.forEach((id) => (checked ? ids.add(id) : ids.delete(id)))
  selectedPermissionIds.value = Array.from(ids)
}

async function openAssign(role) {
  assignRole.value = role
  assignError.value = ''
  assignVisible.value = true
  assignLoading.value = true
  try {
    if (!permissions.value.length) {
      await loadPermissions()
    }
    const res = await getRolePermissions(role.id)
    selectedPermissionIds.value = (res.data || []).map((p) => p.id)
  } catch (e) {
    assignError.value = e.response?.data?.message || e.message || '加载权限失败'
    selectedPermissionIds.value = []
  } finally {
    assignLoading.value = false
  }
}

async function onAssign() {
  if (!assignRole.value) return
  assignError.value = ''
  saving.value = true
  try {
    await assignRolePermissions(
      assignRole.value.id,
      selectedPermissionIds.value.map(Number)
    )
    assignVisible.value = false
  } catch (e) {
    assignError.value = e.response?.data?.message || e.message || '分配失败'
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadRoles()
})
</script>
