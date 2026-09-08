# VRL Final Fix Report

**Status:** Complete  
**Commit:** `fix(mobile): start location reporter after login session`

## Findings fixed

### Important
1. **H5 reporter never starts after normal login**
   - Root cause: cold start without token → `App.onShow` → `startLocationReporter` early-returns; login `setSession` + `switchTab` does not re-fire App `onShow`.
   - Fix: call `startLocationReporter()` at end of `setSession` in `mobile-rescuer/src/stores/user.js` after token is written. Keep App onShow/onHide and logout `stopLocationReporter`.
   - `startLocationReporter` made restart-safe: no-op if already running with timer; if prior early-return left `running=false`, start works; if running without timer, ensure interval.

### Minor
2. **Silent location API toasts** — `reportLocation` passes `showError: false`; `getBoundVehicle({ showError })` optional param used as `{ showError: false }` from locationReporter tick.
3. **DispatchDetail silent poll** — `nearbyHint.value = ''` moved inside non-silent branch so silent poll does not wipe hints.
4. **init.sql** — TOW_DRIVER `role_permission` comment now mentions permission 67 / `rescuer:location`.
5. **DispatchDetail route id change** — after `loadOrder`, if still PENDING, call `loadNearby` + `initMap` + `startNearbyPoll` (status watcher may not re-fire when both orders are PENDING).

## Tests

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
& "d:\cursorworkspace\.tools\apache-maven\bin\mvn.cmd" -q "-Dtest=RescueVehicleServiceTest,RescuerMobileServiceTest" test
```

**Result:** PASS (exit 0)

Frontend: no unit suite; sanity-checked JS changes (setSession start, restart-safe reporter, showError wiring, DispatchDetail silent/hint + route-id PENDING path).

## Concerns

- Live H5 login → GPS report → dispatch nearby poll still needs device/browser geolocation + bound vehicle to confirm end-to-end.
- Circular import `user.js` ↔ `locationReporter.js` already existed via `stopLocationReporter`; `startLocationReporter` follows the same runtime-call pattern (safe after module init).
