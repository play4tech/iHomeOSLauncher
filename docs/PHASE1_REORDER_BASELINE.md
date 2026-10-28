# Phase 1: Workspace Reorder Baseline

## Status

Phase 1 instrumentation is committed. After the user authorized a follow-up fix, the without Quickstep build was rebuilt and installed. Android MCP provides device interaction and snapshots but not logcat access; device logs were retrieved separately. The seven requested reorder baseline cases remain **not run as pre-fix baselines**.

Phase 1 itself added diagnostics only. The separately authorized Phase 2 fix is recorded below.

## Instrumentation

- Branch: `codex/phase1-reorder-debug`
- Commit: `29971e299a` — `Phase 1: Add ReorderDebug instrumentation`
- Build: `.\gradlew.bat installBlissWithoutQuickstepDebug --console=plain` — `BUILD SUCCESSFUL`; installed on the connected `RMX3371 - 14` device.
- Instrumented paths: `Workspace.java`, `CellLayout.java`, `ReorderAlgorithm.java`, and `DragController.java`.
- `mDragMode` transitions are logged only in `Workspace.setDragMode()`.
- `manageFolderFeedback()`, `createUserFolderIfNecessary()`, `Folder.java`, and `FolderPagedView.java` were not modified by the Phase 1 instrumentation commit.

The instrumentation emits drag touch and visual-center coordinates, density, reorder-ring bounds, folder radius, candidate target and score data, direction vectors, page-flip thresholds, drag-mode transitions, and final drop placement/folder handling.

## Device facts captured before the Android MCP request

Earlier in this task, the connected device was observed as a Realme RMX3371 running Android 14 with a 1080x2400 display and 480 dpi. The corresponding density is 3.0 (`480 / 160`). A UI hierarchy snapshot showed the active `CellLayout` at approximately `[48,157][1032,1819]`, with four columns.

The earlier hierarchy and density observations have since been cross-checked by live `ReorderDebug` events in device logcat.

## Geometry currently derivable from source

The page-flip gutter is 24dp. At density 3.0, that is 72px. A live event at `10-20 02:44:41.988` reported page bounds `[0,1080]`, left threshold `72px`, and right threshold `1008px`.

- Left threshold: `72px`
- Right threshold: `1008px`

At `10-20 02:44:41.990`, the last-column ring for `cell=(3,1)`, `span=(1,1)`, `countX=4` had center X `908px`, radius `106px`, and outer X bounds `[802px,1014px]`.

**Measured overlap:** the last-column reorder ring intersects the right page-flip zone from `1008px` to `1014px`, a 6px horizontal overlap. The captured drag touch and visual center (`937px` and `918px`) were below the edge threshold, so the event itself reported `isAtRightEdge=false`. A live first-column ring measurement is still pending.

## Crash found in device logs

The crash buffer contains a fatal launcher exception at `10-20 02:44:46.185`:

```text
E AndroidRuntime: FATAL EXCEPTION: main
E AndroidRuntime: Process: com.ihome.launcher, PID: 25151
E AndroidRuntime: java.lang.IllegalArgumentException: Illegal Capacity: -8
E AndroidRuntime:     at java.util.ArrayList.<init>(ArrayList.java:168)
E AndroidRuntime:     at com.android.launcher3.folder.Folder.getItemsOnPage(Folder.java:1743)
E AndroidRuntime:     at com.android.launcher3.folder.FolderAnimationManager.getAnimator(FolderAnimationManager.java:242)
E AndroidRuntime:     at com.android.launcher3.folder.Folder.animateClosed(Folder.java:968)
E AndroidRuntime:     at com.android.launcher3.folder.Folder.handleClose(Folder.java:927)
E AndroidRuntime:     at foundation.e.bliss.folder.GridFolder.handleClose(GridFolder.java:267)
E AndroidRuntime:     at com.android.launcher3.folder.Folder.completeDragExit(Folder.java:1181)
E AndroidRuntime:     at com.android.launcher3.folder.Folder.onDragExit(Folder.java:1259)
E AndroidRuntime:     at com.android.launcher3.dragndrop.DragController.checkTouchMove(DragController.java:519)
```

The trace places the failure in folder drag-exit handling while the folder close animation builds its item list. The immediate exception is an attempt to construct an `ArrayList` with capacity `-8`. Source inspection traced this to the last-page item count going negative when the requested page outlived the live item range. The last logged touch before the exception was `(727,1830)` at `02:44:46.182`. A subsequent `pidof com.ihome.launcher` returned `27215`, so a launcher process had restarted after the crashed PID `25151`.

Logs were obtained with `adb logcat -b crash` and filtered `adb logcat` output for tag `ReorderDebug`; Android MCP confirmed device `693a9b29` and was used for the follow-up interaction check.

## Phase 2: Crash fix and verification

`getItemsOnPage()` previously used the pager's last page to calculate `totalItemsInFolder - itemsPerPage * page` without ensuring that the requested page still had items. During a drag, the current page can become stale as its items are removed; that calculation can become negative and was `-8` in the crash log.

The method now rejects negative pages and nonpositive page capacities, calculates the start/end range from the live icon count using `long` intermediate arithmetic, and returns an empty list when the requested page starts past the remaining icons. The `ArrayList` capacity is now `endIndex - startIndex`, which is nonnegative for every constructed list.

Verification performed:

- `.\gradlew.bat installBlissWithoutQuickstepDebug --console=plain` — `BUILD SUCCESSFUL`; installed on `RMX3371 - 14`.
- Android MCP folder drag-out check: opened the 10-item `Utilities` folder, moved to its second page (one icon), and dragged that icon to the workspace. The folder closed without a crash. The crash buffer still contained only the earlier `02:44:46.185` exception, and the launcher process remained alive.
- The test icon was returned to its folder, the temporary home-screen duplicate was removed, and the folder and calendar widget were returned to their prior workspace positions.

No new automated test was added. No Phase 2 fix commit has been created; `Folder.java` already contained unrelated user edits, which were preserved.

## Requested baseline cases

These seven pre-fix reorder baseline cases were not run. The focused post-fix folder drag-out check is documented above; it does not cover these cases.

| Case | Scenario | Result |
|---|---|---|
| 1 | Last-column reorder, `countX - 2` to `countX - 1` | Not run |
| 2 | First-column reorder, `1` to `0` | Not run |
| 3 | Mid-row reorder, `1` to `2` | Not run |
| 4 | Full-row/cross-source boundary fallback | Not run |
| 5 | Multi-span boundary reorder | Not run |
| 6 | Folder creation on boundary icon | Not run |
| 7 | Right-bezel page flip | Not run |

## Questions for the later measurement run

1. **Why do Tests 1, 2, and 5 fail to shift horizontally?** The captured `directionVector`, `reorderCandidate`, and `reorderResult` events are needed to establish whether the failure is caused by direction classification, candidate scoring, span constraints, or another path. No cause is asserted from the current evidence.
2. **Does the page-flip zone overlap the boundary reorder ring?** Yes, for the measured last column: ring `[802,1014]px` overlaps the right flip zone beginning at `1008px` by `6px`. The first-column overlap still needs a matching live ring measurement.

## Next required input

The seven requested pre-fix reorder baselines remain unfilled. Any further device run should use Android MCP for interaction; Android MCP currently does not expose logcat, so device logs must be retrieved separately.
