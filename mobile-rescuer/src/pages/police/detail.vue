<template><view class="page muted">加载中…</view></template>
<script setup>
import { onShow } from '@dcloudio/uni-app'
import { requirePageAccess } from '../../utils/guard.js'
onShow(() => { requirePageAccess('police') })
</script>
