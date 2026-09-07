<template>
  <view class="page">
    <view class="card">
      <view class="field">
        <view class="field-label">事故车辆车牌</view>
        <input class="field-input" v-model="form.plateNo" placeholder="车牌号" />
      </view>
      <view class="field">
        <view class="field-label">车辆类型</view>
        <input class="field-input" v-model="form.vehicleType" placeholder="如轿车/货车" />
      </view>
      <view class="field">
        <view class="field-label">受损描述</view>
        <textarea class="field-input area" v-model="form.damageDesc" placeholder="受损情况" />
      </view>
      <view class="field">
        <view class="field-label">现场备注</view>
        <textarea class="field-input area" v-model="form.sceneRemark" placeholder="备注" />
      </view>
      <view class="btn-primary" @click="onSave">保存现场信息</view>
    </view>

    <view class="card">
      <view class="row-between" style="margin-bottom: 20rpx">
        <text class="section-title">受损照片</text>
        <text class="link" @click="onPick">拍照/选图</text>
      </view>
      <view class="media-grid" v-if="medias.length">
        <image
          v-for="m in medias"
          :key="m.id"
          class="thumb"
          :src="mediaUrl(m.filePath)"
          mode="aspectFill"
          @click="preview(m.filePath)"
        />
      </view>
      <view v-else class="muted">暂无照片</view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getTask, saveScene, listMedia, uploadMedia } from '../../api/rescuer'
import { mediaUrl } from '../../utils/request'

const id = ref(null)
const form = reactive({
  plateNo: '',
  vehicleType: '',
  damageDesc: '',
  sceneRemark: ''
})
const medias = ref([])

onLoad(async (q) => {
  id.value = q.id
  await load()
})

async function load() {
  try {
    const [detail, mediaRes] = await Promise.all([getTask(id.value), listMedia(id.value)])
    const fr = detail.data?.fieldRecord
    if (fr) {
      form.plateNo = fr.plateNo || ''
      form.vehicleType = fr.vehicleType || ''
      form.damageDesc = fr.damageDesc || ''
      form.sceneRemark = fr.sceneRemark || ''
    }
    medias.value = (mediaRes.data || []).filter((m) => m.bizType === 'DAMAGE')
  } catch (_) {}
}

async function onSave() {
  try {
    await saveScene(id.value, { ...form })
    uni.showToast({ title: '已保存', icon: 'success' })
  } catch (_) {}
}

function onPick() {
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
        const mediaRes = await listMedia(id.value)
        medias.value = (mediaRes.data || []).filter((m) => m.bizType === 'DAMAGE')
      } catch (_) {}
    }
  })
}

function preview(filePath) {
  const urls = medias.value.map((m) => mediaUrl(m.filePath))
  uni.previewImage({ urls, current: mediaUrl(filePath) })
}
</script>

<style scoped>
.area {
  min-height: 140rpx;
}
.section-title {
  font-weight: 600;
}
.link {
  color: #2979ff;
}
.media-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.thumb {
  width: 200rpx;
  height: 200rpx;
  border-radius: 12rpx;
  background: #eee;
}
</style>
