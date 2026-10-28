# Workspace Boundary Reorder Verification

Date: 2026-10-20

Branch: `codex/phase1-reorder-debug`

Commits: Phase 1 instrumentation `29971e299a`; Phase 2 fix `26e3fa6f86`; Phase 4 cleanup `3ce3c96778`.

## Summary

The earlier seven-case regression suite passed three times per case on the connected Realme device, including folder creation. The user correctly pointed out that its boundary endpoints were selected from the measured code hit zones, so those passes did not establish that an ordinary hand drag would work. The follow-up below widens the edge-icon reorder target and tests near-bezel drags with path wobble. The final device state is Play Store `(2,0)`, My Files `(3,0)` on page 0.

Phase 4 removed the temporary `ReorderDebug` instrumentation. The without-Quickstep debug build installed successfully afterward. The latest crash-buffer read was empty.

## Implementation

- `ReorderAlgorithm` now checks for a free, contiguous slot in the same row when a displaced item is pushed off a boundary. It keeps the existing cross-row fallback when that row has no fitting slot. Multi-span items are checked using their full span.
- Folderable 1×1 icons in the first or last column get up to 12dp of additional reorder radius. The extension is limited by the measured layout edge, leaving an 8dp minimum inset. The folder creation radius still uses the original reorder radius, so direct center drops keep the same folder target.
- The Workspace page-flip threshold now leaves a 24dp clearance beyond boundary reorder rings when the screen width permits it, with an 8dp minimum bezel strip. This gives the reorder preview room before the edge page-flip zone.
- Phase 4 removed the temporary diagnostic logs and helper code. No `ReorderDebug` references remain under `src/`.

The protected code was verified unchanged against the branch base (`3316fa594210eaf42efe42db0c86978dbd3d71db`): `manageFolderFeedback()` (55 lines) and `createUserFolderIfNecessary()` (66 lines) are text-identical. `Folder.java` and `FolderPagedView.java` have no committed changes in this branch. Existing local modifications to those files were left untouched and unstaged.

## Device and Geometry

- Device: Realme RMX3371, Android 14
- `adb shell wm size`: `Physical size: 1080x2400`
- `adb shell wm density`: `Physical density: 480` (3.0 px/dp)
- Workspace grid: 4 columns

Representative baseline geometry logs:

```text
ReorderDebug: reorderRing boundary=first cell=(0,0) span=(1,1) countX=4 density=3.0 centerLocalPx=(170,154) centerRawScreenPx=170.0 radiusPx=106.0 outerRawScreenX=[64.0,276.0]
ReorderDebug: reorderRing boundary=last cell=(3,0) span=(1,1) countX=4 density=3.0 centerLocalPx=(908,154) centerRawScreenPx=908.0 radiusPx=106.0 outerRawScreenX=[802.0,1014.0]
ReorderDebug: pageFlipGeometry page=0 density=3.0 edgeGutterPx=72.0 minimumBezelPx=24.0 leftReorderRingOuterX=-14.232956 rightReorderRingOuterX=1014.0 leftThresholdRawScreenPx=24.0 rightThresholdRawScreenPx=1015.0 pageBoundsRawScreenPx=[0,1080] touchRawScreenPx=(808,295) centerRawScreenPx=(805.0,260.0) relTouchX=808.0 relCenterX=805.0 leftEdgeThreshold=24.0 rightEdgeThreshold=1015.0 isAtLeftEdge=false isAtRightEdge=false
ReorderDebug: pageFlipGeometry page=1 density=3.0 edgeGutterPx=72.0 minimumBezelPx=24.0 leftReorderRingOuterX=-14.232956 rightReorderRingOuterX=1015.0 leftThresholdRawScreenPx=24.0 rightThresholdRawScreenPx=1016.0 pageBoundsRawScreenPx=[0,1080] touchRawScreenPx=(1406,295) centerRawScreenPx=(1403.0,260.0) relTouchX=278.0 relCenterX=275.0 leftEdgeThreshold=24.0 rightEdgeThreshold=1016.0 isAtLeftEdge=false isAtRightEdge=false
```

For the first-column app icon, the ring spans x=64–276px and the left page-flip zone starts at x=24px, so those do not overlap. The last-column app ring reaches x=1014px and right flipping starts at x=1015px, leaving a 1px gap; page 1 measured 1015px and 1016px respectively. There is one conditional edge case: the page-flip log reports a left boundary envelope at x=-14.23px on page 0, where the first-column item is the multi-span Clock widget. Because the code retains a minimum 8dp bezel strip, the left flip threshold remains x=24px, which intersects that edge-reaching envelope. Thus overlap is absent for the tested 1×1 app rings, but possible when a boundary item's computed ring reaches past the physical edge.

The post-update geometry for a 1×1 boundary icon on this 1080px, 480dpi display is a 142px radius (106px base plus 12dp/36px), limited by the 8dp layout-edge inset. Using the logged cell centers x=170 and x=908, the expanded rings end at x=28 on the left and x=1050 on the right. The current page-flip thresholds resolve to x=24 and x=1056, leaving 4px and 6px of separation. These post-update values are calculated from the runtime layout and density rules; Phase 4 intentionally removed the diagnostic tag, so no post-update `ReorderDebug` line was emitted.

## Seven-Case UI Test Results

The test fixture was restored before each repeat. All cases completed 3/3 times.

| Test | Result | Verified final state |
|---|---|---|
| T1 Last-column reorder | PASS 3/3 | Play Store `(3,0)`, My Files `(2,0)`, screen 0 / page 0. |
| T2 First-column reorder | PASS 3/3 | Indus `(0,0)`, Claude `(1,0)`, screen 1 / page 1. |
| T3 Mid-row reorder | PASS 3/3 | Indus `(2,0)`, Keep notes `(1,0)`, screen 1 / page 1. |
| T4 Full-row cross-source fallback | PASS 3/3 | Calculator `(3,0)`; ChatGPT displaced to `(3,1)` by the existing cross-row fallback; no folder was created. |
| T5 Multi-span boundary reorder | PASS 3/3 | Brave `(1,3)`; 2×2 Calendar widget moved horizontally from `(0,3)` to `(2,3)` on page 1. |
| T6 Folder creation on boundary icon | PASS 3/3 | Folder created at `(3,0)` with exactly `Photos` and `My Files`. |
| T7 Edge-bezel page flip | PASS 3/3 | One right-neighbor page selection; Photos dropped at `(3,5)` on page 1. |

Representative `adb logcat -s ReorderDebug` lines:

```text
ReorderDebug: boundaryHorizontalPreference itemCell=(3,0) span=(1,1) directionBefore=(1,0) sameRowSlotX=2 dropRegion=Rect(3, 0 - 4, 1)
ReorderDebug: boundaryHorizontalPreference applied directionAfter=(-1,0) itemCellAfter=(2,0) remainingIntersecting=0
ReorderDebug: boundaryHorizontalPreference itemCell=(0,0) span=(1,1) directionBefore=(-1,0) sameRowSlotX=1 dropRegion=Rect(0, 0 - 1, 1)
ReorderDebug: boundaryHorizontalPreference applied directionAfter=(1,0) itemCellAfter=(1,0) remainingIntersecting=0
ReorderDebug: boundaryHorizontalPreference itemCell=(0,3) span=(2,2) directionBefore=(-1,1) sameRowSlotX=2 dropRegion=Rect(1, 3 - 2, 4)
ReorderDebug: boundaryHorizontalPreference applied directionAfter=(1,0) itemCellAfter=(2,3) remainingIntersecting=0
ReorderDebug: dropFinal folderPath=handled cell=(3,0) screenId=0 pageIndex=0
ReorderDebug: neighborPageSelected page=1 side=right currentPage=0 nextPage=0
```

## Earlier Calibrated Boundary Probes

These checks are retained as history, but they should not be treated as evidence of human tolerance: the successful endpoint was placed inside the measured reorder ring, and the successful right-edge wobble stayed within a few pixels of its measured boundary. The user’s criticism of that test design is valid. The near-bezel, irregular-path checks below replace them as the usability evidence.

## Direct Same-Page Boundary Reorders

After the cleanup build was installed, the device was reset to the home screen with Play Store at `(2,0)` and My Files at `(3,0)`. The existing gesture helper performed a human-style Play Store drag: 800ms long-press, a small initial move, eight interpolated move steps, then a 900ms dwell at `(808,295)` before release. My Files shifted into `(2,0)` and Play Store moved to `(3,0)`, with no folder or page change.

After the user clarified the desired direction, Android MCP dragged My Files from its second-last cell `(2,0)` at `(663,295)` to the outer ring of Play Store in the last cell `(3,0)`, ending at `(808,295)`. The snapshot showed Play Store shifted to `(2,0)` and My Files moved to `(3,0)`. There was no folder and the device remained on the same page.

The same human-style drag was then run in the second row: Gmail from `(2,1)` at `(663,572)` to the outer ring of Maps in `(3,1)`, ending at `(808,572)`. Android MCP confirmed Maps at `(2,1)` and Gmail at `(3,1)`, with the same page visible and no folder created.

The earlier center-drop folder used to demonstrate folder creation was removed before these reorder runs. The temporary diagnostics had already been removed, so there was no `ReorderDebug` drop log for these last gestures; Android MCP snapshots confirmed the final positions.

## Held Preview and Shaky-Drag Follow-up

These additional checks used the without-Quickstep build on the same RMX3371. Android MCP snapshots were captured while the drag finger was still held, before release.

- **Right boundary, direct path:** Started Play Store at `(663,295)` and moved to `(1012,295)`, aligned with My Files in `(3,0)`. My Files visibly shifted into `(2,0)` while Play Store remained attached to the drag. Releasing left Play Store `(3,0)` and My Files `(2,0)` on page 0, without creating a folder.
- **Right boundary, shaky path:** Repeated the move with small path and hover jitter: X ranged from `1009` to `1014px`, Y from `292` to `298px`; the final release point was `(1012,295)`. The held snapshot showed My Files shifted aside. Final cells were Play Store `(3,0)` and My Files `(2,0)`, with no folder or page change.
- **Left boundary:** On page 1, dragged Indus from `(417,295)` to `(68,295)` past first-column Claude. Claude shifted to `(1,0)` while Indus was held; after release Indus was `(0,0)` and Claude `(1,0)`. The page stayed selected and no folder was created.
- **Boundary sensitivity probe:** An inward endpoint at `(1000,295)` remained in the folder-feedback area and created an `Untitled` folder containing Play Store and My Files. That folder was removed by restoring the saved test fixture. A wider exploratory edge excursion crossed the page-flip threshold; it was canceled and the fixture restored. The successful shaky path stayed within `x=1009..1014`, just inside the measured right flip threshold at `x=1015`.

Those earlier jitter measurements were too narrow to support a real-world usability claim. The wider near-bezel runs below provide the relevant follow-up.

## Near-Bezel Human-Path Follow-up

These runs used UI element bounds and the device’s physical screen dimensions to start the gesture, then ended at 96% of screen width/4% of screen width rather than at a measured reorder-ring coordinate. The path used irregular intermediate moves and a continuing wobble while held. Android MCP snapshots were captured before release to verify the preview animation. A second right-edge stress path ended at 97% of screen width with ±8px jitter.

| Scenario | Held-preview evidence | Final result |
|---|---|---|
| Right boundary, row 0: My Files → Play Store | Drag ended at `(1037,298)` on a 1080px display with small path/hold jitter. While held, Play Store was visibly in the second-to-last cell and My Files remained attached to the drag near the edge. `ReorderAlarmListener.onAlarm targetCell=(3,0)` fired. | My Files `(3,0)`, Play Store `(2,0)`, page 0; no folder. |
| Right boundary, near-edge stress in reverse: Play Store → My Files | Endpoint `(1048,296)` with ±8px path/hover jitter. MCP showed My Files shift aside before release; no page snap or folder occurred. | Play Store `(3,0)`, My Files `(2,0)`, page 0. The saved final layout was restored after this reverse-direction stress run. |
| Right boundary, row 1: Gmail → Maps | Drag ended at `(1037,567)` with a different irregular path. MCP showed Maps move aside while Gmail was still held. `ReorderAlarmListener.onAlarm targetCell=(3,1)` fired. | Gmail `(3,1)`, Maps `(2,1)`, page 0; no folder. |
| Left boundary, page 1: Indus → Claude | Drag ended at `(43,298)` with path wobble while page 1 was selected. MCP showed Claude shift into column 1 before release. `ReorderAlarmListener.onAlarm targetCell=(0,0)` fired. | Indus `(0,0)`, Claude `(1,0)`, page 1; no page change or folder. |
| Folder regression on right boundary: Keep notes → ChatGPT | Dragged to the target center and held. After release MCP reported `Folder: Untitled, 2 items` at `(3,0)`. | Folder creation still works. The pre-test database snapshot was restored afterward; no test folder remains in the final home state. |
| Right page flip | A jittered drag ended at x=1077, held at the physical bezel for about 900ms, then moved inward and released. | `PagedView.snapToPage whichPage=1 current=0` appeared once; page 1 was visible after release and Photos landed at `(3,5)`. |

Representative post-cleanup log lines (the temporary `ReorderDebug` tag had been removed):

```text
DragTrace: ReorderAlarmListener.onAlarm targetCell=(3,0)
DragTrace: Workspace.onDrop item=My Files source=Workspace
DropDebug: performReorder returned: 3, 0
IconDragLogger: DROP SUCCESS -> Moved icon 'My Files' to Cell(3, 0)
DragTrace: Workspace.onDrop item=Play Store source=Workspace
IconDragLogger: DROP SUCCESS -> Moved icon 'Play Store' to Cell(3, 0)
DragTrace: ReorderAlarmListener.onAlarm targetCell=(3,1)
IconDragLogger: DROP SUCCESS -> Moved icon 'Gmail' to Cell(3, 1)
DragTrace: ReorderAlarmListener.onAlarm targetCell=(0,0)
IconDragLogger: DROP SUCCESS -> Moved icon 'Indus' to Cell(0, 0)
DragTrace: Workspace.onDrop item=Keep notes source=Workspace
DragTrace: PagedView.snapToPage whichPage=1 current=0
DragTrace: Workspace.onDrop performReorder returned targetCell=(3,5)
```

A separate 12-second bezel hold advanced two pages. The 900ms run above is the result for the requested single edge dwell; continued holding allows the existing paging behavior to repeat.

## Build and Compatibility Scope

`./gradlew installBlissWithoutQuickstepDebug --console=plain` completed successfully and installed on `RMX3371 - 14`. Only existing Gradle, manifest/resource, and deprecation warnings were reported. `adb logcat -d -b crash` returned no crash entries after the final UI operation.

UI behavior was exercised on one Realme RMX3371 device. The code derives thresholds from runtime density and layout geometry, but this run does not certify Samsung, Xiaomi, Pixel, or other OEM builds. Those require device-specific validation. The final crash-buffer check was empty, and the launcher database was restored to the post-reorder state after the folder and page-flip fixtures were used.
