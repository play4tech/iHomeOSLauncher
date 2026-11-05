const fs = require('fs');
const path = require('path');

const PROJECT_ROOT = path.resolve(__dirname, '..');
const OUTPUT_FILE_MD = path.join(PROJECT_ROOT, 'launcher_important_logic.md');
const OUTPUT_FILE_TXT = path.join(PROJECT_ROOT, 'launcher_important_logic.txt');
const PARTS_DIR = path.join(PROJECT_ROOT, 'launcher_logic_parts');

const PARTS = {
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
            "src/com/android/launcher3/settings/AppLibraryOnlyActivity.kt",
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
};

const ALL_FILES = [];
for (const part of Object.values(PARTS)) {
    ALL_FILES.push(...part.files);
}

function writeBundle(filePath, title, fileList, description = '') {
    fs.mkdirSync(path.dirname(filePath), { recursive: true });
    let out = `# ${title}\n\n`;
    if (description) {
        out += `${description}\n\n`;
    }
    out += `## Table of Contents\n\n`;
    for (const relPath of fileList) {
        const fullPath = path.join(PROJECT_ROOT, relPath);
        if (fs.existsSync(fullPath)) {
            const anchor = relPath.replace(/\//g, '').replace(/\./g, '').replace(/-/g, '');
            out += `- [${relPath}](#${anchor})\n`;
        }
    }
    out += `\n---\n\n`;

    for (const relPath of fileList) {
        const fullPath = path.join(PROJECT_ROOT, relPath);
        if (!fs.existsSync(fullPath)) {
            console.log(`Skipping ${relPath} (not found)`);
            continue;
        }
        let ext = 'java';
        if (relPath.endsWith('.kt')) ext = 'kotlin';
        else if (relPath.endsWith('.xml')) ext = 'xml';

        const content = fs.readFileSync(fullPath, 'utf-8');
        const lineCount = (content.match(/\n/g) || []).length + 1;
        const byteCount = Buffer.byteLength(content, 'utf-8');

        out += `# ${relPath}\n\n`;
        out += `**Absolute Path**: \`${fullPath}\`  \n`;
        out += `**Lines**: ${lineCount} | **Bytes**: ${byteCount}\n\n`;
        out += `\`\`\`${ext}\n`;
        out += content;
        if (!content.endsWith('\n')) {
            out += '\n';
        }
        out += `\`\`\`\n\n---\n\n`;
    }

    fs.writeFileSync(filePath, out, 'utf-8');
}

function main() {
    console.log(`Generating consolidated and modular files from project root: ${PROJECT_ROOT}`);

    writeBundle(
        OUTPUT_FILE_MD,
        "iHome / Bliss Launcher - Consolidated Core Logic for Gemini Analysis",
        ALL_FILES,
        `This master document aggregates all ${ALL_FILES.length} core architectural and functional code logic files and manifests.`
    );
    fs.copyFileSync(OUTPUT_FILE_MD, OUTPUT_FILE_TXT);

    const masterSize = fs.statSync(OUTPUT_FILE_MD).size;
    console.log(`Master file updated: ${OUTPUT_FILE_MD} & .txt (${ALL_FILES.length} files, ${masterSize} bytes)`);

    fs.mkdirSync(PARTS_DIR, { recursive: true });
    for (const [partId, partInfo] of Object.entries(PARTS)) {
        const partMd = path.join(PARTS_DIR, `${partId}.md`);
        const partTxt = path.join(PARTS_DIR, `${partId}.txt`);
        writeBundle(
            partMd,
            partInfo.title,
            partInfo.files,
            `Modular code logic bundle designed to stay within Gemini attachment size limits (${partInfo.files.length} files).`
        );
        fs.copyFileSync(partMd, partTxt);
        const size = fs.statSync(partTxt).size;
        console.log(`  -> Generated ${partId}.txt/.md: ${partInfo.files.length} files, ${(size / 1024).toFixed(1)} KB`);
    }

    const manifestMd = path.join(PARTS_DIR, "android_manifest.md");
    const manifestTxt = path.join(PARTS_DIR, "android_manifest.txt");
    const part7Md = path.join(PARTS_DIR, "part7_android_manifest.md");
    if (fs.existsSync(part7Md)) {
        fs.copyFileSync(part7Md, manifestMd);
        fs.copyFileSync(part7Md, manifestTxt);
        console.log(`  -> Generated android_manifest.txt/.md alias`);
    }
}

main();
