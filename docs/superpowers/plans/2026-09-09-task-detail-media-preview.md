# Task Detail Media Preview (Mobile Rescuer) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On the mobile rescuer task detail page, show read-only DAMAGE and PARK photo thumbnails under「现场摘要」, with tap-to-preview.

**Architecture:** Full (non-silent) `load()` fetches `getTask` and `listMedia` in parallel; split by `bizType` into `damageMedias` / `parkMedias`. Silent location poll keeps calling only `getTask`. UI reuses `mediaUrl` + `uni.previewImage` patterns from `scene.vue` / `park.vue`. No backend changes.

**Tech Stack:** uni-app Vue 3, existing `listMedia` / `mediaUrl` helpers

**Spec:** `docs/superpowers/specs/2026-09-09-task-detail-media-preview-design.md`

## Global Constraints

- Detail is **read-only** for media (no upload / delete on this page)
- Photos live **inside** the existing「现场摘要」card (`v-if="fieldRecord"`)
- Empty groups are **hidden**; if both empty, omit the entire photo block (no「暂无照片」)
- Silent poll `load({ silent: true })` must **not** call `listMedia`
- Media fetch failure: keep order / fieldRecord; set media lists to `[]`
- Thumbnail style aligned with scene/park (~200rpx, `aspectFill`, `border-radius: 12rpx`)
- No shared MediaGrid extraction; no backend / DTO changes

## File Structure

- Modify: `mobile-rescuer/src/pages/task/detail.vue` — template, load, preview helpers, styles
- Reuse (no edit required): `mobile-rescuer/src/api/rescuer.js` (`listMedia`), `mobile-rescuer/src/utils/request.js` (`mediaUrl`)

---

### Task 1: Detail page media load + display + preview

**Files:**
- Modify: `mobile-rescuer/src/pages/task/detail.vue`

**Interfaces:**
- Consumes: `listMedia(id)` → `{ data: DispatchMedia[] }`; `mediaUrl(filePath)` → absolute URL string; media items with `id`, `filePath`, `bizType` (`DAMAGE` | `PARK`)
- Produces: page state `damageMedias`, `parkMedias`; `previewMedias(list, filePath)` for `uni.previewImage`

- [ ] **Step 1: Add imports and media state**

In `detail.vue` script, extend the rescuer API import and add `mediaUrl`:

```js
import {
  getTask,
  acceptTask,
  rejectTask,
  checkinTask,
  completeTask,
  listMedia
} from '../../api/rescuer'
import { mediaUrl } from '../../utils/request'
```

After `fieldRecord` ref, add:

```js
const damageMedias = ref([])
const parkMedias = ref([])
```

- [ ] **Step 2: Split media in full `load()`; leave silent path on `getTask` only**

Replace the start of `async function load({ silent = false } = {})` so that:

1. When `silent === true`, keep current behavior: only `getTask`, do **not** touch `damageMedias` / `parkMedias`.
2. When `silent === false`, use:

```js
async function load({ silent = false } = {}) {
  try {
    if (silent) {
      const res = await getTask(id.value)
      order.value = res.data?.order || null
      fieldRecord.value = res.data?.fieldRecord || null
      assignedVehicle.value = res.data?.assignedVehicle || null
      pollHint.value = ''
      updateMapHint()
      await nextTick()
      if (mapInstance) {
        syncVehicleMarker({ recenter: false })
        await tryAutoCheckin()
      }
      return
    }

    const [res, mediaRes] = await Promise.all([
      getTask(id.value),
      listMedia(id.value).catch(() => ({ data: [] }))
    ])
    order.value = res.data?.order || null
    fieldRecord.value = res.data?.fieldRecord || null
    assignedVehicle.value = res.data?.assignedVehicle || null
    const all = mediaRes.data || []
    damageMedias.value = all.filter((m) => m.bizType === 'DAMAGE')
    parkMedias.value = all.filter((m) => m.bizType === 'PARK')
    pollHint.value = ''
    updateMapHint()
    await nextTick()
    await ensureMap()
    syncVehicleMarker({ recenter: true })
    await tryAutoCheckin()
  } catch (_) {
    if (silent || order.value) {
      if (silent) pollHint.value = '位置刷新失败，显示上次数据'
      return
    }
    destroyMap()
    order.value = null
    fieldRecord.value = null
    assignedVehicle.value = null
    damageMedias.value = []
    parkMedias.value = []
  }
}
```

Notes for the implementer:

- Preserve any existing map/check-in behavior already in `load` (the sketch above matches the current silent vs full split; if the file has drifted, keep map helpers identical and only add the media parallel fetch + assignment).
- `listMedia(...).catch(() => ({ data: [] }))` ensures a media-only failure still applies order/fieldRecord and clears photo sections.

- [ ] **Step 3: Add preview helper**

```js
function previewMedias(list, filePath) {
  const urls = list.map((m) => mediaUrl(m.filePath))
  uni.previewImage({ urls, current: mediaUrl(filePath) })
}
```

- [ ] **Step 4: Extend「现场摘要」template**

Replace the field-record card block with:

```vue
    <view class="card" v-if="fieldRecord">
      <view class="section-title">现场摘要</view>
      <view class="line">车牌：{{ fieldRecord.plateNo || '-' }}</view>
      <view class="line">车型：{{ fieldRecord.vehicleType || '-' }}</view>
      <view class="line">受损：{{ fieldRecord.damageDesc || '-' }}</view>
      <view class="line">停放：{{ fieldRecord.parkAddress || '-' }}</view>

      <view v-if="damageMedias.length || parkMedias.length" class="media-block">
        <view v-if="damageMedias.length" class="media-section">
          <view class="section-title media-title">受损照片</view>
          <view class="media-grid">
            <image
              v-for="m in damageMedias"
              :key="m.id"
              class="thumb"
              :src="mediaUrl(m.filePath)"
              mode="aspectFill"
              @click="previewMedias(damageMedias, m.filePath)"
            />
          </view>
        </view>
        <view v-if="parkMedias.length" class="media-section">
          <view class="section-title media-title">停放照片</view>
          <view class="media-grid">
            <image
              v-for="m in parkMedias"
              :key="m.id"
              class="thumb"
              :src="mediaUrl(m.filePath)"
              mode="aspectFill"
              @click="previewMedias(parkMedias, m.filePath)"
            />
          </view>
        </view>
      </view>
    </view>
```

- [ ] **Step 5: Add scoped styles (match scene/park)**

Append to the existing `<style scoped>` in `detail.vue`:

```css
.media-block {
  margin-top: 20rpx;
}
.media-section + .media-section {
  margin-top: 20rpx;
}
.media-title {
  margin-bottom: 16rpx;
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
```

- [ ] **Step 6: Manual H5 verification**

Prerequisites: backend running, `mobile-rescuer` `npm run dev:h5`, login as `towdriver` / `admin123`, open an ACCEPTED (or completed) task that has a `fieldRecord`.

Checklist:

1. Upload ≥1 DAMAGE photo on 现场采集, return to detail →「受损照片」only (if no PARK)
2. Upload ≥1 PARK photo on 入库登记, return to detail → both sections if both exist
3. Task with fieldRecord but no media → summary text only, no photo block
4. Tap a thumb → full-screen preview; swipe stays within that group
5. DevTools Network: while detail stays open, silent poll (~10s) shows `GET .../tasks/{id}` but **not** repeated `.../media` on each poll (media only on full `onShow` / non-silent load)

- [ ] **Step 7: Commit**

```bash
git add mobile-rescuer/src/pages/task/detail.vue
git commit -m "feat(mobile): show uploaded photos on task detail"
```

---

## Self-review (plan vs spec)

| Spec requirement | Task coverage |
|------------------|---------------|
| Read-only display under 现场摘要 | Task 1 Steps 4–5 |
| DAMAGE / PARK groups; hide empty; hide block if both empty | Task 1 Step 4 `v-if`s |
| `Promise.all` full load; silent poll no media | Task 1 Step 2 |
| `mediaUrl` + preview | Task 1 Steps 1, 3–4 |
| Media fail → `[]`, keep summary | Task 1 Step 2 `.catch` + catch wipe only on hard fail |
| No backend / no MediaGrid | File Structure |
| Acceptance / poll network check | Task 1 Step 6 |

No placeholders remaining; single-file scope matches YAGNI.
