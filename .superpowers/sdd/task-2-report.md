# Task 2 Report: WizardViewModel — Phase state machine + flow rewrite

## Status: COMPLETE

## Summary
Rewrote `WizardViewModel.kt` with the new Phase-based state machine, speed test flow, download without auto-failover, extract-and-deploy pipeline, and launch gateway.

## Changes Made

**File:** `app/src/main/java/com/faster/tibot/ui/wizard/WizardViewModel.kt`

### Added
- `Phase` enum: IDLE, SPEED_TEST, READY, DOWNLOADING, EXTRACTING, DEPLOYING, DONE, ERROR
- `LogLevel` enum: INFO, SUCCESS, ERROR, PROGRESS
- `LogLine` data class
- `startSpeedTest()` -- auto-called when advancing to step 3, picks fastest mirror
- `extractAndDeploy()` -- extracts tar, then calls verifyRootfs()
- `verifyRootfs()` -- checks bin/sh exists and is executable in extracted rootfs
- `onLaunchGateway()` -- marks configured and starts foreground service

### Modified
- `WizardState` -- replaced old fields (downloadProgress, triedMirrorIds, deployProgress) with new phase-based fields (phase, phaseSubtitle, progressPercent, downloadedBytes, totalBytes, speedBytesPerSec, logs, error)
- `WizardState.selectedMirrorId` -- default changed from "github" to "ustc"
- `WizardState.currentStep` -- comment removed, no hardcoded step-to-phase mapping
- `nextStep()` -- step==2 now saves token AND calls startSpeedTest() after setting step=3
- `startDownload()` -- no auto-failover (no recursive mirror switching), user manually retries
- `rootfsFile` -- changed from `File(app.filesDir, ...)` to `File(app.getExternalFilesDir(null), ...)` (matching Task 1)

### Removed
- `ProotManager` import and `prootManager` field (no longer used in this ViewModel)
- Old `startDownload()` with auto-failover logic
- `verifyAndExtract()` method
- `deployProgress()` method
- Old state fields: `downloadProgress`, `triedMirrorIds`, `deployProgress`

### Kept (not removed)
- `DeployStep` data class (used by WizardScreen.kt, will be removed in Task 4)
- `DeployStatus` enum (used by WizardScreen.kt, will be removed in Task 4)
- `DownloadProgress` import (used in download/extract flows)
- `DownloadState` import (used in download/extract flows)

## Verification Checklist
- [x] Phase/LogLevel/LogLine declared before WizardState (compilation requirement)
- [x] DeployStep/DeployStatus still present (lines 42-43)
- [x] rootfsFile uses `getExternalFilesDir(null)` matching Task 1
- [x] No auto-failover logic in startDownload()
- [x] No references to triedMirrorIds, verifyAndExtract(), deployProgress()
- [x] DownloadProgress, DownloadState imports still present
- [x] ProotManager import removed
- [x] Speed test auto-runs when entering download step
- [x] Launch gateway correctly starts TiBotForegroundService

## Commits
```
6f97b1e feat: add Phase state machine, speed test flow, launch gateway to WizardViewModel
18c3095 fix: check setExecutable return value in verifyRootfs
```

## Post-Review Fix (2026-06-19)

**Issue:** In `verifyRootfs()`, the `else` branch called `sh.setExecutable(true)` but ignored its Boolean return value, then checked `sh.exists()` — which was guaranteed to be `true` since the outer `if` condition `sh.exists() && sh.canExecute()` had already confirmed the file exists. This made the error branch effectively dead code.

**Fix:** Merged `setExecutable` + `exists` check into `else if (sh.setExecutable(true))`, so the error branch is reached when the permission change fails.

**File:** `app/src/main/java/com/faster/tibot/ui/wizard/WizardViewModel.kt` (line 237)

**Commit:** `18c3095`

## Concerns
- Phase must be declared before WizardState (since WizardState has `val phase: Phase`), which means Phase/LogLevel/LogLine appear before the "existing" DeployStep/DeployStatus declarations rather than after them as the brief states. This is a necessary ordering change for compilation.
