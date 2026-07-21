/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.folder;

import static android.view.View.ALPHA;

import static com.android.launcher3.BubbleTextView.TEXT_ALPHA_PROPERTY;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.graphics.IconShape.getShape;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.util.Property;
import android.view.View;
import android.view.animation.AnimationUtils;

import androidx.core.content.ContextCompat;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PropertyResetListener;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.BaseDragLayer;

import java.util.List;

import foundation.e.bliss.multimode.MultiModeController;

/**
 * Manages the opening and closing animations for a {@link Folder}.
 *
 * All of the animations are done in the Folder.
 * ie. When the user taps on the FolderIcon, we immediately hide the FolderIcon and show the Folder
 * in its place before starting the animation.
 */
public class FolderAnimationManager {

    private static final int FOLDER_NAME_ALPHA_DURATION = 32;
    private static final int LARGE_FOLDER_FOOTER_DURATION = 128;

    private Folder mFolder;
    private FolderPagedView mContent;
    private GradientDrawable mFolderBackground;

    private FolderIcon mFolderIcon;
    private PreviewBackground mPreviewBackground;

    private Context mContext;

    private final boolean mIsOpening;

    private final int mDuration;
    private final int mDelay;

    private final TimeInterpolator mFolderInterpolator;
    private final TimeInterpolator mLargeFolderPreviewItemOpenInterpolator;
    private final TimeInterpolator mLargeFolderPreviewItemCloseInterpolator;

    private final PreviewItemDrawingParams mTmpParams = new PreviewItemDrawingParams(0, 0, 0);
    private final FolderGridOrganizer mPreviewVerifier;

    private ObjectAnimator mBgColorAnimator;

    private DeviceProfile mDeviceProfile;

    public FolderAnimationManager(Folder folder, boolean isOpening) {
        mFolder = folder;
        mContent = folder.mContent;
        mFolderBackground = (GradientDrawable) mFolder.getBackground();

        mFolderIcon = folder.mFolderIcon;
        mPreviewBackground = mFolderIcon.mBackground;

        mContext = folder.getContext();
        mDeviceProfile = folder.mActivityContext.getDeviceProfile();
        mPreviewVerifier = new FolderGridOrganizer(mDeviceProfile);

        mIsOpening = isOpening;

        Resources res = mContent.getResources();
        mDuration = res.getInteger(R.integer.config_materialFolderExpandDuration);
        mDelay = res.getInteger(R.integer.config_folderDelay);

        mFolderInterpolator = AnimationUtils.loadInterpolator(mContext,
                R.interpolator.standard_interpolator);
        mLargeFolderPreviewItemOpenInterpolator = AnimationUtils.loadInterpolator(mContext,
                R.interpolator.large_folder_preview_item_open_interpolator);
        mLargeFolderPreviewItemCloseInterpolator = AnimationUtils.loadInterpolator(mContext,
                R.interpolator.standard_accelerate_interpolator);
    }

    /**
     * Returns the animator that changes the background color.
     */
    public ObjectAnimator getBgColorAnimator() {
        return mBgColorAnimator;
    }

    /**
     * Prepares the Folder for animating between open / closed states.
     */
    public AnimatorSet getAnimator() {
        final BaseDragLayer.LayoutParams lp =
                (BaseDragLayer.LayoutParams) mFolder.getLayoutParams();
        mFolderIcon.getPreviewItemManager().recomputePreviewDrawingParams();
        ClippedFolderIconLayoutRule rule = mFolderIcon.getLayoutRule();
        final List<BubbleTextView> itemsInPreview = getPreviewIconsOnPage(0);

        // Match position of the FolderIcon
        final Rect folderIconPos = new Rect();
        float scaleRelativeToDragLayer = mFolder.mActivityContext.getDragLayer()
                .getDescendantRectRelativeToSelf(mFolderIcon, folderIconPos);
        int scaledRadius = mPreviewBackground.getScaledRadius();
        float initialSize = (scaledRadius * 2) * scaleRelativeToDragLayer;

        // Match size/scale of icons in the preview
        float previewScale = rule.scaleForItem(itemsInPreview.size());
        float previewSize = rule.getIconSize() * previewScale;
        float initialScale = previewSize / itemsInPreview.get(0).getIconSize()
                * scaleRelativeToDragLayer;
        final float finalScale = 1f;
        float scale = mIsOpening ? initialScale : finalScale;
        mFolder.setPivotX(0);
        mFolder.setPivotY(0);

        // Scale the contents of the folder.
        float contentScale = MultiModeController.isSingleLayerMode() ? 1f : scale;
        mFolder.mContent.setScaleX(contentScale);
        mFolder.mContent.setScaleY(contentScale);
        mFolder.mContent.setPivotX(0);
        mFolder.mContent.setPivotY(0);
        mFolder.mFooter.setScaleX(contentScale);
        mFolder.mFooter.setScaleY(contentScale);
        mFolder.mFooter.setPivotX(0);
        mFolder.mFooter.setPivotY(0);

        mFolder.updateFolderOnAnimate(mIsOpening);
        int unusedOffsetY = (int) (mFolder.getUnusedOffsetYOnAnimate(mIsOpening) * initialScale);


        // We want to create a small X offset for the preview items, so that they follow their
        // expected path to their final locations. ie. an icon should not move right, if it's final
        // location is to its left. This value is arbitrarily defined.
        int previewItemOffsetX = MultiModeController.isSingleLayerMode() ?  0: (int) (previewSize / 2);
        if (Utilities.isRtl(mContext.getResources())) {
            previewItemOffsetX = (int) (lp.width * initialScale - initialSize - previewItemOffsetX);
        }

        final int paddingOffsetX = (int) (mContent.getPaddingLeft() * initialScale);
        final int paddingOffsetY = (int) (mContent.getPaddingTop() * initialScale);

        int initialX = folderIconPos.left + mFolder.getPaddingLeft()
                + Math.round(mPreviewBackground.getOffsetX() * scaleRelativeToDragLayer)
                - paddingOffsetX - previewItemOffsetX;
        int initialY = folderIconPos.top + mFolder.getPaddingTop()
                + Math.round(mPreviewBackground.getOffsetY() * scaleRelativeToDragLayer)
                - paddingOffsetY;

        // In single-layer mode (GridFolder), we scale the entire folder (iOS style).
        // This requires aligning the scaled content position to the icon position.
        if (MultiModeController.isSingleLayerMode()
                && mFolder instanceof foundation.e.bliss.folder.GridFolder) {
            float folderScaleEnd = initialSize / Math.max(lp.width, 1);
            int folderTabHeight = ((foundation.e.bliss.folder.GridFolder) mFolder).mFolderTabHeight;
            
            float scaledOffsetX = (mFolder.getPaddingLeft() + mContent.getPaddingLeft() + previewItemOffsetX) * folderScaleEnd;
            float scaledOffsetY = (mFolder.getPaddingTop() + folderTabHeight + mContent.getPaddingTop()) * folderScaleEnd;
            
            initialX = (int) (folderIconPos.left + Math.round(mPreviewBackground.getOffsetX() * scaleRelativeToDragLayer) - scaledOffsetX);
            initialY = (int) (folderIconPos.top + Math.round(mPreviewBackground.getOffsetY() * scaleRelativeToDragLayer) - scaledOffsetY);
        }
        
        final float xDistance = initialX - lp.x;
        final float yDistance = initialY - lp.y - unusedOffsetY;

        // Set up the Folder background.
        int initialColor = Themes.getAttrColor(mContext, R.attr.folderPreviewColor);
        int finalColor = Themes.getAttrColor(mContext, R.attr.folderBackgroundColor);

        if (MultiModeController.isSingleLayerMode()) {
            initialColor = android.graphics.Color.TRANSPARENT;
            finalColor = android.graphics.Color.TRANSPARENT;
        }

        mFolderBackground.mutate();
        mFolderBackground.setColor(mIsOpening ? initialColor : finalColor);

        // Set up the reveal animation that clips the Folder.
        int totalOffsetX = paddingOffsetX + previewItemOffsetX;
        Rect startRect = new Rect(totalOffsetX,
                paddingOffsetY,
                Math.round((totalOffsetX + initialSize)),
                Math.round((paddingOffsetY + initialSize)));
        Rect endRect = new Rect(0, 0, lp.width, lp.height);
        float finalRadius = mFolderBackground.getCornerRadius();

        // Create the animators.
        AnimatorSet a = new AnimatorSet();

        // Initialize the Folder items' text.
        PropertyResetListener colorResetListener =
                new PropertyResetListener<>(TEXT_ALPHA_PROPERTY, 1f);
        for (BubbleTextView icon : mFolder.getItemsOnPage(mFolder.mContent.getCurrentPage())) {
            if (mIsOpening) {
                icon.setTextVisibility(false);
            }
            ObjectAnimator anim = icon.createTextAlphaAnimator(mIsOpening);
            anim.addListener(colorResetListener);
            play(a, anim);
        }

        mBgColorAnimator = getAnimator(mFolderBackground, "color", initialColor, finalColor);
        play(a, mBgColorAnimator);
        play(a, getAnimator(mFolder, View.TRANSLATION_X, xDistance, 0f));
        play(a, getAnimator(mFolder, View.TRANSLATION_Y, yDistance, 0f));
        
        // In standard Launcher3, the content and footer scale individually.
        // In iOS-style single-layer mode, we scale the ENTIRE folder view instead.
        if (!MultiModeController.isSingleLayerMode()) {
            play(a, getAnimator(mFolder.mContent, SCALE_PROPERTY, initialScale, finalScale));
            play(a, getAnimator(mFolder.mFooter, SCALE_PROPERTY, initialScale, finalScale));
        }

        final int footerAlphaDuration;
        final int footerStartDelay;
        if (isLargeFolder()) {
            if (mIsOpening) {
                mFolder.mFooter.setAlpha(0);
                footerAlphaDuration = LARGE_FOLDER_FOOTER_DURATION;
                footerStartDelay = mDuration - footerAlphaDuration;
            } else {
                footerAlphaDuration = 0;
                footerStartDelay = 0;
            }
        } else {
            footerStartDelay = 0;
            footerAlphaDuration = mDuration;
        }
        play(a, getAnimator(mFolder.mFooter, ALPHA, 0, 1f), footerStartDelay, footerAlphaDuration);

        // Create reveal animator for the folder background.
        if (MultiModeController.isSingleLayerMode()) {
            // iOS-style: scale the entire folder view symmetrically toward the icon,
            // and fade it out. This avoids the asymmetric clip-reveal that cuts
            // the right side off with a sharp edge.
            float folderScaleEnd = initialSize / Math.max(lp.width, 1);
            mFolder.setPivotX(0f);
            mFolder.setPivotY(0f);
            
            float folderScale = mIsOpening ? folderScaleEnd : 1f;
            mFolder.setScaleX(folderScale);
            mFolder.setScaleY(folderScale);
            
            play(a, getAnimator(mFolder, View.SCALE_X, folderScaleEnd, 1f));
            play(a, getAnimator(mFolder, View.SCALE_Y, folderScaleEnd, 1f));
            // Fade out the folder as it shrinks
            play(a, getAnimator(mFolder, View.ALPHA, 0f, 1f));
        } else {
            play(a, getShape().createRevealAnimator(
                    mFolder.getAnimateObject(), startRect, endRect, finalRadius, !mIsOpening));
        }
        if (MultiModeController.isSingleLayerMode()) {
            com.android.launcher3.Launcher launcher = com.android.launcher3.Launcher.getLauncher(mContext);
            BaseDragLayer dragLayer = mFolder.mActivityContext.getDragLayer();
            View dockGlass = dragLayer.findViewById(R.id.dock_glass);
            View hotseat = dragLayer.findViewById(R.id.hotseat);
            View pageIndicator = dragLayer.findViewById(R.id.page_indicator);
            View workspace = dragLayer.findViewById(R.id.workspace);
            View scrimView = dragLayer.findViewById(R.id.scrim_view);
            foundation.e.bliss.blur.BlurBackgroundView blurLayer = launcher.mBlurLayer;
            View qsb = hotseat instanceof com.android.launcher3.Hotseat ? ((com.android.launcher3.Hotseat) hotseat).getQsb() : null;
            
            // v1 = closed-state value, v2 = open-state value (fixed constants -- getAnimator's
            // own mIsOpening check handles direction, so don't also branch on it here or read
            // a live .getAlpha(); doing both was double-applying direction and caused these
            // views to animate the wrong way on close).
            if (dockGlass != null) {
                play(a, getAnimator(dockGlass, View.ALPHA, 1f, 0f));
            }
            if (hotseat != null) {
                play(a, getAnimator(hotseat, View.ALPHA, 1f, 0f));
            }
            if (pageIndicator != null) {
                play(a, getAnimator(pageIndicator, View.ALPHA, 1f, 0f));
            }
            if (workspace != null) {
                play(a, getAnimator(workspace, View.ALPHA, 1f, 0f));
            }
            if (qsb != null) {
                play(a, getAnimator(qsb, View.ALPHA, 1f, 0f));
            }
            if (scrimView != null) {
                play(a, getAnimator(scrimView, View.ALPHA, 1f, 0f));
            }
            if (blurLayer != null) {
                play(a, getAnimator(blurLayer, View.ALPHA, 0f, 1f));
            }
            
            // Cross-fade the folder icon back in during close so it doesn't blink at the end.
            // Use ObjectAnimator directly — getAnimator() reverses values on close, which would
            // fade the icon OUT (1→0) instead of IN (0→1).
            if (!mIsOpening && mFolderIcon != null) {
                // Restore mBackgroundIsVisible so the canvas actually draws the icon content.
                // setIconVisible(false) was called when opening; without this the icon draws nothing.
                mFolderIcon.setIconVisible(true);
                mFolderIcon.setAlpha(0f);
                mFolderIcon.setVisibility(View.VISIBLE);
                int iconFadeStartDelay = mDuration / 2;
                int iconFadeDuration = mDuration - iconFadeStartDelay;
                ObjectAnimator iconFade = ObjectAnimator.ofFloat(mFolderIcon, View.ALPHA, 0f, 1f);
                play(a, iconFade, iconFadeStartDelay, iconFadeDuration);
            }
        }

        int column = MultiModeController.isSingleLayerMode() ? 3 : 2;
        int row = MultiModeController.isSingleLayerMode() ? 3 : 2;

        // Create reveal animator for the folder content (capture the top 4 icons 2x2)
        int width = mDeviceProfile.folderCellLayoutBorderSpacePx.x
                + mDeviceProfile.folderCellWidthPx * row;
        int height = mDeviceProfile.folderCellLayoutBorderSpacePx.y
                + mDeviceProfile.folderCellHeightPx * column;
        int page = mIsOpening ? mContent.getCurrentPage() : mContent.getDestinationPage();
        int left = mContent.getPaddingLeft() + page * lp.width;
        Rect contentStart = new Rect(left, 0, left + width, height);
        Rect contentEnd = new Rect(left, 0, left + lp.width, lp.height);
        if (!MultiModeController.isSingleLayerMode()) {
            play(a, getShape().createRevealAnimator(
                    mFolder.getContent(), contentStart, contentEnd, finalRadius, !mIsOpening));
        }


        // Fade in the folder name, as the text can overlap the icons when grid size is small.
        mFolder.mFolderName.setAlpha(mIsOpening ? 0f : 1f);
        play(a, getAnimator(mFolder.mFolderName, View.ALPHA, 0, 1),
                mIsOpening ? FOLDER_NAME_ALPHA_DURATION : 0,
                mIsOpening ? mDuration - FOLDER_NAME_ALPHA_DURATION : FOLDER_NAME_ALPHA_DURATION);

        // Translate the footer so that it tracks the bottom of the content.
        float normalHeight = mFolder.getContentAreaHeight();
        float scaledHeight = normalHeight * initialScale;
        float diff = normalHeight - scaledHeight;
        play(a, getAnimator(mFolder.mFooter, View.TRANSLATION_Y, -diff, 0f));

        // Animate the elevation midway so that the shadow is not noticeable in the background.
        int midDuration = mDuration / 2;
        Animator z = getAnimator(mFolder, View.TRANSLATION_Z, -mFolder.getElevation(), 0);
        play(a, z, mIsOpening ? midDuration : 0, midDuration);

        // Store clip variables.
        // Because {@link #onAnimationStart} and {@link #onAnimationEnd} callbacks are sent to
        // message queue and executed on separate frame, we should save states in
        // {@link #onAnimationStart} instead of before creating animator, so that cancelling
        // animation A and restarting animation B allows A to reset states in
        // {@link #onAnimationEnd} before B reads new UI state from {@link #onAnimationStart}.
        a.addListener(new AnimatorListenerAdapter() {
            private CellLayout mCellLayout;

            private boolean mFolderClipChildren;
            private boolean mFolderClipToPadding;
            private boolean mContentClipChildren;
            private boolean mContentClipToPadding;
            private boolean mCellLayoutClipChildren;
            private boolean mCellLayoutClipPadding;

            @Override
            public void onAnimationStart(Animator animator) {
                super.onAnimationStart(animator);
                mCellLayout = mContent.getCurrentCellLayout();
                mFolderClipChildren = mFolder.getClipChildren();
                mFolderClipToPadding = mFolder.getClipToPadding();
                mContentClipChildren = mContent.getClipChildren();
                mContentClipToPadding = mContent.getClipToPadding();
                mCellLayoutClipChildren = mCellLayout.getClipChildren();
                mCellLayoutClipPadding = mCellLayout.getClipToPadding();

                mFolder.setClipChildren(false);
                mFolder.setClipToPadding(false);
                mContent.setClipChildren(false);
                mContent.setClipToPadding(false);
                mCellLayout.setClipChildren(false);
                mCellLayout.setClipToPadding(false);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mFolder.setTranslationX(0.0f);
                mFolder.setTranslationY(0.0f);
                mFolder.setTranslationZ(0.0f);
                mFolder.setScaleX(1f);
                mFolder.setScaleY(1f);
                mFolder.setAlpha(1f);
                mFolder.mContent.setScaleX(1f);
                mFolder.mContent.setScaleY(1f);
                mFolder.mFooter.setScaleX(1f);
                mFolder.mFooter.setScaleY(1f);
                mFolder.mFooter.setTranslationX(0f);
                mFolder.mFolderName.setAlpha(1f);

                mFolder.setClipChildren(mFolderClipChildren);
                mFolder.setClipToPadding(mFolderClipToPadding);
                mContent.setClipChildren(mContentClipChildren);
                mContent.setClipToPadding(mContentClipToPadding);
                mCellLayout.setClipChildren(mCellLayoutClipChildren);
                mCellLayout.setClipToPadding(mCellLayoutClipPadding);
            }
        });

        // We set the interpolator on all current child animators here, because the preview item
        // animators may use a different interpolator.
        for (Animator animator : a.getChildAnimations()) {
            animator.setInterpolator(mFolderInterpolator);
        }

        int radiusDiff = scaledRadius - mPreviewBackground.getRadius();
        addPreviewItemAnimators(a, initialScale / scaleRelativeToDragLayer,
                // Background can have a scaled radius in drag and drop mode, so we need to add the
                // difference to keep the preview items centered.
                (int) (previewItemOffsetX / scaleRelativeToDragLayer) + radiusDiff, radiusDiff);
        return a;
    }

    /**
     * Returns the list of "preview items" on {@param page}.
     */
    private List<BubbleTextView> getPreviewIconsOnPage(int page) {
        return mPreviewVerifier.setFolderInfo(mFolder.mInfo)
                .previewItemsForPage(page, mFolder.getIconsInReadingOrder());
    }

    /**
     * Animate the items on the current page.
     */
    private void addPreviewItemAnimators(AnimatorSet animatorSet, final float folderScale,
            int previewItemOffsetX, int previewItemOffsetY) {
        ClippedFolderIconLayoutRule rule = mFolderIcon.getLayoutRule();
        boolean isOnFirstPage = mFolder.mContent.getCurrentPage() == 0;
        final List<BubbleTextView> itemsInPreview = getPreviewIconsOnPage(
                isOnFirstPage ? 0 : mFolder.mContent.getCurrentPage());
        final int numItemsInPreview = itemsInPreview.size();
        final int numItemsInFirstPagePreview = isOnFirstPage
                ? numItemsInPreview : mFolderIcon.getLayoutRule().getMaxNumItemsInPreview();

        TimeInterpolator previewItemInterpolator = getPreviewItemInterpolator();

        ShortcutAndWidgetContainer cwc = mContent.getPageAt(0).getShortcutsAndWidgets();
        for (int i = 0; i < numItemsInPreview; ++i) {
            final BubbleTextView btv = itemsInPreview.get(i);
            CellLayoutLayoutParams btvLp = (CellLayoutLayoutParams) btv.getLayoutParams();

            // Calculate the final values in the LayoutParams.
            btvLp.isLockedToGrid = true;
            cwc.setupLp(btv);

            // Match scale of icons in the preview of the items on the first page.
            float previewScale = rule.scaleForItem(numItemsInFirstPagePreview);
            float previewSize = rule.getIconSize() * previewScale;
            float iconScale = previewSize / itemsInPreview.get(i).getIconSize();

            final float initialScale = iconScale / folderScale;
            final float finalScale = 1f;
            float scale = mIsOpening ? initialScale : finalScale;
            btv.setScaleX(scale);
            btv.setScaleY(scale);

            // Match positions of the icons in the folder with their positions in the preview
            rule.computePreviewItemDrawingParams(i, numItemsInFirstPagePreview, mTmpParams);
            // The PreviewLayoutRule assumes that the icon size takes up the entire width so we
            // offset by the actual size.
            int iconOffsetX = (int) ((btvLp.width - btv.getIconSize()) * iconScale) / 2;

            final int previewPosX =
                    (int) ((mTmpParams.transX - iconOffsetX + previewItemOffsetX) / folderScale);
            final float paddingTop = btv.getPaddingTop() * iconScale;
            final int previewPosY = (int) ((mTmpParams.transY + previewItemOffsetY - paddingTop)
                    / folderScale);

            final float xDistance = previewPosX - btvLp.x;
            final float yDistance = previewPosY - btvLp.y;

            Animator translationX = getAnimator(btv, View.TRANSLATION_X, xDistance, 0f);
            translationX.setInterpolator(previewItemInterpolator);
            play(animatorSet, translationX);

            Animator translationY = getAnimator(btv, View.TRANSLATION_Y, yDistance, 0f);
            translationY.setInterpolator(previewItemInterpolator);
            play(animatorSet, translationY);

            Animator scaleAnimator = getAnimator(btv, SCALE_PROPERTY, initialScale, finalScale);
            scaleAnimator.setInterpolator(previewItemInterpolator);
            play(animatorSet, scaleAnimator);

            if (mFolder.getItemCount() > mFolderIcon.getLayoutRule().getMaxNumItemsInPreview()) {
                // These delays allows the preview items to move as part of the Folder's motion,
                // and its only necessary for large folders because of differing interpolators.
                int delay = mIsOpening ? mDelay : mDelay * 2;
                if (mIsOpening) {
                    translationX.setStartDelay(delay);
                    translationY.setStartDelay(delay);
                    scaleAnimator.setStartDelay(delay);
                }
                translationX.setDuration(translationX.getDuration() - delay);
                translationY.setDuration(translationY.getDuration() - delay);
                scaleAnimator.setDuration(scaleAnimator.getDuration() - delay);
            }

            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    // Necessary to initialize values here because of the start delay.
                    if (mIsOpening) {
                        btv.setTranslationX(xDistance);
                        btv.setTranslationY(yDistance);
                        btv.setScaleX(initialScale);
                        btv.setScaleY(initialScale);
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    btv.setTranslationX(0.0f);
                    btv.setTranslationY(0.0f);
                    btv.setScaleX(1f);
                    btv.setScaleY(1f);
                }
            });
        }
    }

    private void play(AnimatorSet as, Animator a) {
        play(as, a, a.getStartDelay(), mDuration);
    }

    private void play(AnimatorSet as, Animator a, long startDelay, int duration) {
        a.setStartDelay(startDelay);
        a.setDuration(duration);
        as.play(a);
    }

    private boolean isLargeFolder() {
        return mFolder.getItemCount() > mFolderIcon.getLayoutRule().getMaxNumItemsInPreview();
    }

    private TimeInterpolator getPreviewItemInterpolator() {
        if (isLargeFolder()) {
            // With larger folders, we want the preview items to reach their final positions faster
            // (when opening) and later (when closing) so that they appear aligned with the rest of
            // the folder items when they are both visible.
            return mIsOpening
                    ? mLargeFolderPreviewItemOpenInterpolator
                    : mLargeFolderPreviewItemCloseInterpolator;
        }
        return mFolderInterpolator;
    }

    private Animator getAnimator(View view, Property property, float v1, float v2) {
        return mIsOpening
                ? ObjectAnimator.ofFloat(view, property, v1, v2)
                : ObjectAnimator.ofFloat(view, property, v2, v1);
    }

    private ObjectAnimator getAnimator(GradientDrawable drawable, String property, int v1, int v2) {
        return mIsOpening
                ? ObjectAnimator.ofArgb(drawable, property, v1, v2)
                : ObjectAnimator.ofArgb(drawable, property, v2, v1);
    }
}
