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
        <input v-model.trim="filters.orderNo" placeholder="工单号" @keyup.enter="loadList" />
      </label>
      <label>
        状态
        <select v-model="filters.status">
          <option value="">全部</option>
          <option value="PENDING">待派单</option>
          <option value="DISPATCHED">处理中</option>
          <option value="COMPLETED">已完成</option>
          <option value="ABORTED">已中止</option>
        </select>
      </label>
      <label>
        事故地点
        <input
          v-model.trim="filters.accidentAddress"
          placeholder="地址关键词"
          @keyup.enter="loadList"
        />
      </label>
      <div class="filter-actions">
        <button type="button" @click="loadList">查询</button>
        <button type="button" class="secondary" @click="resetFilters">重置</button>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading" class="loading-text">加载中…</p>
    <div v-else class="panel">
      <table class="data-table">
        <thead>
          <tr>
            <th>单号</th>
            <th>地点</th>
            <th>状态</th>
            <th>调度员</th>
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
            <td>{{ row.accidentAddress || '—' }}</td>
            <td>
              <span :class="['badge', statusBadgeClass(row.status)]">
                {{ statusLabel(row.status) }}
              </span>
            </td>
            <td>{{ row.dispatcherName || row.dispatcherId || '—' }}</td>
            <td>{{ formatTime(row.createTime) }}</td>
            <td class="actions" @click.stop>
              <button type="button" class="secondary" @click="goDetail(row.id)">详情</button>
            </td>
          </tr>
          <tr v-if="!orders.length">
            <td colspan="6" class="empty-cell">暂无工单</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listDispatches } from '../../api/dispatch'

const router = useRouter()
const orders = ref([])
const loading = ref(false)
const error = ref('')

const filters = reactive({
  orderNo: '',
  status: '',
  accidentAddress: ''
})

const statusLabels = {
  PENDING: '待派单',
  DISPATCHED: '处理中',
  COMPLETED: '已完成',
  ABORTED: '已中止'
}

function statusLabel(status) {
  return statusLabels[status] || status || '—'
}

function statusBadgeClass(status) {
  if (status === 'PENDING') return 'badge-info'
  if (status === 'DISPATCHED') return 'badge-info'
  if (status === 'COMPLETED') return 'badge-success'
  return 'badge-muted'
}

function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}

function resetFilters() {
  filters.orderNo = ''
  filters.status = ''
  filters.accidentAddress = ''
  loadList()
}

async function loadList() {
  loading.value = true
  error.value = ''
  try {
    const params = {}
    if (filters.orderNo) params.orderNo = filters.orderNo
    if (filters.status) params.status = filters.status
    if (filters.accidentAddress) params.address = filters.accidentAddress
    const res = await listDispatches(params)
    orders.value = res.data?.list || []
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
</style>
