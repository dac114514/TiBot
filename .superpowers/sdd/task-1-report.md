# Task 1 Report: RootfsDownloadManager -- speedTest + fixed download

**Status:** DONE
**Date:** 2026-06-19
**Commit:** `d04e2ab` on branch `main`

## 1. What Was Implemented

### 1.1 MirrorSource.kt -- Added SpeedResult data class

**File:** `app/src/main/java/com/faster/tibot/data/rootfs/MirrorSource.kt`

- Added `SpeedResult` data class with `mirrorId: String`, `latencyMs: Long`, and optional `error: String?` fields (lines 9-13).

### 1.2 RootfsDownloadManager.kt -- Added speedTest()

**File:** `app/src/main/java/com/faster/tibot/data/rootfs/RootfsDownloadManager.kt`

- Added imports for `kotlinx.coroutines.async` and `kotlinx.coroutines.coroutineScope` (lines 10-11).
- Added `speedTest()` method (lines 44-65) that concurrently measures mirror latency:
  - Uses `coroutineScope` + `async` to fire concurrent HEAD requests to all mirrors
  - Each request has 5s connect and read timeouts
  - Returns `List<SpeedResult>` with measured RTT or error info

### 1.3 RootfsDownloadManager.kt -- Fixed download() with stall-based timeout

- Replaced `DOWNLOAD_TIMEOUT_MS = 30_000L` with two constants:
  - `STALL_TIMEOUT_MS = 30_000L` -- triggers only when 30s pass with zero bytes received
  - `HARD_TIMEOUT_MS = 600_000L` -- 10-minute absolute hard cap
- Changed `setDestinationUri(Uri.fromFile(destFile))` to `setDestinationInExternalFilesDir(context, null, "rootfs.tar.gz")` to fix Android 10+ `Uri.fromFile` issue
- Rewrote stall detection:
  - Replaced poll-count-based `stallCount` variable with timestamp-based `lastProgressTime`
  - Resets `lastProgressTime` when `bytesDelta > 0`
  - Triggers stall error when `now - lastProgressTime > STALL_TIMEOUT_MS`
  - Works correctly from t=0 (previously required `downloaded > 0` to activate)
- Updated timeout error messages to reference `HARD_TIMEOUT_MS`
- All STATUS_SUCCESSFUL, STATUS_FAILED, STATUS_RUNNING/PENDING handlers preserved as-is

## 2. Self-Review Findings

- `destFile` parameter retained in `download()` signature to avoid breaking callers (`WizardViewModel.kt`). It is still used for `destFile.delete()` in the timeout handler.
- Stall detection now correctly detects stalls even at 0 bytes received (e.g., connection accepted but never sends data).
- `setDestinationInExternalFilesDir` returns a `DownloadManager.Request`, so chaining `.setTitle(...)` etc. works correctly.
- Imports verified: `async`, `coroutineScope`, `withTimeoutOrNull` all present.

## 3. Concerns

- `destFile` parameter is partially vestigial -- kept only for compatibility with `WizardViewModel.kt`. Could be removed when the ViewModel is refactored in subsequent tasks.
- `destFile.delete()` in the timeout handler still references the now-unused-for-destination `destFile` parameter. In practice, `setDestinationInExternalFilesDir` places the file in a system-managed location, so manual `delete()` may not work as expected.

## 4. Commits Made

| Commit | Branch | Message |
|--------|--------|---------|
| `d04e2ab` | `main` | `feat: add speedTest and stall-based download timeout to RootfsDownloadManager` |

Files changed:
- `app/src/main/java/com/faster/tibot/data/rootfs/MirrorSource.kt` (+6, -0)
- `app/src/main/java/com/faster/tibot/data/rootfs/RootfsDownloadManager.kt` (+45, -14)

## 5. Fix Report -- Hard Timeout Cleanup Path (2026-06-19)

**Commit:** `2d65ace` on branch `main`

**Issue:** In `RootfsDownloadManager.kt`, the hard timeout handler called `destFile.delete()` when `success == null`. However, the actual download uses `setDestinationInExternalFilesDir(context, null, "rootfs.tar.gz")`, so `destFile` points to the wrong path. On a 10-minute hard timeout, an orphaned `rootfs.tar.gz` is left in the external files directory.

**Fix:** Replaced `destFile.delete()` (line 210) with:
```kotlin
val actualFile = File(context.getExternalFilesDir(null), "rootfs.tar.gz")
actualFile.delete()
```

The `java.io.File` import was already present (line 15), so no additional import was needed.

**File changed:** `app/src/main/java/com/faster/tibot/data/rootfs/RootfsDownloadManager.kt` (+2, -1)
