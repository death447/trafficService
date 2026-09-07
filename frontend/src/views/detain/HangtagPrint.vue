<template>
  <div class="hangtag-page">
    <div class="toolbar no-print">
      <button type="button" class="secondary" @click="goBack">返回</button>
      <button type="button" :disabled="!vehicle || loading" @click="onPrint">打印</button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading" class="loading-text">加载中…</p>

    <div v-else-if="vehicle" class="hangtag-card">
      <h1>扣留车辆吊牌</h1>
      <div class="hangtag-body">
        <dl class="fields">
          <div>
            <dt>扣留编号</dt>
            <dd>{{ vehicle.detainNo || '—' }}</dd>
          </div>
          <div>
            <dt>车牌号码</dt>
            <dd class="plate">{{ vehicle.plateNo || '—' }}</dd>
          </div>
          <div>
            <dt>车辆类型</dt>
            <dd>{{ vehicle.vehicleType || '—' }}</dd>
          </div>
          <div>
            <dt>停车场</dt>
            <dd>{{ vehicle.parkingLotName || '—' }}</dd>
          </div>
          <div>
            <dt>扣留部门</dt>
            <dd>{{ vehicle.detainDept || '—' }}</dd>
          </div>
          <div>
            <dt>入库时间</dt>
            <dd>{{ formatTime(vehicle.inTime) }}</dd>
          </div>
        </dl>
        <div class="qr-wrap">
          <canvas ref="qrCanvas" width="160" height="160"></canvas>
          <p class="qr-caption">{{ vehicle.detainNo }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import QRCode from 'qrcode'
import { getDetain } from '../../api/detain'

const route = useRoute()
const router = useRouter()

const vehicle = ref(null)
const loading = ref(false)
const error = ref('')
const qrCanvas = ref(null)

function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}

async function renderQr(text) {
  await nextTick()
  if (!qrCanvas.value || !text) return
  await QRCode.toCanvas(qrCanvas.value, text, {
    width: 160,
    margin: 1,
    errorCorrectionLevel: 'M'
  })
}

async function loadDetail() {
  loading.value = true
  error.value = ''
  try {
    const res = await getDetain(route.params.id)
    vehicle.value = res.data || null
    if (!vehicle.value) {
      error.value = '扣留车辆不存在'
      return
    }
    await renderQr(vehicle.value.detainNo)
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载吊牌失败'
  } finally {
    loading.value = false
  }
}

function onPrint() {
  window.print()
}

function goBack() {
  router.push('/detained-vehicles')
}

onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
.hangtag-page {
  min-height: 100vh;
  padding: 1.5rem;
  background: #f4f7fa;
}

.toolbar {
  display: flex;
  gap: 0.6rem;
  margin-bottom: 1.25rem;
}

.hangtag-card {
  max-width: 640px;
  margin: 0 auto;
  padding: 1.75rem 1.85rem;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
}

.hangtag-card h1 {
  font-size: 1.25rem;
  font-weight: 600;
  text-align: center;
  margin-bottom: 1.35rem;
  letter-spacing: 0.04em;
}

.hangtag-body {
  display: flex;
  gap: 1.5rem;
  align-items: flex-start;
  justify-content: space-between;
}

.fields {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 0;
}

.fields dt {
  font-size: 0.75rem;
  color: var(--text-secondary);
  margin-bottom: 0.15rem;
}

.fields dd {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 500;
}

.fields .plate {
  font-size: 1.15rem;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.qr-wrap {
  text-align: center;
  flex-shrink: 0;
}

.qr-caption {
  margin-top: 0.45rem;
  font-size: 0.75rem;
  color: var(--text-secondary);
  word-break: break-all;
}

@media print {
  .hangtag-page {
    padding: 0;
    background: #fff;
    min-height: auto;
  }

  .no-print {
    display: none !important;
  }

  .hangtag-card {
    max-width: none;
    box-shadow: none;
    border: none;
    border-radius: 0;
    padding: 0;
  }
}
</style>
