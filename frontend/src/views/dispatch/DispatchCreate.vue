<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>新建工单</h1>
        <p class="subtitle">填写事故信息并提交派单申请</p>
      </div>
      <button type="button" class="secondary" @click="goBack">返回列表</button>
    </div>

    <p v-if="!amapReady" class="hint">未配置 VITE_AMAP_KEY，请手填坐标。</p>
    <p v-if="mapError" class="error">{{ mapError }}</p>
    <p v-if="lookupError" class="error">{{ lookupError }}</p>

    <form class="panel form-panel" @submit.prevent="onSubmit">
      <label>
        施救原因
        <textarea v-model.trim="form.rescueReason" rows="3" required placeholder="简要描述事故原因" />
      </label>
      <label>
        事故地点
        <input
          ref="searchInput"
          v-model.trim="form.accidentAddress"
          required
          placeholder="可搜索地点，或点击/拖动地图标记选点"
        />
      </label>
      <div class="coord-row">
        <label>
          经度
          <input v-model.trim="form.longitude" type="number" step="any" required />
        </label>
        <label>
          纬度
          <input v-model.trim="form.latitude" type="number" step="any" required />
        </label>
      </div>

      <label>
        调度员
        <select v-model="form.dispatcherId" required>
          <option value="" disabled>请选择调度员</option>
          <option v-for="u in dispatcherOptions" :key="u.id" :value="String(u.id)">
            {{ userDisplay(u) }}
          </option>
        </select>
      </label>
      <label>
        施救员
        <select v-model="form.rescuerId">
          <option value="">暂不指定</option>
          <option v-for="u in rescuerOptions" :key="u.id" :value="String(u.id)">
            {{ userDisplay(u) }}
          </option>
        </select>
      </label>
      <label>
        救援车辆
        <select v-model="form.vehicleId">
          <option value="">暂不指定</option>
          <option v-for="v in vehicles" :key="v.id" :value="String(v.id)">
            {{ vehicleDisplay(v) }}
          </option>
        </select>
      </label>

      <div v-if="amapReady" class="map-wrap">
        <p class="map-hint">可搜索地点、点击或拖动标记选点</p>
        <div ref="mapEl" class="map-box" />
      </div>

      <p v-if="formError" class="error">{{ formError }}</p>
      <div class="form-actions">
        <button type="button" class="secondary" @click="goBack">取消</button>
        <button type="submit" :disabled="saving">{{ saving ? '提交中…' : '提交工单' }}</button>
      </div>
    </form>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createDispatch } from '../../api/dispatch'
import { getUserList } from '../../api/user'
import { listVehicles } from '../../api/vehicle'
import { useUserStore } from '../../stores/user'
import { createPickerMap, hasAmapKey } from '../../utils/amap'

const router = useRouter()
const userStore = useUserStore()
const mapEl = ref(null)
const searchInput = ref(null)
const amapReady = hasAmapKey()
const mapError = ref('')
const lookupError = ref('')
const formError = ref('')
const saving = ref(false)
const users = ref([])
const vehicles = ref([])
let mapInstance = null

const vehicleStatusLabels = {
  IDLE: '空闲',
  BUSY: '忙碌',
  OFFLINE: '离线'
}

const form = reactive({
  rescueReason: '',
  accidentAddress: '',
  longitude: '',
  latitude: '',
  dispatcherId: userStore.userId != null ? String(userStore.userId) : '',
  rescuerId: '',
  vehicleId: ''
})

const dispatcherOptions = computed(() =>
  users.value.filter((u) => {
    if (u.status !== 1) return false
    const roles = u.roles || []
    return roles.some((r) => r.roleCode === 'DISPATCHER' || r.roleCode === 'ADMIN')
  })
)

const rescuerOptions = computed(() =>
  users.value.filter((u) => {
    if (u.status !== 1) return false
    const roles = u.roles || []
    return roles.some((r) => r.roleCode === 'TOW_DRIVER')
  })
)

function userDisplay(u) {
  if (!u) return '—'
  return u.realName ? `${u.realName}（${u.username}）` : u.username
}

function vehicleDisplay(v) {
  const status = vehicleStatusLabels[v.status] || v.status || '—'
  return `${v.plateNo}（${status}）`
}

function toNullableId(value) {
  if (value === '' || value == null) return null
  const n = Number(value)
  return Number.isNaN(n) ? null : n
}

function goBack() {
  router.push('/dispatches')
}

async function loadLookups() {
  lookupError.value = ''
  const results = await Promise.allSettled([
    getUserList({ size: 500 }),
    listVehicles({})
  ])
  const [userResult, vehicleResult] = results
  if (userResult.status === 'fulfilled') {
    users.value = userResult.value.data?.list || []
  }
  if (vehicleResult.status === 'fulfilled') {
    vehicles.value = vehicleResult.value.data?.list || []
  }
  const failed = results.filter((r) => r.status === 'rejected')
  if (failed.length === results.length) {
    const first = failed[0].reason
    lookupError.value =
      first?.response?.data?.message || first?.message || '加载基础数据失败'
  } else if (failed.length) {
    const first = failed[0].reason
    lookupError.value =
      first?.response?.data?.message || first?.message || '部分基础数据加载失败'
  }
}

async function initMap() {
  if (!amapReady || !mapEl.value) return
  try {
    mapInstance = await createPickerMap(mapEl.value, {
      searchInput: searchInput.value,
      onPicked({ lng, lat, address }) {
        form.longitude = String(lng)
        form.latitude = String(lat)
        if (address) form.accidentAddress = address
      }
    })
  } catch (e) {
    mapError.value = e.message || '地图加载失败，请手填坐标'
  }
}

async function onSubmit() {
  formError.value = ''
  saving.value = true
  try {
    const res = await createDispatch({
      rescueReason: form.rescueReason,
      accidentAddress: form.accidentAddress,
      longitude: Number(form.longitude),
      latitude: Number(form.latitude),
      dispatcherId: toNullableId(form.dispatcherId),
      rescuerId: toNullableId(form.rescuerId),
      vehicleId: toNullableId(form.vehicleId)
    })
    const id = res.data?.id
    if (id != null) {
      router.push(`/dispatches/${id}`)
    } else {
      router.push('/dispatches')
    }
  } catch (e) {
    formError.value = e.response?.data?.message || e.message || '创建工单失败'
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadLookups()
  await nextTick()
  await initMap()
})

onBeforeUnmount(() => {
  if (mapInstance && typeof mapInstance.destroy === 'function') {
    mapInstance.destroy()
  }
  mapInstance = null
})
</script>

<style scoped>
.hint {
  margin-bottom: 0.85rem;
  padding: 0.65rem 0.85rem;
  background: #fff8e8;
  border: 1px solid #f0d78c;
  border-radius: var(--radius);
  color: #8a6a12;
  font-size: 0.85rem;
}

.form-panel {
  padding: 1.25rem 1.35rem;
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  max-width: 720px;
}

.form-panel label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.form-panel input,
.form-panel textarea,
.form-panel select {
  padding: 0.5rem 0.65rem;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  font-family: inherit;
  font-size: 0.875rem;
  color: var(--text);
  background: #fff;
}

.coord-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.85rem;
}

.map-wrap {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.map-hint {
  font-size: 0.8rem;
  color: var(--text-secondary);
}

.map-box {
  height: 320px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--bg-muted);
}

.form-actions {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
  margin-top: 0.35rem;
}

@media (max-width: 640px) {
  .coord-row {
    grid-template-columns: 1fr;
  }
}
</style>
