<template>
  <div class="page">
    <div class="page-header">
      <h1>用户管理</h1>
      <button v-auth="'user:add'" type="button" @click="openCreate">新增用户</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading">加载中…</p>
    <table v-else class="data-table">
      <thead>
        <tr>
          <th>用户名</th>
          <th>邮箱</th>
          <th>手机号</th>
          <th>真实姓名</th>
          <th>状态</th>
          <th>角色</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="user in users" :key="user.id">
          <td>{{ user.username }}</td>
          <td>{{ user.email }}</td>
          <td>{{ user.phone }}</td>
          <td>{{ user.realName }}</td>
          <td>{{ user.status === 1 ? '启用' : '停用' }}</td>
          <td>{{ roleNames(user) }}</td>
          <td class="actions">
            <button v-auth="'user:edit'" type="button" @click="openEdit(user)">编辑</button>
            <button v-auth="'user:edit'" type="button" @click="toggleStatus(user)">
              {{ user.status === 1 ? '停用' : '启用' }}
            </button>
            <button v-auth="'user:delete'" type="button" class="danger" @click="onDelete(user)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-if="formVisible" class="modal" @click.self="formVisible = false">
      <form class="modal-card" @submit.prevent="onSubmit">
        <h2>{{ editingId ? '编辑用户' : '新增用户' }}</h2>
        <label>
          用户名
          <input v-model.trim="form.username" required />
        </label>
        <label>
          邮箱
          <input v-model.trim="form.email" type="email" required />
        </label>
        <label>
          密码
          <input v-model="form.password" type="password" :required="!editingId" :placeholder="editingId ? '留空则不修改' : ''" />
        </label>
        <label>
          手机号
          <input v-model.trim="form.phone" />
        </label>
        <label>
          真实姓名
          <input v-model.trim="form.realName" />
        </label>
        <label>
          状态
          <select v-model.number="form.status">
            <option :value="1">启用</option>
            <option :value="0">停用</option>
          </select>
        </label>
        <fieldset>
          <legend>角色</legend>
          <label v-for="role in roles" :key="role.id" class="check">
            <input type="checkbox" :value="role.id" v-model="form.roleIds" />
            {{ role.roleName }} ({{ role.roleCode }})
          </label>
        </fieldset>
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
import { onMounted, reactive, ref } from 'vue'
import { getUserList, createUser, updateUser, deleteUser, getUserRoles } from '../../api/user'
import { getRoleList } from '../../api/role'

const users = ref([])
const roles = ref([])
const loading = ref(false)
const error = ref('')
const formVisible = ref(false)
const formError = ref('')
const saving = ref(false)
const editingId = ref(null)

const form = reactive({
  username: '',
  email: '',
  password: '',
  phone: '',
  realName: '',
  status: 1,
  roleIds: []
})

function roleNames(user) {
  return (user.roles || []).map((r) => r.roleName).join('、') || '—'
}

function resetForm() {
  form.username = ''
  form.email = ''
  form.password = ''
  form.phone = ''
  form.realName = ''
  form.status = 1
  form.roleIds = []
  formError.value = ''
}

async function loadUsers() {
  loading.value = true
  error.value = ''
  try {
    const res = await getUserList()
    users.value = res.data?.list || []
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载用户失败'
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  try {
    const res = await getRoleList()
    roles.value = res.data?.list || []
  } catch {
    roles.value = []
  }
}

function openCreate() {
  editingId.value = null
  resetForm()
  formVisible.value = true
}

async function openEdit(user) {
  editingId.value = user.id
  resetForm()
  form.username = user.username || ''
  form.email = user.email || ''
  form.phone = user.phone || ''
  form.realName = user.realName || ''
  form.status = user.status ?? 1
  const fromRow = (user.roles || []).map((r) => r.id)
  try {
    const res = await getUserRoles(user.id)
    form.roleIds = (res.data || []).map((r) => r.id)
  } catch {
    form.roleIds = fromRow
  }
  formVisible.value = true
}

function payload() {
  return {
    username: form.username,
    email: form.email,
    password: form.password,
    phone: form.phone,
    realName: form.realName,
    status: Number(form.status),
    roleIds: form.roleIds.map(Number)
  }
}

async function onSubmit() {
  formError.value = ''
  saving.value = true
  try {
    if (editingId.value) {
      await updateUser(editingId.value, payload())
    } else {
      await createUser(payload())
    }
    formVisible.value = false
    await loadUsers()
  } catch (e) {
    formError.value = e.response?.data?.message || e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function toggleStatus(user) {
  error.value = ''
  const roleIds = (user.roles || []).map((r) => r.id)
  try {
    await updateUser(user.id, {
      username: user.username,
      email: user.email,
      password: '',
      phone: user.phone,
      realName: user.realName,
      status: user.status === 1 ? 0 : 1,
      roleIds
    })
    await loadUsers()
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '更新状态失败'
  }
}

async function onDelete(user) {
  if (!confirm(`确认删除用户「${user.username}」？`)) return
  error.value = ''
  try {
    await deleteUser(user.id)
    await loadUsers()
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '删除失败'
  }
}

onMounted(() => {
  loadUsers()
  loadRoles()
})
</script>
