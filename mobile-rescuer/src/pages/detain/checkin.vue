<template>
  <view class="page">
    <view class="stepper">
      <view class="step" :class="{ done: step > 1, current: step === 1 }">
        <view class="dot">{{ step > 1 ? '✓' : '1' }}</view>
        <text class="step-title">车辆信息</text>
        <text class="step-sub">待录入</text>
      </view>
      <view class="bar" :class="{ on: step > 1 }" />
      <view class="step" :class="{ done: step > 2, current: step === 2 }">
        <view class="dot">{{ step > 2 ? '✓' : '2' }}</view>
        <text class="step-title">施救信息</text>
        <text class="step-sub">待录入</text>
      </view>
      <view class="bar" :class="{ on: step > 2 }" />
      <view class="step" :class="{ done: false, current: step === 3 }">
        <view class="dot">3</view>
        <text class="step-title">停放信息</text>
        <text class="step-sub">待录入</text>
      </view>
    </view>

    <view v-show="step === 1" class="card">
      <view class="field">
        <view class="field-label">扣押编码 *</view>
        <input class="field-input" v-model="form.detainNo" placeholder="请填写强制扣押凭证编码" />
      </view>
      <view class="field">
        <view class="field-label">车牌号码 *</view>
        <input
          class="field-input"
          v-model="form.plateNo"
          placeholder="请填写车牌号码"
          @input="onPlateInput"
          @blur="searchOrders"
        />
        <view v-if="linkedOrder" class="link-row">
          <text class="link-text">已关联 {{ linkedOrder.orderNo }} / {{ statusText(linkedOrder.status) }}</text>
          <text class="link-clear" @click="clearLink">清除</text>
        </view>
      </view>
      <view class="field">
        <view class="field-label">厂牌型号</view>
        <input class="field-input" v-model="form.brandModel" placeholder="请选择或填写厂牌型号" />
      </view>
      <view class="field">
        <view class="field-label">车辆类型</view>
        <picker mode="selector" :range="typeNames" @change="onTypePick">
          <view class="field-input picker-value" :class="{ placeholder: !form.vehicleType }">
            {{ form.vehicleType || '请选择车辆类型' }}
          </view>
        </picker>
      </view>
      <view class="field">
        <view class="field-label">车辆颜色</view>
        <picker mode="selector" :range="colorNames" @change="onColorPick">
          <view class="field-input picker-value" :class="{ placeholder: !form.vehicleColor }">
            {{ form.vehicleColor || '请选择车辆颜色' }}
          </view>
        </picker>
      </view>
      <view class="field">
        <view class="field-label">行驶里程</view>
        <input class="field-input" v-model="form.mileage" placeholder="请填写行驶里程" />
      </view>
      <view class="field">
        <view class="field-label">重要装备</view>
        <input class="field-input" v-model="form.importantEquipment" placeholder="无" />
      </view>
      <view class="field">
        <view class="field-label">有无钥匙</view>
        <view class="radios">
          <view class="radio" :class="{ on: form.hasKey === 'YES' }" @click="form.hasKey = 'YES'">有</view>
          <view class="radio" :class="{ on: form.hasKey === 'NO' }" @click="form.hasKey = 'NO'">无</view>
        </view>
      </view>
    </view>

    <view v-show="step === 2" class="card">
      <view class="field">
        <view class="field-label">扣押部门</view>
        <input class="field-input" v-model="form.detainDept" placeholder="请选择扣押部门" />
      </view>
      <view class="field">
        <view class="field-label">施救人员</view>
        <input class="field-input" v-model="form.rescuerName" placeholder="施救人员" />
      </view>
      <view class="field">
        <view class="field-label">施救原因</view>
        <view class="radios">
          <view
            v-for="r in reasons"
            :key="r.value"
            class="radio"
            :class="{ on: form.rescueReason === r.value }"
            @click="form.rescueReason = r.value"
          >{{ r.label }}</view>
        </view>
      </view>
      <view class="field">
        <view class="field-label">施救方式</view>
        <input class="field-input" v-model="form.rescueMethod" placeholder="请选择施救方式" />
      </view>
      <view class="field">
        <view class="field-label">施救时间</view>
        <input class="field-input" v-model="form.rescueTime" placeholder="yyyy-MM-dd HH:mm:ss" />
      </view>
      <view class="field">
        <view class="field-label">施救地点</view>
        <view class="row-between loc-row">
          <input class="field-input loc-input" v-model="form.rescueAddress" placeholder="请填写施救地址" />
          <view class="loc-btn" @click="fillLocation">定位</view>
        </view>
      </view>
      <view class="field">
        <view class="field-label">现场照片</view>
        <view class="media-grid">
          <image
            v-for="(p, i) in scenePhotos"
            :key="'s' + i"
            class="thumb"
            :src="p"
            mode="aspectFill"
            @click="previewLocal(scenePhotos, i)"
          />
          <view class="thumb add" @click="pickPhoto('scene')">点击上传</view>
        </view>
      </view>
    </view>

    <view v-show="step === 3" class="card">
      <view class="field">
        <view class="field-label">入场编号</view>
        <view class="field-input picker-value placeholder">提交后生成</view>
      </view>
      <view class="field">
        <view class="field-label">停放场地 *</view>
        <picker
          mode="selector"
          :range="lotNames"
          :value="lotIndex"
          :disabled="!lots.length"
          @change="onLotChange"
        >
          <view class="field-input picker-value" :class="{ placeholder: !selectedLotName }">
            {{ selectedLotName || '请选择停车场' }}
          </view>
        </picker>
      </view>
      <view class="field">
        <view class="field-label">停放区域</view>
        <picker
          mode="selector"
          :range="areaNames"
          :value="areaIndex"
          :disabled="!areas.length"
          @change="onAreaChange"
        >
          <view class="field-input picker-value" :class="{ placeholder: !selectedAreaName }">
            {{ selectedAreaName || (areas.length ? '请选择停放区域' : '暂无区域') }}
          </view>
        </picker>
      </view>
      <view class="field">
        <view class="field-label">车位编号</view>
        <input class="field-input" v-model="form.stallNo" placeholder="请填写车位编号" />
      </view>
      <view class="field">
        <view class="field-label">停放照片</view>
        <view class="media-grid">
          <image
            v-for="(p, i) in parkPhotos"
            :key="'p' + i"
            class="thumb"
            :src="p"
            mode="aspectFill"
            @click="previewLocal(parkPhotos, i)"
          />
          <view class="thumb add" @click="pickPhoto('park')">点击上传</view>
        </view>
      </view>
    </view>

    <view class="btn-primary" :class="{ 'btn-disabled': submitting }" @click="onPrimary">
      {{ primaryLabel }}
    </view>

    <view v-if="pickerVisible" class="picker-mask" @click="pickerVisible = false">
      <view class="picker-sheet" @click.stop>
        <view class="picker-title">选择运行中工单</view>
        <view
          v-for="row in pickerRows"
          :key="row.id"
          class="picker-item"
          @click="bindOrder(row)"
        >
          <text>{{ row.orderNo }} · {{ statusText(row.status) }}</text>
          <text class="picker-sub">{{ row.accidentAddress || '—' }}</text>
        </view>
        <view class="picker-cancel" @click="pickerVisible = false">不关联</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { checkInDetain, uploadDetainMedia, listActiveOrders } from '../../api/detain'
import {
  ORDER_STATUS_TEXT,
  applyEmptyOrderFields,
  classifyMatches,
  shouldSearchPlate
} from '../../utils/plateOrder.js'
import { listParkings, listParkingAreas } from '../../api/parking'
import { listEnabledVehicleTypes } from '../../api/vehicleType'
import { getMe } from '../../api/auth'
import { requirePageAccess } from '../../utils/guard.js'

const reasons = [
  { label: '事故', value: 'ACCIDENT' },
  { label: '违法', value: 'ILLEGAL' },
  { label: '救援', value: 'RESCUE' }
]
const colorNames = ['黑', '白', '银', '灰', '红', '蓝', '绿', '黄', '其他']

const step = ref(1)
const form = reactive({
  detainNo: '',
  plateNo: '',
  brandModel: '',
  vehicleType: '',
  vehicleColor: '',
  mileage: '',
  importantEquipment: '无',
  hasKey: '',
  detainDept: '',
  rescuerName: '',
  rescueReason: 'ACCIDENT',
  rescueMethod: '',
  rescueTime: formatNow(),
  rescueAddress: '',
  parkingLotId: '',
  parkingAreaId: '',
  stallNo: ''
})

const linkedOrder = ref(null)
const pickerVisible = ref(false)
const pickerRows = ref([])
let plateTimer = null
let searchSeq = 0

const lots = ref([])
const areas = ref([])
const lotsFailed = ref(false)
const vehicleTypes = ref([])
const scenePhotos = ref([])
const parkPhotos = ref([])
const submitting = ref(false)
const meLoaded = ref(false)

const typeNames = computed(() => vehicleTypes.value.map((t) => t.name))
const lotNames = computed(() => lots.value.map((lot) => lot.name || `停车场#${lot.id}`))
const lotIndex = computed(() => {
  const idx = lots.value.findIndex((lot) => String(lot.id) === String(form.parkingLotId))
  return idx < 0 ? 0 : idx
})
const selectedLotName = computed(() => {
  const lot = lots.value.find((row) => String(row.id) === String(form.parkingLotId))
  return lot ? (lot.name || `停车场#${lot.id}`) : ''
})
const areaNames = computed(() => areas.value.map((a) => a.name || `区域#${a.id}`))
const areaIndex = computed(() => {
  const idx = areas.value.findIndex((a) => String(a.id) === String(form.parkingAreaId))
  return idx < 0 ? 0 : idx
})
const selectedAreaName = computed(() => {
  const area = areas.value.find((row) => String(row.id) === String(form.parkingAreaId))
  return area ? area.name : ''
})
const primaryLabel = computed(() => {
  if (submitting.value) return '提交中…'
  if (step.value < 3) return '下一步'
  return '确定提交'
})

function formatNow() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

onShow(() => {
  if (!requirePageAccess('parking')) return
  loadLots()
  loadTypes()
  loadMe()
})

async function loadMe() {
  if (meLoaded.value && form.rescuerName) return
  try {
    const res = await getMe()
    const data = res.data || {}
    if (!form.rescuerName) form.rescuerName = data.realName || data.username || ''
    meLoaded.value = true
  } catch (_) {}
}

async function loadTypes() {
  try {
    const res = await listEnabledVehicleTypes()
    vehicleTypes.value = res.data || []
  } catch (_) {
    vehicleTypes.value = []
  }
}

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

async function loadAreas(lotId) {
  areas.value = []
  form.parkingAreaId = ''
  if (!lotId) return
  try {
    const res = await listParkingAreas(lotId, { status: 'ENABLED' })
    areas.value = res.data || []
  } catch (_) {
    areas.value = []
  }
}

function onTypePick(e) {
  const i = Number(e.detail.value)
  form.vehicleType = typeNames.value[i] || ''
}

function onColorPick(e) {
  const i = Number(e.detail.value)
  form.vehicleColor = colorNames[i] || ''
}

function onLotChange(e) {
  const idx = Number(e.detail.value)
  const lot = lots.value[idx]
  form.parkingLotId = lot ? String(lot.id) : ''
  loadAreas(lot ? lot.id : null)
}

function onAreaChange(e) {
  const idx = Number(e.detail.value)
  const area = areas.value[idx]
  form.parkingAreaId = area ? String(area.id) : ''
}

function pickPhoto(kind) {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: (res) => {
      const path = res.tempFilePaths?.[0]
      if (!path) return
      if (kind === 'scene') scenePhotos.value = [...scenePhotos.value, path]
      else parkPhotos.value = [...parkPhotos.value, path]
    }
  })
}

function previewLocal(list, index) {
  uni.previewImage({ urls: list, current: list[index] })
}

function fillLocation() {
  const apply = (lng, lat) => {
    const text = `${lat.toFixed(6)},${lng.toFixed(6)}`
    if (!form.rescueAddress) form.rescueAddress = text
    else form.rescueAddress = `${form.rescueAddress} ${text}`
  }
  if (typeof navigator !== 'undefined' && navigator.geolocation) {
    navigator.geolocation.getCurrentPosition(
      (pos) => apply(pos.coords.longitude, pos.coords.latitude),
      () => uni.showToast({ title: '定位失败', icon: 'none' }),
      { enableHighAccuracy: true, timeout: 10000 }
    )
    return
  }
  uni.getLocation({
    type: 'gcj02',
    success: (r) => apply(r.longitude, r.latitude),
    fail: () => uni.showToast({ title: '定位失败', icon: 'none' })
  })
}

function statusText(status) {
  return ORDER_STATUS_TEXT[status] || status || ''
}

function clearLink() {
  linkedOrder.value = null
  pickerVisible.value = false
}

function bindOrder(order) {
  linkedOrder.value = order
  pickerVisible.value = false
  applyEmptyOrderFields(form, order)
}

function onPlateInput() {
  clearLink()
  if (plateTimer) clearTimeout(plateTimer)
  plateTimer = setTimeout(() => { searchOrders() }, 400)
}

async function searchOrders() {
  if (plateTimer) {
    clearTimeout(plateTimer)
    plateTimer = null
  }
  if (!shouldSearchPlate(form.plateNo)) return
  const seq = ++searchSeq
  try {
    const res = await listActiveOrders(String(form.plateNo || '').trim())
    if (seq !== searchSeq) return
    const list = res.data?.list || []
    const decision = classifyMatches(list)
    if (decision.kind === 'one') {
      bindOrder(decision.order)
      return
    }
    if (decision.kind === 'many') {
      pickerRows.value = decision.orders
      pickerVisible.value = true
      return
    }
    uni.showToast({ title: '未找到运行中工单', icon: 'none' })
  } catch (_) {
    /* request.js 已 toast；不打断填表 */
  }
}

function onPrimary() {
  if (submitting.value) return
  if (step.value === 1) {
    if (!String(form.detainNo || '').trim() || !String(form.plateNo || '').trim()) {
      uni.showToast({ title: '请填写扣押编码和车牌', icon: 'none' })
      return
    }
    step.value = 2
    return
  }
  if (step.value === 2) {
    step.value = 3
    return
  }
  onSubmit()
}

function toIso(value) {
  const text = String(value || '').trim()
  if (!text) return null
  return text.replace(' ', 'T')
}

async function onSubmit() {
  if (lotsFailed.value) {
    uni.showToast({ title: '停车场列表加载失败', icon: 'none' })
    return
  }
  const plate = String(form.plateNo || '').trim()
  const detainNo = String(form.detainNo || '').trim()
  const lotId = form.parkingLotId ? Number(form.parkingLotId) : NaN
  if (!detainNo || !plate || !Number.isFinite(lotId)) {
    uni.showToast({ title: '请填写扣押编码、车牌并选择停车场', icon: 'none' })
    return
  }
  const body = {
    detainNo,
    plateNo: plate,
    parkingLotId: lotId
  }
  if (linkedOrder.value && linkedOrder.value.id) {
    body.dispatchOrderId = Number(linkedOrder.value.id)
  }
  const put = (key, val) => {
    const s = String(val || '').trim()
    if (s) body[key] = s
  }
  put('vehicleType', form.vehicleType)
  put('brandModel', form.brandModel)
  put('vehicleColor', form.vehicleColor)
  put('mileage', form.mileage)
  put('importantEquipment', form.importantEquipment)
  put('hasKey', form.hasKey)
  put('detainDept', form.detainDept)
  put('rescuerName', form.rescuerName)
  put('rescueReason', form.rescueReason)
  put('rescueMethod', form.rescueMethod)
  put('rescueAddress', form.rescueAddress)
  put('stallNo', form.stallNo)
  const iso = toIso(form.rescueTime)
  if (iso) body.rescueTime = iso
  if (form.parkingAreaId) body.parkingAreaId = Number(form.parkingAreaId)
  submitting.value = true
  try {
    const res = await checkInDetain(body)
    const data = res.data || {}
    const id = data.id
    if (id) {
      for (const path of scenePhotos.value) {
        try { await uploadDetainMedia(id, path, 'SCENE') } catch (_) {}
      }
      for (const path of parkPhotos.value) {
        try { await uploadDetainMedia(id, path, 'PARK') } catch (_) {}
      }
    }
    uni.showToast({ title: `已入库 ${data.detainNo || detainNo}`, icon: 'success' })
    setTimeout(() => uni.navigateBack(), 400)
  } catch (_) {
    submitting.value = false
  }
}
</script>

<style scoped>
.stepper {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 12rpx 8rpx 28rpx;
}
.step {
  width: 140rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  color: #999;
}
.step.current,
.step.done {
  color: #2979ff;
}
.dot {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  border: 2rpx solid #ccc;
  text-align: center;
  line-height: 44rpx;
  font-size: 24rpx;
  background: #fff;
  margin-bottom: 8rpx;
}
.step.current .dot,
.step.done .dot {
  border-color: #2979ff;
  background: #2979ff;
  color: #fff;
}
.step-title {
  font-size: 24rpx;
}
.step-sub {
  font-size: 20rpx;
  color: #bbb;
}
.bar {
  flex: 1;
  height: 4rpx;
  background: #ddd;
  margin-top: 24rpx;
}
.bar.on {
  background: #2979ff;
}
.picker-value.placeholder {
  color: #999;
  -webkit-text-fill-color: #999;
}
.radios {
  display: flex;
  gap: 24rpx;
}
.radio {
  padding: 12rpx 28rpx;
  border-radius: 8rpx;
  border: 1px solid #ddd;
  color: #666;
  font-size: 28rpx;
}
.radio.on {
  border-color: #2979ff;
  color: #2979ff;
  background: #e8f0ff;
}
.media-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.thumb {
  width: 160rpx;
  height: 160rpx;
  border-radius: 12rpx;
  background: #f0f3f8;
}
.thumb.add {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 24rpx;
  border: 1px dashed #ccc;
}
.loc-row {
  gap: 12rpx;
}
.loc-input {
  flex: 1;
}
.loc-btn {
  color: #2979ff;
  padding: 20rpx 12rpx;
  font-size: 28rpx;
  flex-shrink: 0;
}
.link-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12rpx;
  font-size: 24rpx;
}
.link-text { color: #2979ff; flex: 1; padding-right: 16rpx; }
.link-clear { color: #999; }
.picker-mask {
  position: fixed;
  left: 0; right: 0; top: 0; bottom: 0;
  background: rgba(0,0,0,0.4);
  z-index: 20;
  display: flex;
  align-items: flex-end;
}
.picker-sheet {
  width: 100%;
  background: #fff;
  border-radius: 16rpx 16rpx 0 0;
  padding: 24rpx 32rpx 48rpx;
}
.picker-title { font-size: 30rpx; font-weight: 600; margin-bottom: 16rpx; }
.picker-item {
  padding: 20rpx 0;
  border-bottom: 1px solid #eee;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.picker-sub { color: #999; font-size: 24rpx; }
.picker-cancel { text-align: center; color: #666; padding: 28rpx 0 8rpx; }
</style>
