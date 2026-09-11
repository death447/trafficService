<template>
  <div class="page report">
    <div class="page-header">
      <div>
        <h1>报表</h1>
        <p class="subtitle">按日期汇总调度运营、服务质量与停车场扣留</p>
      </div>
    </div>

    <div class="panel filters">
      <label>
        开始日期
        <input v-model="from" type="date" />
      </label>
      <label>
        结束日期
        <input v-model="to" type="date" />
      </label>
      <div class="filter-actions">
        <button type="button" :disabled="loading" @click="onQuery">查询</button>
        <button type="button" class="secondary" :disabled="exporting" @click="onExport">
          {{ exporting ? '导出中…' : '导出 Excel' }}
        </button>
      </div>
    </div>

    <p v-if="queryError" class="error">{{ queryError }}</p>
    <p v-if="exportError" class="error">{{ exportError }}</p>
    <p v-if="loading" class="loading-text">加载中…</p>

    <template v-if="summary">
      <section class="report-section">
        <h2 class="section-title">调度运营</h2>
        <div class="stat-row">
          <div class="stat-card">
            <span class="stat-kicker">区间</span>
            <span class="stat-label">工单总数</span>
            <strong>{{ summary.dispatch?.total ?? 0 }}</strong>
          </div>
          <div class="stat-card success">
            <span class="stat-kicker">区间</span>
            <span class="stat-label">已完成</span>
            <strong>{{ summary.dispatch?.completed ?? 0 }}</strong>
          </div>
          <div class="stat-card accent">
            <span class="stat-kicker">区间</span>
            <span class="stat-label">处置中</span>
            <strong>{{ summary.dispatch?.inProgress ?? 0 }}</strong>
          </div>
          <div class="stat-card">
            <span class="stat-kicker">区间</span>
            <span class="stat-label">中止</span>
            <strong>{{ summary.dispatch?.aborted ?? 0 }}</strong>
          </div>
        </div>
        <div class="chart-grid">
          <div class="panel chart-panel">
            <h3 class="chart-title">按日工单量</h3>
            <div v-if="hasDispatchData" ref="trendEl" class="chart-box" />
            <div v-else class="chart-empty">暂无数据</div>
          </div>
          <div class="panel chart-panel">
            <h3 class="chart-title">状态分布</h3>
            <div v-if="hasDispatchData" ref="statusEl" class="chart-box" />
            <div v-else class="chart-empty">暂无数据</div>
          </div>
        </div>
        <div class="panel">
          <div class="table-scroll">
            <table class="data-table">
              <thead>
                <tr>
                  <th>单号</th>
                  <th>事故地址</th>
                  <th>状态</th>
                  <th>车牌</th>
                  <th>施救员</th>
                  <th>创建时间</th>
                  <th>派单时间</th>
                  <th>完成时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in orderRows" :key="row.id">
                  <td>
                    <router-link :to="'/dispatches/' + row.id">{{ row.orderNo }}</router-link>
                  </td>
                  <td>{{ row.accidentAddress || '—' }}</td>
                  <td>
                    <span :class="['badge', statusBadgeClass(row.status)]">
                      {{ statusLabel(row.status) }}
                    </span>
                  </td>
                  <td>{{ row.plateNo || '—' }}</td>
                  <td>{{ row.rescuerName || '—' }}</td>
                  <td>{{ formatTime(row.createTime) }}</td>
                  <td>{{ formatTime(row.dispatchedAt) }}</td>
                  <td>{{ formatTime(row.completedAt) }}</td>
                </tr>
                <tr v-if="!orderRows.length">
                  <td colspan="8" class="empty-cell">暂无数据</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <section class="report-section">
        <h2 class="section-title">服务质量</h2>
        <div class="stat-row cols-5">
          <div class="stat-card">
            <span class="stat-kicker">区间</span>
            <span class="stat-label">已评价数</span>
            <strong>{{ summary.quality?.ratedCount ?? 0 }}</strong>
          </div>
          <div class="stat-card">
            <span class="stat-kicker">均分</span>
            <span class="stat-label">到达及时</span>
            <strong>{{ fmtAvg(summary.quality?.avgPunctual) }}</strong>
          </div>
          <div class="stat-card">
            <span class="stat-kicker">均分</span>
            <span class="stat-label">处置规范</span>
            <strong>{{ fmtAvg(summary.quality?.avgStandard) }}</strong>
          </div>
          <div class="stat-card">
            <span class="stat-kicker">均分</span>
            <span class="stat-label">操作安全</span>
            <strong>{{ fmtAvg(summary.quality?.avgSafety) }}</strong>
          </div>
          <div class="stat-card">
            <span class="stat-kicker">均分</span>
            <span class="stat-label">服务态度</span>
            <strong>{{ fmtAvg(summary.quality?.avgAttitude) }}</strong>
          </div>
        </div>
        <div class="panel chart-panel">
          <h3 class="chart-title">四维均分</h3>
          <div v-if="hasQualityData" ref="scoreEl" class="chart-box" />
          <div v-else class="chart-empty">暂无数据</div>
        </div>
        <div class="panel">
          <div class="table-scroll">
            <table class="data-table">
              <thead>
                <tr>
                  <th>姓名</th>
                  <th>评价单数</th>
                  <th>到达及时</th>
                  <th>处置规范</th>
                  <th>操作安全</th>
                  <th>服务态度</th>
                  <th>综合均分</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in qualityRows" :key="row.rescuerId ?? row.rescuerName">
                  <td>{{ row.rescuerName || '未指定' }}</td>
                  <td>{{ row.ratedCount ?? 0 }}</td>
                  <td>{{ fmtAvg(row.avgPunctual) }}</td>
                  <td>{{ fmtAvg(row.avgStandard) }}</td>
                  <td>{{ fmtAvg(row.avgSafety) }}</td>
                  <td>{{ fmtAvg(row.avgAttitude) }}</td>
                  <td>{{ fmtAvg(row.avgOverall) }}</td>
                </tr>
                <tr v-if="!qualityRows.length">
                  <td colspan="7" class="empty-cell">暂无数据</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <section class="report-section">
        <h2 class="section-title">停车场扣留</h2>
        <div class="stat-row cols-3">
          <div class="stat-card">
            <span class="stat-kicker">区间</span>
            <span class="stat-label">入库</span>
            <strong>{{ summary.detain?.inbound ?? 0 }}</strong>
          </div>
          <div class="stat-card accent">
            <span class="stat-kicker">当前</span>
            <span class="stat-label">在场</span>
            <strong>{{ summary.detain?.inYard ?? 0 }}</strong>
          </div>
          <div class="stat-card">
            <span class="stat-kicker">区间</span>
            <span class="stat-label">出库</span>
            <strong>{{ summary.detain?.outbound ?? 0 }}</strong>
          </div>
        </div>
        <div class="panel">
          <div class="table-scroll">
            <table class="data-table">
              <thead>
                <tr>
                  <th>名称</th>
                  <th>入库</th>
                  <th>在场</th>
                  <th>出库</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in lotRows" :key="row.parkingLotId ?? row.parkingLotName">
                  <td>{{ row.parkingLotName || '—' }}</td>
                  <td>{{ row.inbound ?? 0 }}</td>
                  <td>{{ row.inYard ?? 0 }}</td>
                  <td>{{ row.outbound ?? 0 }}</td>
                </tr>
                <tr v-if="!lotRows.length">
                  <td colspan="4" class="empty-cell">暂无数据</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { getReportSummary, exportReport } from '../../api/report'

const now = new Date()
const from = ref(toYmd(new Date(now.getFullYear(), now.getMonth(), 1)))
const to = ref(toYmd(now))

const summary = ref(null)
const queryError = ref('')
const exportError = ref('')
const loading = ref(false)
const exporting = ref(false)

const trendEl = ref(null)
const statusEl = ref(null)
const scoreEl = ref(null)
let trendChart = null
let statusChart = null
let scoreChart = null

const statusLabels = {
  PENDING: '待派单',
  DISPATCHED: '已派单',
  ACCEPTED: '已接单',
  COMPLETED: '已完成',
  ABORTED: '已中止'
}

const hasDispatchData = computed(() => (summary.value?.dispatch?.total || 0) > 0)
const hasQualityData = computed(() => (summary.value?.quality?.ratedCount || 0) > 0)
const orderRows = computed(() => (summary.value?.dispatch?.orders || []).slice(0, 50))
const qualityRows = computed(() => summary.value?.quality?.byRescuer || [])
const lotRows = computed(() => summary.value?.detain?.byLot || [])

function formatTime(v) {
  if (!v) return '—'
  const s = String(v).replace('T', ' ')
  return s.length >= 16 ? s.slice(0, 16) : s
}

function toYmd(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function inclusiveDays(fromDate, toDate) {
  const a = new Date(fromDate + 'T00:00:00')
  const b = new Date(toDate + 'T00:00:00')
  return Math.round((b - a) / 86400000) + 1
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

function fmtAvg(v) {
  return v == null ? '—' : Number(v).toFixed(1)
}

function validateRange(fromDate, toDate) {
  if (!fromDate || !toDate) return '请选择开始和结束日期'
  if (fromDate > toDate) return '开始日期不能晚于结束日期'
  if (inclusiveDays(fromDate, toDate) > 366) return '查询区间不能超过 366 天'
  return ''
}

function disposeChart(chart) {
  if (chart) chart.dispose()
  return null
}

function disposeAllCharts() {
  trendChart = disposeChart(trendChart)
  statusChart = disposeChart(statusChart)
  scoreChart = disposeChart(scoreChart)
}

function renderCharts() {
  const d = summary.value?.dispatch
  const q = summary.value?.quality

  if (!d || d.total === 0) {
    trendChart = disposeChart(trendChart)
    statusChart = disposeChart(statusChart)
  } else {
    const trend = d.trend || []
    const dist = d.statusDist || []
    if (trendEl.value) {
      if (!trendChart) trendChart = echarts.init(trendEl.value)
      trendChart.setOption(
        {
          color: ['#1f6fa8'],
          grid: { left: 44, right: 16, top: 24, bottom: 36 },
          tooltip: { trigger: 'axis' },
          xAxis: { type: 'category', data: trend.map((i) => i.date) },
          yAxis: { type: 'value', minInterval: 1 },
          series: [{ type: 'line', data: trend.map((i) => i.count), smooth: true }]
        },
        true
      )
    }
    if (statusEl.value) {
      if (!statusChart) statusChart = echarts.init(statusEl.value)
      statusChart.setOption(
        {
          color: ['#1f6fa8'],
          grid: { left: 44, right: 16, top: 24, bottom: 36 },
          tooltip: { trigger: 'axis' },
          xAxis: { type: 'category', data: dist.map((i) => statusLabel(i.status)) },
          yAxis: { type: 'value', minInterval: 1 },
          series: [{ type: 'bar', data: dist.map((i) => i.count) }]
        },
        true
      )
    }
  }

  if (!q || q.ratedCount === 0) {
    scoreChart = disposeChart(scoreChart)
  } else if (scoreEl.value) {
    if (!scoreChart) scoreChart = echarts.init(scoreEl.value)
    const dims = [
      { name: '到达及时', v: q.avgPunctual },
      { name: '处置规范', v: q.avgStandard },
      { name: '操作安全', v: q.avgSafety },
      { name: '服务态度', v: q.avgAttitude }
    ]
    scoreChart.setOption(
      {
        color: ['#1f6fa8'],
        grid: { left: 44, right: 16, top: 24, bottom: 36 },
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: dims.map((i) => i.name) },
        yAxis: { type: 'value', min: 0, max: 5 },
        series: [{ type: 'bar', data: dims.map((i) => (i.v == null ? 0 : Number(i.v))) }]
      },
      true
    )
  }
}

async function onQuery() {
  queryError.value = ''
  const err = validateRange(from.value, to.value)
  if (err) {
    queryError.value = err
    return
  }
  loading.value = true
  try {
    const res = await getReportSummary({ from: from.value, to: to.value })
    const payload = res.data
    if ((payload?.dispatch?.total || 0) === 0) {
      trendChart = disposeChart(trendChart)
      statusChart = disposeChart(statusChart)
    }
    if ((payload?.quality?.ratedCount || 0) === 0) {
      scoreChart = disposeChart(scoreChart)
    }
    summary.value = payload
    await nextTick()
    renderCharts()
  } catch (e) {
    queryError.value = e.response?.data?.message || e.message || '查询失败'
  } finally {
    loading.value = false
  }
}

async function onExport() {
  exportError.value = ''
  const err = validateRange(from.value, to.value)
  if (err) {
    exportError.value = err
    return
  }
  exporting.value = true
  try {
    const res = await exportReport({ from: from.value, to: to.value })
    const blob = res.data
    const header = String(res.headers?.['content-type'] || res.headers?.['Content-Type'] || '')
    const blobType = blob?.type || ''
    if (/json/i.test(header) || /json/i.test(blobType)) {
      try {
        const body = JSON.parse(await blob.text())
        exportError.value = body.message || '导出失败'
      } catch (_) {
        exportError.value = '导出失败'
      }
      return
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `report_${from.value}_${to.value}.xlsx`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  } catch (e) {
    exportError.value = e.response?.data?.message || e.message || '导出失败'
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  onQuery()
})

onBeforeUnmount(() => {
  disposeAllCharts()
})
</script>

<style scoped>
.report {
  display: flex;
  flex-direction: column;
  gap: 0;
}

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

.filters input {
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

.report-section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.section-title {
  font-size: 0.95rem;
  font-weight: 600;
  margin: 0;
}

.stat-label {
  display: block;
  font-size: 0.75rem;
  color: var(--text-secondary);
  margin-bottom: 0.35rem;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
}

.stat-row.cols-5 {
  grid-template-columns: repeat(5, 1fr);
}

.stat-row.cols-3 {
  grid-template-columns: repeat(3, 1fr);
}

.stat-card {
  padding: 1.1rem 1.2rem;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
}

.stat-kicker {
  display: block;
  font-size: 0.7rem;
  letter-spacing: 0.08em;
  color: var(--text-secondary);
  margin-bottom: 0.2rem;
}

.stat-card .stat-label {
  margin-bottom: 0.45rem;
}

.stat-card strong {
  font-size: 1.85rem;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--text);
}

.stat-card.accent strong {
  color: var(--accent-hover);
}

.stat-card.success strong {
  color: var(--success);
}

.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.chart-panel {
  padding: 0.85rem 1rem 1rem;
}

.chart-title {
  margin: 0 0 0.5rem;
  font-size: 0.85rem;
  font-weight: 600;
}

.chart-box {
  height: 280px;
}

.chart-empty {
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.table-scroll {
  overflow-x: auto;
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

@media (max-width: 960px) {
  .stat-row,
  .stat-row.cols-5,
  .stat-row.cols-3 {
    grid-template-columns: 1fr 1fr;
  }

  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
