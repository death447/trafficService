<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>工单详情</h1>
        <p class="subtitle">加载中 / 下一任务实现</p>
      </div>
      <button type="button" class="secondary" @click="$router.push('/dispatches')">返回列表</button>
    </div>
    <div class="panel placeholder">
      <p>工单 #{{ id }} 详情页占位，完整派单/完成/中止能力将在下一任务实现。</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const id = computed(() => route.params.id)
</script>

<style scoped>
.placeholder {
  padding: 1.5rem;
  color: var(--text-secondary);
  font-size: 0.9rem;
}
</style>
