<template>
  <view class="page">
    <view v-if="existing" class="card">
      <view class="section-title">该车辆已入库</view>
      <view class="line">扣押编号：{{ existing.detainNo || '-' }}</view>
      <view class="line">入场编号：{{ existing.entryNo || '-' }}</view>
      <view class="line">车牌：{{ existing.plateNo || '-' }}</view>
      <view class="line">停放场地：{{ existingLotName || '-' }}</view>
      <view class="line">入库时间：{{ formatTime(existing.inTime) }}</view>
      <view class="btn-ghost" style="margin-top: 24rpx" @click="goBack">返回</view>
    </view>

    <view v-else>
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
        <view class="step" :class="{ current: step === 3 }">
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
          <view class="plate-row">
            <input class="field-input plate-input" v-model="form.plateNo" placeholder="请填写车牌号码" />
            <text v-if="accidentVehicles.length > 1" class="link" @click="vehiclePickerVisible = true">选择事故车</text>
          </view>
          <view v-if="accidentVehicles.length" class="hint">
            工单事故车：{{ accidentPlates }}
          </view>
        </view>
        <view class="field">
          <view class="field-label">厂牌型号</view>
          <input class="field-input" v-model="form.brandModel" placeholder="请填写厂牌型号" />
        </view>
        <view class="field">
          <view class="field-label">车辆类型</view>
          <input class="field-input" v-model="form.vehicleType" placeholder="请填写车辆类型" />
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
          <picker
            mode="selector"
            :range="detainDeptNames"
            :value="detainDeptIndex"
            :disabled="!detainDeptNames.length"
            @change="onDetainDeptPick"
          >
            <view class="field-input picker-value" :class="{ placeholder: !form.detainDept }">
              {{ form.detainDept || (detainDeptNames.length ? '请选择扣押部门' : '暂无配置') }}
            </view>
          </picker>
        </view>
        <view class="field">
          <view class="field-label">施救人员</view>
          <view class="field-input picker-value">{{ form.rescuerName || '当前账号' }}</view>
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
          <picker
            mode="selector"
            :range="rescueMethodNames"
            :value="rescueMethodIndex"
            :disabled="!rescueMethodNames.length"
            @change="onRescueMethodPick"
          >
            <view class="field-input picker-value" :class="{ placeholder: !form.rescueMethod }">
              {{ form.rescueMethod || (rescueMethodNames.length ? '请选择施救方式' : '暂无配置') }}
            </view>
          </picker>
        </view>
        <view class="field">
          <view class="field-label">施救时间</view>
          <input class="field-input" v-model="form.rescueTime" placeholder="yyyy-MM-dd HH:mm:ss" />
        </view>
        <view class="field">
          <view class="field-label">施救地点</view>
          <view class="loc-row">
            <input class="field-input loc-input" v-model="form.rescueAddress" placeholder="请填写施救地址" />
            <view class="loc-btn" @click="fillLocation">定位</view>
          </view>
        </view>
        <view class="field">
          <view class="field-label">现场照片</view>
          <view class="media-grid">
            <image
              v-for="(m, i) in damagePhotos"
              :key="(m && m.id) || 'd' + i"
              class="thumb"
              :src="mediaUrl(m.filePath)"
              mode="aspectFill"
              @click="previewServer(i)"
            />
            <view class="thumb add" @click="pickScenePhoto">点击上传</view>
          </view>
          <view class="hint">现场照片同时用于工单现场采集，入库后将复制为扣车现场照片</view>
        </view>
      </view>

      <view v-show="step === 3" class="card">
        <view class="field">
          <view class="field-label">入场编号</view>
          <view class="field-input picker-value" :class="{ placeholder: !entryNo }">
            {{ entryNo || '提交后生成' }}
          </view>
          <view class="hint">年月日时 + 工单号 + 随机数，提交时以入库结果为准</view>
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
            <view class="thumb add" @click="pickParkPhoto">点击上传</view>
          </view>
        </view>
        <view class="field">
          <view class="field-label">停放位置说明 / 备注</view>
          <textarea class="field-input area" v-model="form.remark" placeholder="入库备注" />
        </view>
      </view>

      <view class="btn-primary" :class="{ 'btn-disabled': submitting }" @click="onPrimary">
        {{ primaryLabel }}
      </view>
    </view>

    <view v-if="vehiclePickerVisible" class="picker-mask" @click.self="vehiclePickerVisible = false">
      <view class="picker-sheet" @click.stop>
        <view class="picker-title">选择事故车辆</view>
        <view
          v-for="v in accidentVehicles"
          :key="v.id"
          class="picker-item"
          @click="selectVehicle(v)"
        >
          <text>{{ v.plateNo }}</text>
          <text class="picker-sub">{{ v.vehicleTypeName || '—' }}</text>
        </view>
        <view class="picker-cancel" @click="vehiclePickerVisible = false">取消</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  getTask,
  listMedia,
  uploadMedia,
  listRescuerParkingLots,
  listRescuerParkingAreas,
  detainInTask,
  getTaskDetain,
  getTaskEntryNo,
  uploadDetainPhoto,
  listDictOptions
} from '../../api/rescuer'
import { getMe } from '../../api/auth'
import { mediaUrl } from '../../utils/request'
import { requirePageAccess } from '../../utils/guard.js'

const reasons = [
  { label: '事故', value: 'ACCIDENT' },
  { label: '违法', value: 'ILLEGAL' },
  { label: '救援', value: 'RESCUE' }
]
const colorNames = ['黑', '白', '银', '灰', '红', '蓝', '绿', '黄', '其他']

const id = ref(null)
const accidentVehicles = ref([])
const step = ref(1)
const existing = ref(null)
const entryNo = ref('')
const lots = ref([])
const areas = ref([])
const detainDepts = ref([])
const rescueMethods = ref([])
const damagePhotos = ref([])
const parkPhotos = ref([])
const submitting = ref(false)
const vehiclePickerVisible = ref(false)

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
  stallNo: '',
  remark: ''
})

const accidentPlates = computed(() =>
  accidentVehicles.value.map((v) => v.plateNo).filter(Boolean).join('、')
)
const lotNames = computed(() => lots.value.map((lot) => lot.name || `停车场#${lot.id}`))
const lotIndex = computed(() => {
  const idx = lots.value.findIndex((lot) => String(lot.id) === String(form.parkingLotId))
  return idx < 0 ? 0 : idx
})
const selectedLotName = computed(() => {
  const lot = lots.value.find((row) => String(row.id) === String(form.parkingLotId))
  return lot ? (lot.name || `停车场#${lot.id}`) : ''
})
const existingLotName = computed(() => {
  if (!existing.value) return ''
  const lot = lots.value.find((row) => String(row.id) === String(existing.value.parkingLotId))
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
const detainDeptNames = computed(() => detainDepts.value.map((row) => row.name))
const rescueMethodNames = computed(() => rescueMethods.value.map((row) => row.name))
const detainDeptIndex = computed(() => {
  const idx = detainDeptNames.value.indexOf(form.detainDept)
  return idx < 0 ? 0 : idx
})
const rescueMethodIndex = computed(() => {
  const idx = rescueMethodNames.value.indexOf(form.rescueMethod)
  return idx < 0 ? 0 : idx
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

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

onLoad(async (q) => {
  if (!requirePageAccess('rescuer')) return
  id.value = q.id
  await Promise.all([loadTask(), loadLots(), loadMe(), loadExisting(), loadDicts()])
  loadEntryNo()
})

async function loadTask() {
  try {
    const res = await getTask(id.value)
    const o = res.data?.order || null
    accidentVehicles.value = o?.accidentVehicles || []
    if (accidentVehicles.value.length) applyVehicle(accidentVehicles.value[0])
    if (o?.accidentAddress && !form.rescueAddress) form.rescueAddress = o.accidentAddress
    await loadDamage()
  } catch (_) {}
}

function applyVehicle(v) {
  if (!v) return
  form.plateNo = v.plateNo || ''
  if (v.vehicleTypeName) form.vehicleType = v.vehicleTypeName
}

function selectVehicle(v) {
  applyVehicle(v)
  vehiclePickerVisible.value = false
}

async function loadDamage() {
  try {
    const res = await listMedia(id.value)
    damagePhotos.value = (res.data || []).filter((m) => m.bizType === 'DAMAGE')
  } catch (_) {
    damagePhotos.value = []
  }
}

async function loadMe() {
  try {
    const res = await getMe()
    const data = res.data || {}
    form.rescuerName = data.realName || data.username || ''
  } catch (_) {}
}

async function loadExisting() {
  try {
    const res = await getTaskDetain(id.value)
    existing.value = res.data || null
  } catch (_) {
    existing.value = null
  }
}

async function loadEntryNo() {
  try {
    const res = await getTaskEntryNo(id.value)
    entryNo.value = res.data || ''
  } catch (_) {
    entryNo.value = ''
  }
}

async function loadLots() {
  try {
    const res = await listRescuerParkingLots()
    lots.value = res.data || []
  } catch (_) {
    lots.value = []
    uni.showToast({ title: '停车场列表加载失败', icon: 'none' })
  }
}

async function loadDicts() {
  try {
    const [deptRes, methodRes] = await Promise.all([
      listDictOptions('DETAIN_DEPT'),
      listDictOptions('RESCUE_METHOD')
    ])
    detainDepts.value = deptRes.data || []
    rescueMethods.value = methodRes.data || []
  } catch (_) {
    detainDepts.value = []
    rescueMethods.value = []
  }
}

async function loadAreas(lotId) {
  areas.value = []
  form.parkingAreaId = ''
  if (!lotId) return
  try {
    const res = await listRescuerParkingAreas(lotId)
    areas.value = res.data || []
  } catch (_) {
    areas.value = []
  }
}

function onColorPick(e) {
  const i = Number(e.detail.value)
  form.vehicleColor = colorNames[i] || ''
}

function onDetainDeptPick(e) {
  const idx = Number(e.detail.value)
  form.detainDept = detainDeptNames.value[idx] || ''
}

function onRescueMethodPick(e) {
  const idx = Number(e.detail.value)
  form.rescueMethod = rescueMethodNames.value[idx] || ''
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

function pickScenePhoto() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: async (res) => {
      const path = res.tempFilePaths?.[0]
      if (!path) return
      try {
        await uploadMedia(id.value, path, 'DAMAGE')
        uni.showToast({ title: '上传成功', icon: 'success' })
        await loadDamage()
      } catch (_) {}
    }
  })
}

function pickParkPhoto() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: (res) => {
      const path = res.tempFilePaths?.[0]
      if (!path) return
      parkPhotos.value = [...parkPhotos.value, path]
    }
  })
}

function previewLocal(list, index) {
  uni.previewImage({ urls: list, current: list[index] })
}

function previewServer(index) {
  const urls = damagePhotos.value.map((m) => mediaUrl(m.filePath))
  uni.previewImage({ urls, current: urls[index] })
}

function fillLocation() {
  const apply = (lng, lat) => {
    const text = `${lat.toFixed(6)},${lng.toFixed(6)}`
    form.rescueAddress = form.rescueAddress ? `${form.rescueAddress} ${text}` : text
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

function goBack() {
  uni.navigateBack()
}

async function onSubmit() {
  const plate = String(form.plateNo || '').trim()
  const detainNo = String(form.detainNo || '').trim()
  const lotId = Number(form.parkingLotId)
  if (!detainNo || !plate || !Number.isFinite(lotId) || !lotId) {
    uni.showToast({ title: '请填写扣押编码、车牌并选择停车场', icon: 'none' })
    return
  }
  const body = { detainNo, plateNo: plate, parkingLotId: lotId }
  if (entryNo.value) body.entryNo = entryNo.value
  if (form.parkingAreaId) body.parkingAreaId = Number(form.parkingAreaId)
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
  put('rescueReason', form.rescueReason)
  put('rescueMethod', form.rescueMethod)
  put('rescueAddress', form.rescueAddress)
  put('stallNo', form.stallNo)
  put('remark', form.remark)
  const iso = toIso(form.rescueTime)
  if (iso) body.rescueTime = iso

  submitting.value = true
  try {
    const res = await detainInTask(id.value, body)
    const created = res.data || {}
    const detainId = created.id
    if (detainId) {
      for (const path of parkPhotos.value) {
        try {
          await uploadDetainPhoto(detainId, path)
        } catch (_) {}
      }
    }
    uni.showToast({ title: `已入库 ${created.detainNo || detainNo}`, icon: 'success' })
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
.plate-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.plate-input {
  flex: 1;
}
.link {
  color: #2979ff;
  font-size: 26rpx;
  flex-shrink: 0;
}
.hint {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #999;
  line-height: 1.5;
}
.area {
  min-height: 140rpx;
}
.line {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #333;
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
  display: flex;
  align-items: center;
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
