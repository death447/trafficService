<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>任务管理</h1>
        <p class="subtitle">查看与新建救援派单工单</p>
      </div>
      <button v-auth="'dispatch:add'" type="button" @click="goCreate">新建工单</button>
    </div>

    <div class="panel filters">
      <label>
        单号
        <input v-model.trim="filters.orderNo" placeholder="工单号" @keyup.enter="onQuery" />
      </label>
      <label>
        状态
        <select v-model="filters.status">
          <option value="">全部</option>
          <option value="PENDING">待派单</option>
          <option value="DISPATCHED">已派单</option>
          <option value="ACCEPTED">已接单</option>
          <option value="COMPLETED">已完成</option>
          <option value="ABORTED">已中止</option>
        </select>
      </label>
      <label>
        事故地点
        <input
          v-model.trim="filters.accidentAddress"
          placeholder="地址关键词"
          @keyup.enter="onQuery"
        />
      </label>
      <div class="filter-actions">
        <button type="button" @click="onQuery">查询</button>
        <button type="button" class="secondary" @click="resetFilters">重置</button>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading" class="loading-text">加载中…</p>
    <div v-else class="panel">
      <div class="table-scroll">
        <table class="data-table">
        <thead>
          <tr>
            <th>单号</th>
            <th>地点</th>
            <th>派单时间</th>
            <th>施救员</th>
            <th>施救车辆</th>
            <th>事故当事人</th>
            <th>联系方式</th>
            <th>车牌</th>
            <th>车型</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in orders"
            :key="row.id"
            class="clickable-row"
            @click="goDetail(row.id)"
          >
            <td>{{ row.orderNo }}</td>
            <td
              class="cell-address"
              :title="row.accidentAddress || undefined"
            >
              {{ row.accidentAddress || '—' }}
            </td>
            <td>{{ formatTime(row.dispatchedAt) }}</td>
            <td>{{ row.rescuerName || row.rescuerId || '—' }}</td>
            <td>{{ row.vehiclePlate || '—' }}</td>
            <td>{{ row.partyName || '—' }}</td>
            <td>{{ row.partyPhone || '—' }}</td>
            <td>{{ row.plateNo || '—' }}</td>
            <td>{{ row.vehicleTypeName || '—' }}</td>
            <td>
              <span :class="['badge', statusBadgeClass(row.status)]">
                {{ statusLabel(row.status) }}
              </span>
            </td>
            <td>{{ formatTime(row.createTime) }}</td>
            <td class="actions" @click.stop>
              <button type="button" class="secondary" @click="goDetail(row.id)">详情</button>
              <button
                v-if="canComplete(row.status)"
                v-auth="'dispatch:complete'"
                type="button"
                :disabled="!!acting"
                @click="onComplete(row)"
              >
                {{ acting === `complete-${row.id}` ? '提交中…' : '完成' }}
              </button>
              <button
                v-if="canAbort(row.status)"
                v-auth="'dispatch:abort'"
                type="button"
                class="danger"
                :disabled="!!acting"
                @click="openAbort(row)"
              >
                中止
              </button>
            </td>
          </tr>
          <tr v-if="!orders.length">
            <td colspan="13" class="empty-cell">暂无工单</td>
          </tr>
        </tbody>
        </table>
      </div>
      <p v-if="actionError" class="error action-error">{{ actionError }}</p>
    </div>
    <PaginationBar
      v-if="!loading"
      :page="pagination.page"
      :size="pagination.size"
      :total="pagination.total"
      @update:page="onPageChange"
      @update:size="onSizeChange"
    />

    <div v-if="abortVisible" class="modal" @click.self="abortVisible = false">
      <form class="modal-card" @submit.prevent="onAbort">
        <h2>中止工单</h2>
        <p v-if="abortTarget" class="abort-target">
          {{ abortTarget.orderNo }} · {{ abortTarget.accidentAddress || '无地点' }}
        </p>
        <label>
          中止原因
          <textarea
            v-model.trim="abortReason"
            rows="3"
            required
            placeholder="请填写中止原因"
          />
        </label>
        <p v-if="abortError" class="error">{{ abortError }}</p>
        <div class="modal-actions">
          <button type="button" class="secondary" @click="abortVisible = false">取消</button>
          <button type="submit" class="danger" :disabled="acting === 'abort'">
            {{ acting === 'abort' ? '提交中…' : '确认中止' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listDispatches, completeDispatch, abortDispatch } from '../../api/dispatch'
import PaginationBar from '../../components/PaginationBar.vue'

const router = useRouter()
const orders = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const acting = ref('')

const abortVisible = ref(false)
const abortTarget = ref(null)
const abortReason = ref('')
const abortError = ref('')

const filters = reactive({
  orderNo: '',
  status: '',
  accidentAddress: ''
})

const statusLabels = {
  PENDING: '待派单',
  DISPATCHED: '已派单',
  ACCEPTED: '已接单',
  COMPLETED: '已完成',
  ABORTED: '已中止'
}

function statusLabel(status) {
  return statusLabels[status] || status || '—'
}

function statusBadgeClass(status) {
  if (status === 'PENDING') return 'badge-info'
  if (status === 'DISPATCHED' || status === 'ACCEPTED') return 'badge-info'
  if (status === 'COMPLETED') return 'badge-success'
  return 'badge-muted'
}

function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}

function canComplete(status) {
  return status === 'DISPATCHED' || status === 'ACCEPTED'
}

function canAbort(status) {
  return status === 'PENDING' || status === 'DISPATCHED' || status === 'ACCEPTED'
}

function resetFilters() {
  filters.orderNo = ''
  filters.status = ''
  filters.accidentAddress = ''
  pagination.page = 1
  loadList()
}

function onQuery() {
  pagination.page = 1
  loadList()
}

function onPageChange(p) {
  pagination.page = p
  loadList()
}

function onSizeChange(s) {
  pagination.size = s
  pagination.page = 1
  loadList()
}

async function loadList() {
  loading.value = true
  error.value = ''
  try {
    const params = { page: pagination.page, size: pagination.size }
    if (filters.orderNo) params.orderNo = filters.orderNo
    if (filters.status) params.status = filters.status
    if (filters.accidentAddress) params.address = filters.accidentAddress
    const res = await listDispatches(params)
    orders.value = res.data?.list || []
    pagination.total = res.data?.total ?? 0
    if (res.data?.page) pagination.page = res.data.page
    if (res.data?.size) pagination.size = res.data.size
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载工单失败'
  } finally {
    loading.value = false
  }
}

function goCreate() {
  router.push('/dispatches/new')
}

function goDetail(id) {
  router.push(`/dispatches/${id}`)
}

async function onComplete(row) {
  acting.value = `complete-${row.id}`
  actionError.value = ''
  try {
    await completeDispatch(row.id)
    await loadList()
  } catch (e) {
    actionError.value = e.response?.data?.message || e.message || '完成失败'
  } finally {
    acting.value = ''
  }
}

function openAbort(row) {
  abortTarget.value = row
  abortReason.value = ''
  abortError.value = ''
  abortVisible.value = true
}

async function onAbort() {
  if (!abortReason.value.trim()) {
    abortError.value = '请填写中止原因'
    return
  }
  if (!abortTarget.value) return
  acting.value = 'abort'
  abortError.value = ''
  try {
    await abortDispatch(abortTarget.value.id, { abortReason: abortReason.value.trim() })
    abortVisible.value = false
    abortTarget.value = null
    await loadList()
  } catch (e) {
    abortError.value = e.response?.data?.message || e.message || '中止失败'
  } finally {
    acting.value = ''
  }
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0.85rem 1rem;
  align-items: flex-end;
  padding: 0.9rem 1rem;
  margin-bottom: 1rem;
}

.filters label {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  font-size: 0.8rem;
  color: var(--text-secondary);
  min-width: 140px;
}

.filters input,
.filters select {
  padding: 0.45rem 0.6rem;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  font-family: inherit;
  font-size: 0.875rem;
  color: var(--text);
  min-width: 160px;
}

.filter-actions {
  display: flex;
  gap: 0.4rem;
}

.clickable-row {
  cursor: pointer;
}

.empty-cell {
  text-align: center;
  color: var(--text-secondary);
  padding: 1.5rem 1rem !important;
}

.loading-text {
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  white-space: nowrap;
}

.action-error {
  margin: 0.75rem 1rem 0;
}

.abort-target {
  margin: 0 0 0.75rem;
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.modal-card textarea {
  padding: 0.5rem 0.65rem;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  font-family: inherit;
  font-size: 0.875rem;
  color: var(--text);
  resize: vertical;
}

.table-scroll {
  overflow-x: auto;
}

.data-table th,
.data-table td {
  white-space: nowrap;
}

.cell-address {
  max-width: 14rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
