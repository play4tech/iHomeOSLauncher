#!/usr/bin/env python3
import os

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUTPUT_FILE_MD = os.path.join(PROJECT_ROOT, "launcher_important_logic.md")
OUTPUT_FILE_TXT = os.path.join(PROJECT_ROOT, "launcher_important_logic.txt")
PARTS_DIR = os.path.join(PROJECT_ROOT, "launcher_logic_parts")

PARTS = {
    "part1_launcher_activity": {
        "title": "Part 1: Launcher Activity, Application, State Management & Lifecycle",
        "files": [
            "src/com/android/launcher3/Launcher.java",
            "src/com/android/launcher3/LauncherApplication.java",
            "src/com/android/launcher3/LauncherAppState.java",
            "src/com/android/launcher3/statemanager/StateManager.java",
            "src/com/android/launcher3/LauncherState.java",
            "src/com/android/launcher3/states/RotationHelper.java",
            "src/com/android/launcher3/settings/SettingsActivity.java",
        ],
    },
    "part2_workspace_paging": {
        "title": "Part 2: Desktop Workspace, Paging, Insets & Touch Interaction",
        "files": [
            "src/com/android/launcher3/Workspace.java",
            "src/com/android/launcher3/WorkspaceLayoutManager.java",
            "src/com/android/launcher3/WorkspaceStateTransitionAnimation.java",
            "src/com/android/launcher3/InsettableFrameLayout.java",
            "src/com/android/launcher3/touch/WorkspaceTouchListener.java",
            "src/com/android/launcher3/touch/ItemClickHandler.java",
            "src/com/android/launcher3/touch/ItemLongClickListener.java",
            "bliss/src/foundation/e/bliss/multimode/MultiModeController.kt",
        ],
    },
    "part3_grid_drag_drop": {
        "title": "Part 3: Grid Layout, Reorder Algorithm & Drag-and-Drop Engine",
        "files": [
            "src/com/android/launcher3/CellLayout.java",
            "src/com/android/launcher3/ShortcutAndWidgetContainer.java",
            "src/com/android/launcher3/BubbleTextView.java",
            "src/com/android/launcher3/celllayout/ReorderAlgorithm.java",
            "src/com/android/launcher3/celllayout/ItemConfiguration.kt",
            "src/com/android/launcher3/dragndrop/DragController.java",
            "src/com/android/launcher3/dragndrop/DragLayer.java",
            "src/com/android/launcher3/dragndrop/DragView.java",
            "src/com/android/launcher3/dragndrop/DragOptions.java",
            "src/com/android/launcher3/dragndrop/DragDriver.java",
            "src/com/android/launcher3/DropTarget.java",
        ],
    },
    "part4_folders_dock_wobble": {
        "title": "Part 4: Folders, Dock (Hotseat), Page Indicators & Wobble Edit Mode",
        "files": [
            "src/com/android/launcher3/folder/Folder.java",
            "src/com/android/launcher3/folder/FolderAnimationManager.java",
            "bliss/src/foundation/e/bliss/folder/GridFolder.java",
            "src/com/android/launcher3/folder/FolderIcon.java",
            "src/com/android/launcher3/folder/FolderPagedView.java",
            "src/com/android/launcher3/folder/LauncherDelegate.java",
            "src/com/android/launcher3/Hotseat.java",
            "src/com/android/launcher3/pageindicators/PageIndicatorDots.java",
            "src/com/android/launcher3/pageindicators/WorkspacePageIndicator.java",
            "bliss/src/foundation/e/bliss/wobble/JiggleTopBarView.kt",
            "bliss/src/foundation/e/bliss/wobble/JiggleEditMenuPopup.kt",
            "src/com/android/launcher3/util/RemovedAppStore.java",
        ],
    },
    "part5_app_library_search": {
        "title": "Part 5: App Library, Spotlight Search, Categories & Indexing",
        "files": [
            "src/com/android/launcher3/applibrary/model/CategoryProvider.kt",
            "src/com/android/launcher3/applibrary/model/AppLibraryModel.kt",
            "src/com/android/launcher3/applibrary/model/AppLibraryCategory.kt",
            "src/com/android/launcher3/applibrary/model/SuggestionsProvider.kt",
            "src/com/android/launcher3/applibrary/view/SpotlightSearchOverlay.java",
            "src/com/android/launcher3/applibrary/view/AppLibraryDashboardView.java",
            "src/com/android/launcher3/applibrary/view/AppLibraryPage.java",
            "src/com/android/launcher3/applibrary/view/AppLibraryBodyContainer.java",
            "src/com/android/launcher3/applibrary/view/CategoryCardView.java",
            "src/com/android/launcher3/applibrary/view/MiniClusterView.java",
            "src/com/android/launcher3/applibrary/view/AppLibraryCategoryExpandedView.java",
            "src/com/android/launcher3/applibrary/view/AppLibraryIndexScrollerView.java",
            "src/com/android/launcher3/applibrary/view/ChangeCategoryPickerView.kt",
            "src/com/android/launcher3/applibrary/view/AppLibraryFolderTransition.kt",
            "src/com/android/launcher3/applibrary/view/ExpandedCategoryAdapter.java",
            "src/com/android/launcher3/applibrary/view/AppLibrarySearchAdapter.java",
        ],
    },
    "part6_popups_models_widgets": {
        "title": "Part 6: Context Popups, Data Models, DB Loader, Widgets & Onboarding",
        "files": [
            "src/com/android/launcher3/popup/PopupContainerWithArrow.java",
            "src/com/android/launcher3/popup/ArrowPopup.java",
            "src/com/android/launcher3/popup/LauncherPopupLiveUpdateHandler.java",
            "src/com/android/launcher3/popup/SystemShortcut.java",
            "src/com/android/launcher3/shortcuts/DeepShortcutView.java",
            "src/com/android/launcher3/LauncherModel.java",
            "src/com/android/launcher3/model/ModelWriter.java",
            "src/com/android/launcher3/model/BgDataModel.java",
            "src/com/android/launcher3/model/LoaderTask.java",
            "src/com/android/launcher3/model/data/ItemInfo.java",
            "src/com/android/launcher3/model/data/FolderInfo.java",
            "src/com/android/launcher3/model/data/WorkspaceItemInfo.java",
            "bliss/src/foundation/e/bliss/widgets/WidgetContainer.kt",
            "bliss/src/foundation/e/bliss/widgets/RoundedWidgetView.java",
            "bliss/src/foundation/e/bliss/widgets/WidgetsDbHelper.kt",
            "bliss/src/foundation/e/bliss/widgets/DefaultWidgets.kt",
            "bliss/src/foundation/e/bliss/preferences/BlissPrefs.kt",
            "bliss/src/foundation/e/blisslauncher/features/onboarding/DefaultHomeLayoutResolver.kt",
        ],
    },
    "part7_android_manifest": {
        "title": "Part 7: Android Manifests & Permissions (Common, Bliss, Quickstep Variants)",
        "files": [
            "AndroidManifest-common.xml",
            "AndroidManifest.xml",
            "bliss/AndroidManifest.xml",
            "bliss/AndroidManifest-withoutQuickstep.xml",
            "quickstep/AndroidManifest.xml",
            "quickstep/AndroidManifest-launcher.xml",
        ],
    },
}

ALL_FILES = []
for part in PARTS.values():
    ALL_FILES.extend(part["files"])


def write_bundle(file_path, title, file_list, description=""):
    os.makedirs(os.path.dirname(file_path), exist_ok=True)
    with open(file_path, "w", encoding="utf-8") as out:
        out.write(f"# {title}\n\n")
        if description:
            out.write(f"{description}\n\n")
        out.write("## Table of Contents\n\n")
        for rel_path in file_list:
            full_path = os.path.join(PROJECT_ROOT, rel_path)
            if os.path.exists(full_path):
                anchor = rel_path.replace("/", "").replace(".", "").replace("-", "")
                out.write(f"- [{rel_path}](#{anchor})\n")
        out.write("\n---\n\n")

        for rel_path in file_list:
            full_path = os.path.join(PROJECT_ROOT, rel_path)
            if not os.path.exists(full_path):
                print(f"Skipping {rel_path} (not found)")
                continue
            if rel_path.endswith(".kt"):
                ext = "kotlin"
            elif rel_path.endswith(".xml"):
                ext = "xml"
            else:
                ext = "java"
            with open(full_path, "r", encoding="utf-8") as in_f:
                content = in_f.read()

            out.write(f"# {rel_path}\n\n")
            out.write(f"**Absolute Path**: `{full_path}`  \n")
            out.write(f"**Lines**: {content.count(chr(10)) + 1} | **Bytes**: {len(content.encode('utf-8'))}\n\n")
            out.write(f"```{ext}\n")
            out.write(content)
            if not content.endswith("\n"):
                out.write("\n")
            out.write("```\n\n")
            out.write("---\n\n")


def main():
    print(f"Generating consolidated and modular files from project root: {PROJECT_ROOT}")

    # 1. Generate consolidated master files (MD and TXT)
    write_bundle(
        OUTPUT_FILE_MD,
        "iHome / Bliss Launcher - Consolidated Core Logic for Gemini Analysis",
        ALL_FILES,
        f"This master document aggregates all {len(ALL_FILES)} core architectural and functional code logic files and manifests."
    )
    with open(OUTPUT_FILE_MD, "r", encoding="utf-8") as f_in, open(OUTPUT_FILE_TXT, "w", encoding="utf-8") as f_out:
        f_out.write(f_in.read())

    master_size = os.path.getsize(OUTPUT_FILE_MD)
    print(f"Master file updated: {OUTPUT_FILE_MD} & .txt ({len(ALL_FILES)} files, {master_size} bytes)")

    # 2. Generate modular parts (<350KB each) for Gemini upload limit compliance
    os.makedirs(PARTS_DIR, exist_ok=True)
    for part_id, part_info in PARTS.items():
        part_md = os.path.join(PARTS_DIR, f"{part_id}.md")
        part_txt = os.path.join(PARTS_DIR, f"{part_id}.txt")
        write_bundle(
            part_md,
            part_info["title"],
            part_info["files"],
            f"Modular code logic bundle designed to stay within Gemini attachment size limits ({len(part_info['files'])} files)."
        )
        with open(part_md, "r", encoding="utf-8") as f_in, open(part_txt, "w", encoding="utf-8") as f_out:
            f_out.write(f_in.read())

        size = os.path.getsize(part_txt)
        lines = sum(1 for _ in open(part_txt, "r", encoding="utf-8"))
        print(f"  -> Generated {part_id}.txt/.md: {len(part_info['files'])} files, {lines} lines, {size / 1024:.1f} KB")

    # 3. Also generate android_manifest.md and android_manifest.txt alias in launcher_logic_parts
    manifest_md = os.path.join(PARTS_DIR, "android_manifest.md")
    manifest_txt = os.path.join(PARTS_DIR, "android_manifest.txt")
    part7_md = os.path.join(PARTS_DIR, "part7_android_manifest.md")
    if os.path.exists(part7_md):
        with open(part7_md, "r", encoding="utf-8") as f_in:
            m_content = f_in.read()
        with open(manifest_md, "w", encoding="utf-8") as f_out:
            f_out.write(m_content)
        with open(manifest_txt, "w", encoding="utf-8") as f_out:
            f_out.write(m_content)
        print(f"  -> Generated android_manifest.txt/.md alias")


if __name__ == "__main__":
    main()
