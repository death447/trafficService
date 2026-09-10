<template>
  <view class="page">
    <view class="card">
      <view class="field">
        <view class="field-label">车牌 *</view>
        <input class="field-input" v-model="form.plateNo" placeholder="车牌号" />
      </view>
      <view class="field">
        <view class="field-label">停车场 *</view>
        <picker
          mode="selector"
          :range="lotNames"
          :value="lotIndex"
          :disabled="!lots.length"
          @change="onLotChange"
        >
          <view class="field-input picker-value" :class="{ placeholder: !selectedLotName }">
            {{ selectedLotName || '请选择启用停车场' }}
          </view>
        </picker>
      </view>
      <view class="field">
        <view class="field-label">车型</view>
        <input class="field-input" v-model="form.vehicleType" placeholder="如：小型车" />
      </view>
      <view class="field">
        <view class="field-label">关联工单 ID</view>
        <input class="field-input" type="number" v-model="form.dispatchOrderId" placeholder="可选" />
      </view>
      <view class="field">
        <view class="field-label">扣留部门</view>
        <input class="field-input" v-model="form.detainDept" placeholder="可选" />
      </view>
      <view class="field">
        <view class="field-label">备注</view>
        <input class="field-input" v-model="form.remark" placeholder="可选" />
      </view>
      <view class="btn-primary" :class="{ 'btn-disabled': submitting }" @click="onSubmit">
        {{ submitting ? '提交中…' : '入库' }}
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { checkInDetain } from '../../api/detain'
import { listParkings } from '../../api/parking'
import { requirePageAccess } from '../../utils/guard.js'

const form = reactive({
  plateNo: '',
  parkingLotId: '',
  vehicleType: '',
  dispatchOrderId: '',
  detainDept: '',
  remark: ''
})

const lots = ref([])
const lotsFailed = ref(false)
const submitting = ref(false)

const lotNames = computed(() => lots.value.map((lot) => lot.name || `停车场#${lot.id}`))
const lotIndex = computed(() => {
  const idx = lots.value.findIndex((lot) => String(lot.id) === String(form.parkingLotId))
  return idx < 0 ? 0 : idx
})
const selectedLotName = computed(() => {
  const lot = lots.value.find((row) => String(row.id) === String(form.parkingLotId))
  return lot ? (lot.name || `停车场#${lot.id}`) : ''
})

onShow(() => {
  if (!requirePageAccess('parking')) return
  loadLots()
})

async function loadLots() {
  lotsFailed.value = false
  try {
    const res = await listParkings({ status: 'ENABLED', page: 1, size: 100 })
    lots.value = res.data?.list || []
  } catch (_) {
    lots.value = []
    lotsFailed.value = true
    uni.showToast({ title: '停车场列表加载失败', icon: 'none' })
  }
}

function onLotChange(e) {
  const idx = Number(e.detail.value)
  const lot = lots.value[idx]
  form.parkingLotId = lot ? String(lot.id) : ''
}

async function onSubmit() {
  if (submitting.value) return
  if (lotsFailed.value) {
    uni.showToast({ title: '停车场列表加载失败', icon: 'none' })
    return
  }
  const plate = String(form.plateNo || '').trim()
  const lotId = form.parkingLotId ? Number(form.parkingLotId) : NaN
  if (!plate || !Number.isFinite(lotId)) {
    uni.showToast({ title: '请填写车牌并选择停车场', icon: 'none' })
    return
  }
  const body = {
    plateNo: plate,
    parkingLotId: lotId
  }
  const vehicleType = String(form.vehicleType || '').trim()
  const detainDept = String(form.detainDept || '').trim()
  const remark = String(form.remark || '').trim()
  const orderId = String(form.dispatchOrderId || '').trim()
  if (vehicleType) body.vehicleType = vehicleType
  if (detainDept) body.detainDept = detainDept
  if (remark) body.remark = remark
  if (orderId) body.dispatchOrderId = Number(orderId)
  submitting.value = true
  try {
    const res = await checkInDetain(body)
    const data = res.data || {}
    uni.showToast({ title: `已入库 ${data.detainNo}`, icon: 'success' })
    setTimeout(() => uni.navigateBack(), 400)
  } catch (_) {
    submitting.value = false
  }
}
</script>

<style scoped>
.picker-value.placeholder {
  color: #999;
  -webkit-text-fill-color: #999;
}
</style>
