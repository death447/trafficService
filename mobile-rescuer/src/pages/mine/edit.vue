<template>
  <view class="page">
    <view class="card">
      <view class="field">
        <view class="field-label">真实姓名</view>
        <input class="field-input" v-model="form.realName" placeholder="真实姓名" />
      </view>
      <view class="field">
        <view class="field-label">手机号</view>
        <input class="field-input" v-model="form.phone" placeholder="手机号" />
      </view>
      <view class="field">
        <view class="field-label">邮箱</view>
        <input class="field-input" v-model="form.email" placeholder="邮箱" />
      </view>
      <view class="btn-primary" @click="onSave">保存</view>
    </view>
  </view>
</template>

<script setup>
import { reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getProfile, updateProfile } from '../../api/rescuer'

const form = reactive({
  realName: '',
  phone: '',
  email: ''
})

onLoad(async () => {
  try {
    const res = await getProfile()
    form.realName = res.data?.realName || ''
    form.phone = res.data?.phone || ''
    form.email = res.data?.email || ''
  } catch (_) {}
})

async function onSave() {
  try {
    await updateProfile({
      realName: form.realName,
      phone: form.phone,
      email: form.email
    })
    uni.showToast({ title: '已保存', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 400)
  } catch (_) {}
}
</script>
