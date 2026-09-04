<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>片区管理</h1>
        <p class="subtitle">维护服务片区编码、状态与电子围栏</p>
      </div>
      <button v-auth="'district:add'" type="button" @click="openCreate">新增片区</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading" class="loading-text">加载中…</p>
    <div v-else class="panel">
      <table class="data-table">
        <thead>
          <tr>
            <th>名称</th>
            <th>编码</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="district in districts" :key="district.id">
            <td>{{ district.name }}</td>
            <td>{{ district.code }}</td>
            <td>
              <span :class="['badge', statusBadgeClass(district.status)]">
                {{ statusLabel(district.status) }}
              </span>
            </td>
            <td class="actions">
              <button v-auth="'district:edit'" type="button" @click="openEdit(district)">编辑</button>
              <button
                v-auth="'district:delete'"
                type="button"
                class="danger"
                @click="onDelete(district)"
              >
                删除
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="formVisible" class="modal" @click.self="closeForm">
      <form class="modal-card modal-wide" @submit.prevent="onSubmit">
        <h2>{{ editingId ? '编辑片区' : '新增片区' }}</h2>
        <label>
          名称
          <input v-model.trim="form.name" required />
        </label>
        <label>
          编码
          <input v-model.trim="form.code" required :disabled="!!editingId" />
        </label>
        <label>
          状态
          <select v-model="form.status" required>
            <option value="ENABLED">启用</option>
            <option value="DISABLED">停用</option>
          </select>
        </label>
        <label>
          备注
          <input v-model.trim="form.remark" />
        </label>

        <div class="fence-block">
          <div class="fence-header">
            <span>围栏（至少 3 个顶点）</span>
            <div v-if="amapReady" class="fence-tools">
              <button type="button" class="secondary" @click="undoFencePoint">撤销一点</button>
              <button type="button" class="secondary" @click="clearFence">清空</button>
            </div>
          </div>
          <p v-if="!amapReady && !hasAmapKey()" class="hint">
            未配置 VITE_AMAP_KEY，请直接编辑下方 JSON 围栏（格式：[{"lng":…,"lat":…},…]）。
          </p>
          <p v-else-if="!amapReady" class="hint">
            地图加载失败，请直接编辑下方 JSON 围栏（格式：[{"lng":…,"lat":…},…]）。
          </p>
          <p v-else class="map-hint">点击地图追加顶点；可撤销或清空后重绘。</p>
          <div v-if="amapReady" ref="mapEl" class="map-box" />
          <label v-if="!amapReady" class="fence-json-label">
            围栏 JSON
            <textarea
              v-model="fenceJsonText"
              rows="6"
              required
              placeholder='[{"lng":114.04,"lat":22.53},{"lng":114.08,"lat":22.53},{"lng":114.08,"lat":22.56}]'
            />
          </label>
          <p v-else class="vertex-count">当前顶点数：{{ fencePoints.length }}</p>
        </div>

        <p v-if="formError" class="error">{{ formError }}</p>
        <div class="modal-actions">
          <button type="button" class="secondary" @click="closeForm">取消</button>
          <button type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import {
  listDistricts,
  createDistrict,
  updateDistrict,
  deleteDistrict
} from '../../api/district'
import { createPolygonEditor, hasAmapKey } from '../../utils/amap'

const districts = ref([])
const loading = ref(false)
const error = ref('')
const formVisible = ref(false)
const formError = ref('')
const saving = ref(false)
const editingId = ref(null)
const mapEl = ref(null)
const amapReady = ref(hasAmapKey())
const fencePoints = ref([])
const fenceJsonText = ref('[]')
let polygonEditor = null

const form = reactive({
  name: '',
  code: '',
  status: 'ENABLED',
  remark: ''
})

const statusLabels = {
  ENABLED: '启用',
  DISABLED: '停用'
}

function statusLabel(status) {
  return statusLabels[status] || status || '—'
}

function statusBadgeClass(status) {
  if (status === 'ENABLED') return 'badge-success'
  return 'badge-muted'
}

function parseFenceJson(raw) {
  if (!raw) return []
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!Array.isArray(parsed)) return []
    return parsed
      .map((p) => ({ lng: Number(p.lng), lat: Number(p.lat) }))
      .filter((p) => Number.isFinite(p.lng) && Number.isFinite(p.lat))
  } catch {
    return []
  }
}

function resetForm() {
  form.name = ''
  form.code = ''
  form.status = 'ENABLED'
  form.remark = ''
  formError.value = ''
  fencePoints.value = []
  fenceJsonText.value = '[]'
  amapReady.value = hasAmapKey()
}

function destroyEditor() {
  if (polygonEditor && typeof polygonEditor.destroy === 'function') {
    polygonEditor.destroy()
  }
  polygonEditor = null
}

async function initEditor(path) {
  destroyEditor()
  if (!amapReady.value || !mapEl.value) return
  try {
    polygonEditor = await createPolygonEditor(mapEl.value, {
      path,
      onChange(points) {
        fencePoints.value = points
      }
    })
  } catch (e) {
    amapReady.value = false
    fenceJsonText.value = JSON.stringify(fencePoints.value, null, 2)
    formError.value = e.message || '地图加载失败，请改用 JSON 编辑'
  }
}

async function loadDistricts() {
  loading.value = true
  error.value = ''
  try {
    const res = await listDistricts()
    districts.value = res.data?.list || []
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载片区失败'
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  editingId.value = null
  resetForm()
  formVisible.value = true
  await nextTick()
  if (amapReady.value) await initEditor([])
}

async function openEdit(district) {
  editingId.value = district.id
  resetForm()
  form.name = district.name || ''
  form.code = district.code || ''
  form.status = district.status || 'ENABLED'
  form.remark = district.remark || ''
  const path = parseFenceJson(district.fenceJson)
  fencePoints.value = path
  fenceJsonText.value = JSON.stringify(path, null, 2)
  formVisible.value = true
  await nextTick()
  if (amapReady.value) await initEditor(path)
}

function closeForm() {
  formVisible.value = false
  destroyEditor()
}

function undoFencePoint() {
  if (polygonEditor?.undo) polygonEditor.undo()
}

function clearFence() {
  if (polygonEditor?.clear) polygonEditor.clear()
  else {
    fencePoints.value = []
    fenceJsonText.value = '[]'
  }
}

function resolveFencePayload() {
  if (amapReady.value) {
    return fencePoints.value
  }
  const points = parseFenceJson(fenceJsonText.value)
  if (!points.length && fenceJsonText.value.trim() && fenceJsonText.value.trim() !== '[]') {
    throw new Error('围栏 JSON 格式无效')
  }
  return points
}

async function onSubmit() {
  formError.value = ''
  let fence
  try {
    fence = resolveFencePayload()
  } catch (e) {
    formError.value = e.message || '围栏格式无效'
    return
  }
  if (!fence || fence.length < 3) {
    formError.value = '围栏至少需要3个顶点'
    return
  }
  saving.value = true
  try {
    const data = {
      name: form.name,
      code: form.code,
      status: form.status,
      remark: form.remark || null,
      fence
    }
    if (editingId.value) {
      await updateDistrict(editingId.value, data)
    } else {
      await createDistrict(data)
    }
    closeForm()
    await loadDistricts()
  } catch (e) {
    formError.value = e.response?.data?.message || e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function onDelete(district) {
  if (!confirm(`确认删除片区「${district.name}」？`)) return
  error.value = ''
  try {
    await deleteDistrict(district.id)
    await loadDistricts()
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '删除失败'
  }
}

loadDistricts()

onBeforeUnmount(() => {
  destroyEditor()
})
</script>

<style scoped>
.modal-wide {
  width: 640px;
}

.fence-block {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.fence-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.fence-tools {
  display: flex;
  gap: 0.4rem;
}

.hint {
  margin: 0;
  padding: 0.55rem 0.75rem;
  background: #fff8e8;
  border: 1px solid #f0d78c;
  border-radius: var(--radius);
  color: #8a6a12;
  font-size: 0.82rem;
}

.map-hint {
  margin: 0;
  font-size: 0.8rem;
  color: var(--text-secondary);
}

.map-box {
  height: 280px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--bg-muted);
}

.fence-json-label textarea {
  padding: 0.5rem 0.65rem;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.8rem;
  color: var(--text);
  resize: vertical;
}

.vertex-count {
  margin: 0;
  font-size: 0.8rem;
  color: var(--text-secondary);
}
</style>
