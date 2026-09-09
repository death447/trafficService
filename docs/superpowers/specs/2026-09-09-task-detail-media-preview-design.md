# Task Detail Media Preview (Mobile Rescuer)

Date: 2026-09-09  
Scope: `mobile-rescuer` task detail page only  
Status: approved for planning

## Goal

On the rescuer mobile **task detail** page, show photos already uploaded via 现场采集 / 入库登记 so the driver can review them without opening those pages again. Detail remains read-only for media (no upload from detail).

## Non-goals

- Uploading or deleting photos from task detail
- Backend / DTO changes (`RescuerTaskDetail` stays as-is)
- Extracting a shared MediaGrid component
- Showing photos when there is no `fieldRecord` card
- Changing map, check-in, or task action flows

## UX

- Keep the existing「现场摘要」card (`v-if="fieldRecord"`).
- Below the text lines (车牌 / 车型 / 受损 / 停放):
  - If any `DAMAGE` media exists: section title「受损照片」+ thumbnail grid
  - If any `PARK` media exists: section title「停放照片」+ thumbnail grid
  - If both groups are empty: omit the entire photo block (text summary still shown)
- Thumbnail style aligned with `scene.vue` / `park.vue` (~200rpx, `aspectFill`, rounded)
- Tap opens `uni.previewImage` with that group's URLs; `current` is the tapped image
- No「暂无照片」placeholders on detail (empty groups are hidden)

## Data flow

1. Full (non-silent) `load()`:
   - `Promise.all([getTask(id), listMedia(id)])`
   - Map order / fieldRecord / assignedVehicle as today
   - Split media into `damageMedias` and `parkMedias` by `bizType`
2. Silent poll `load({ silent: true })` (location / auto check-in):
   - Continues to call **only** `getTask`
   - Does **not** re-fetch media
3. Display URLs via existing `mediaUrl(filePath)` from `utils/request.js`
4. Media fetch failure on full load:
   - Do not clear order / fieldRecord
   - Set both media lists to `[]` (or keep last successful lists if preferred during implementation; default: `[]` on failure so UI does not show stale URLs after auth/path errors)

APIs already available:

- `GET /api/mobile/rescuer/tasks/{id}/media` → `listMedia(id)`
- Static files via `mediaUrl` → `{origin}/uploads/{filePath}`

## Implementation boundary

| Change | Path |
|--------|------|
| UI + load + preview | `mobile-rescuer/src/pages/task/detail.vue` |
| Reuse | `listMedia` in `api/rescuer.js`, `mediaUrl` in `utils/request.js` |

No new API modules, no backend commits for this feature.

## Acceptance

1. With DAMAGE photos only:「受损照片」visible; no park section
2. With PARK photos only:「停放照片」visible; no damage section
3. With both: both sections under summary text
4. With neither: summary text only, no photo block
5. Tap thumbnail → full-screen preview for that group
6. After uploading on scene/park and returning to detail (`onShow` full load), new photos appear
7. Location poll does not spam `listMedia`

## Testing notes

- Manual H5: seed or upload DAMAGE/PARK on a task, open detail, verify sections and preview
- Confirm silent poll path still updates vehicle marker without media requests (network tab)
