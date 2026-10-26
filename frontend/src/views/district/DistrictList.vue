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
    <PaginationBar
      v-if="!loading"
      :page="pagination.page"
      :size="pagination.size"
      :total="pagination.total"
      @update:page="onPageChange"
      @update:size="onSizeChange"
    />

    <div v-if="formVisible" class="modal district-modal" @click.self="closeForm">
      <form class="modal-card modal-wide" @submit.prevent="onSubmit">
        <div class="modal-title-row">
          <h2>{{ editingId ? '编辑片区' : '新增片区' }}</h2>
          <div class="modal-actions modal-actions-inline">
            <button type="button" class="secondary" @click="closeForm">取消</button>
            <button type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button>
          </div>
        </div>
        <div class="meta-grid">
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
          <label class="meta-remark">
            备注
            <input v-model.trim="form.remark" />
          </label>
        </div>

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
          <p v-else class="map-hint">灰色为其它启用片区（参考）；点击地图追加当前围栏顶点，可撤销或清空后重绘。</p>
          <div v-if="amapReady" ref="mapEl" class="map-box" />
          <label v-if="!amapReady" class="fence-json-label">
            围栏 JSON
            <textarea
              v-model="fenceJsonText"
              rows="6"
              required
              placeholder='[{"lng":120.74,"lat":30.74},{"lng":120.77,"lat":30.74},{"lng":120.77,"lat":30.76}]'
            />
          </label>
          <p v-else class="vertex-count">当前顶点数：{{ fencePoints.length }}</p>
        </div>

        <p v-if="formError" class="error">{{ formError }}</p>
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
import { loadEnabledDistrictOverlayItems } from '../../utils/districtOverlay'
import PaginationBar from '../../components/PaginationBar.vue'

const districts = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
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

async function loadReferenceDistricts() {
  const items = await loadEnabledDistrictOverlayItems()
  return items.filter((d) => d.id !== editingId.value)
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
    const refs = await loadReferenceDistricts()
    polygonEditor.setReferenceDistricts?.(refs)
    // 弹窗刚打开时容器尺寸可能未稳定，延后再 fit 一次
    await nextTick()
    requestAnimationFrame(() => {
      try {
        polygonEditor?.fitReferenceView?.()
      } catch (_) {
        /* ignore */
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
    const res = await listDistricts({ page: pagination.page, size: pagination.size })
    districts.value = res.data?.list || []
    pagination.total = res.data?.total ?? 0
    if (res.data?.page) pagination.page = res.data.page
    if (res.data?.size) pagination.size = res.data.size
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载片区失败'
  } finally {
    loading.value = false
  }
}

function onPageChange(p) {
  pagination.page = p
  loadDistricts()
}

function onSizeChange(s) {
  pagination.size = s
  pagination.page = 1
  loadDistricts()
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
.district-modal {
  padding: 1.25rem 1rem;
  align-items: stretch;
}

.modal-wide {
  width: min(1280px, calc(100vw - 1.5rem));
  max-height: calc(100vh - 2.5rem);
  padding: 1rem 1.15rem 1.1rem;
  gap: 0.55rem;
  overflow: auto;
}

.modal-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.modal-title-row h2 {
  margin: 0;
}

.modal-actions-inline {
  margin-top: 0;
  flex-shrink: 0;
}

.meta-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 0.9fr) minmax(120px, 0.7fr) minmax(0, 1.4fr);
  gap: 0.45rem 0.65rem;
}

.meta-grid label {
  gap: 0.2rem;
  font-size: 0.8rem;
}

.meta-grid :deep(input),
.meta-grid :deep(select),
.meta-grid input,
.meta-grid select {
  padding: 0.35rem 0.5rem;
  font-size: 0.85rem;
}

.fence-block {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  flex: 1;
  min-height: 0;
}

.fence-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  font-size: 0.8rem;
  color: var(--text-secondary);
}

.fence-tools {
  display: flex;
  gap: 0.4rem;
}

.hint {
  margin: 0;
  padding: 0.45rem 0.65rem;
  background: #fff8e8;
  border: 1px solid #f0d78c;
  border-radius: var(--radius);
  color: #8a6a12;
  font-size: 0.78rem;
}

.map-hint {
  margin: 0;
  font-size: 0.75rem;
  color: var(--text-secondary);
}

.map-box {
  height: min(68vh, 720px);
  min-height: 480px;
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
  font-size: 0.75rem;
  color: var(--text-secondary);
}

@media (max-width: 900px) {
  .meta-grid {
    grid-template-columns: 1fr 1fr;
  }

  .meta-remark {
    grid-column: 1 / -1;
  }

  .map-box {
    height: min(55vh, 520px);
    min-height: 360px;
  }
}
</style>
