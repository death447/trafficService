<template>
  <view class="page">
    <view v-for="(vehicle, idx) in vehicles" :key="vehicle.key" class="card">
      <view class="card-head">
        <text class="plate">{{ vehicle.plateNo || '未填写车牌' }}</text>
        <text v-if="vehicles.length > 1" class="card-hint">第 {{ idx + 1 }} / {{ vehicles.length }} 辆车</text>
      </view>

      <view class="field">
        <view class="field-label">事故车辆车牌</view>
        <input
          class="field-input"
          v-model="vehicle.plateNo"
          :disabled="vehicle.accidentVehicleId !== null && vehicle.accidentVehicleId !== undefined"
          placeholder="车牌号"
        />
      </view>
      <view class="field">
        <view class="field-label">车辆类型</view>
        <picker mode="selector" :range="typeNames" @change="(e) => onTypePick(e, vehicle)">
          <view class="field-input" :class="{ placeholder: !vehicle.vehicleType }">
            {{ vehicle.vehicleType || '请选择车型' }}
          </view>
        </picker>
      </view>
      <view class="field">
        <view class="field-label">受损描述</view>
        <textarea class="field-input area" v-model="vehicle.damageDesc" placeholder="受损情况" />
      </view>
      <view class="field">
        <view class="field-label">受损照片</view>
        <view class="media-grid" v-if="vehicle.medias.length">
          <view v-for="m in vehicle.medias" :key="m.id" class="thumb-wrap">
            <image
              class="thumb"
              :src="mediaUrl(m.filePath)"
              mode="aspectFill"
              @click="preview(vehicle, m.filePath)"
            />
            <view class="thumb-del" @click.stop="onDelete(vehicle, m)">×</view>
          </view>
        </view>
        <view class="thumb add" @click="onPick(vehicle)">拍照/选图</view>
      </view>
    </view>

    <view class="card">
      <view class="field">
        <view class="field-label">现场备注</view>
        <textarea class="field-input area" v-model="sceneRemark" placeholder="整单共用的现场备注" />
      </view>
      <view class="btn-primary" @click="onSave">保存现场信息</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getTask, saveScene, listMedia, uploadMedia, deleteMedia } from '../../api/rescuer'
import { listEnabledVehicleTypes } from '../../api/vehicleType'
import { mediaUrl } from '../../utils/request'
import { requirePageAccess } from '../../utils/guard.js'

const id = ref(null)
const vehicles = ref([])
const sceneRemark = ref('')
const vehicleTypes = ref([])
const typeNames = computed(() => vehicleTypes.value.map((t) => t.name))

function onTypePick(e, vehicle) {
  const i = Number(e.detail.value)
  vehicle.vehicleType = typeNames.value[i] || ''
}

onLoad(async (q) => {
  if (!requirePageAccess('rescuer')) return
  id.value = q.id
  await load()
})

async function load() {
  try {
    const [detail, mediaRes, typeRes] = await Promise.all([
      getTask(id.value),
      listMedia(id.value),
      listEnabledVehicleTypes().catch(() => ({ data: [] }))
    ])
    vehicleTypes.value = typeRes.data || []
    const data = detail.data || {}
    const order = data.order
    const fr = data.fieldRecord
    const accidentVehicles = order?.accidentVehicles || []
    const sceneVehicles = data.sceneVehicles || []
    const damageMedias = (mediaRes.data || []).filter((m) => m.bizType === 'DAMAGE')

    sceneRemark.value = fr?.sceneRemark || ''

    if (sceneVehicles.length) {
      vehicles.value = sceneVehicles.map((row) => ({
        key: `sv-${row.id || row.accidentVehicleId}`,
        accidentVehicleId: row.accidentVehicleId ?? null,
        plateNo: row.plateNo || '',
        vehicleType: row.vehicleType || '',
        damageDesc: row.damageDesc || '',
        medias: row.medias || []
      }))
    } else if (accidentVehicles.length) {
      vehicles.value = accidentVehicles.map((av, i) => ({
        key: `av-${av.id ?? i}`,
        accidentVehicleId: av.id ?? null,
        plateNo: av.plateNo || '',
        vehicleType: av.vehicleTypeName || (i === 0 ? fr?.vehicleType || '' : ''),
        damageDesc: i === 0 ? fr?.damageDesc || '' : '',
        medias: damageMedias.filter((m) => m.accidentVehicleId && m.accidentVehicleId === av.id)
      }))
    } else {
      vehicles.value = [
        {
          key: 'single',
          accidentVehicleId: null,
          plateNo: fr?.plateNo || '',
          vehicleType: fr?.vehicleType || '',
          damageDesc: fr?.damageDesc || '',
          medias: damageMedias.filter((m) => !m.accidentVehicleId)
        }
      ]
    }
  } catch (_) {}
}

async function onSave() {
  const payload = vehicles.value.map((v) => ({
    accidentVehicleId: v.accidentVehicleId,
    plateNo: v.plateNo,
    vehicleType: v.vehicleType,
    damageDesc: v.damageDesc
  }))
  try {
    await saveScene(id.value, { sceneRemark: sceneRemark.value, vehicles: payload })
    uni.showToast({ title: '已保存', icon: 'success' })
  } catch (_) {}
}

function onPick(vehicle) {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: async (res) => {
      const path = res.tempFilePaths?.[0]
      if (!path) return
      try {
        await uploadMedia(id.value, path, 'DAMAGE', vehicle.accidentVehicleId)
        uni.showToast({ title: '上传成功', icon: 'success' })
        await reloadMedias()
      } catch (_) {}
    }
  })
}

async function reloadMedias() {
  try {
    const mediaRes = await listMedia(id.value)
    const damageMedias = (mediaRes.data || []).filter((m) => m.bizType === 'DAMAGE')
    for (const vehicle of vehicles.value) {
      vehicle.medias = vehicle.accidentVehicleId
        ? damageMedias.filter((m) => m.accidentVehicleId === vehicle.accidentVehicleId)
        : damageMedias.filter((m) => !m.accidentVehicleId)
    }
  } catch (_) {}
}

function preview(vehicle, filePath) {
  const urls = vehicle.medias.map((m) => mediaUrl(m.filePath))
  uni.previewImage({ urls, current: mediaUrl(filePath) })
}

function onDelete(vehicle, item) {
  uni.showModal({
    title: '删除照片',
    content: '确定删除这张受损照片？',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteMedia(id.value, item.id)
        vehicle.medias = vehicle.medias.filter((m) => m.id !== item.id)
        uni.showToast({ title: '已删除', icon: 'success' })
      } catch (_) {}
    }
  })
}
</script>

<style scoped>
.card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 16rpx;
}
.plate {
  font-size: 32rpx;
  font-weight: 600;
}
.card-hint {
  font-size: 22rpx;
  color: #999;
}
.area {
  min-height: 140rpx;
}
.placeholder {
  color: #999;
}
.media-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 16rpx;
}
.thumb {
  width: 200rpx;
  height: 200rpx;
  border-radius: 12rpx;
  background: #eee;
}
.thumb.add {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 200rpx;
  height: 100rpx;
  color: #2979ff;
  font-size: 26rpx;
  border: 1px dashed #2979ff;
  background: #f5f9ff;
}
.thumb-wrap {
  position: relative;
  width: 200rpx;
  height: 200rpx;
}
.thumb-del {
  position: absolute;
  top: 6rpx;
  right: 6rpx;
  width: 40rpx;
  height: 40rpx;
  line-height: 36rpx;
  text-align: center;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 28rpx;
}
</style>
