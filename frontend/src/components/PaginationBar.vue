<template>
  <div class="pagination-bar">
    <span class="pagination-total">共 {{ total }} 条</span>
    <div class="pagination-nav">
      <button type="button" class="secondary" :disabled="page <= 1" @click="prev">上一页</button>
      <span class="pagination-indicator">第 {{ page }} / {{ totalPages }} 页</span>
      <button type="button" class="secondary" :disabled="page >= totalPages || total === 0" @click="next">
        下一页
      </button>
    </div>
    <label class="pagination-size">
      每页
      <select :value="size" @change="onSizeChange">
        <option v-for="opt in sizeOptions" :key="opt" :value="opt">{{ opt }}</option>
      </select>
      条
    </label>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Number, required: true },
  size: { type: Number, required: true },
  total: { type: Number, required: true }
})

const emit = defineEmits(['update:page', 'update:size'])

const sizeOptions = [10, 20, 50, 100]

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))

function prev() {
  if (props.page > 1) emit('update:page', props.page - 1)
}

function next() {
  if (props.page < totalPages.value && props.total > 0) emit('update:page', props.page + 1)
}

function onSizeChange(e) {
  emit('update:size', Number(e.target.value))
}
</script>

<style scoped>
.pagination-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem 1rem;
  padding: 0.75rem 1rem;
  margin-top: auto;
  flex-shrink: 0;
  position: sticky;
  bottom: 0;
  z-index: 5;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: 0 -4px 12px rgba(15, 23, 42, 0.04);
  font-size: 0.875rem;
  color: var(--text-secondary);
}

.pagination-nav {
  display: flex;
  align-items: center;
  gap: 0.65rem;
}

.pagination-indicator {
  color: var(--text);
  white-space: nowrap;
}

.pagination-size {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  white-space: nowrap;
}

.pagination-size select {
  padding: 0.35rem 0.5rem;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  font-family: inherit;
  font-size: 0.875rem;
  color: var(--text);
  background: var(--bg-surface);
}
</style>
