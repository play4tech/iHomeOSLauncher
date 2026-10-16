package com.android.launcher3.applibrary.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Insettable
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.applibrary.model.AppLibraryCategory
import com.android.launcher3.applibrary.model.AppLibraryModel
import com.android.launcher3.applibrary.model.CategoryProvider
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.InsettableFrameLayout
import foundation.e.bliss.glass.PopupGlassView
import foundation.e.bliss.glass.LiquidGlassToast
import foundation.e.bliss.graphics.ContinuousCornerGeometry
import foundation.e.bliss.graphics.GlassShapeSpec

/**
 * Authentic iOS-style Liquid Glass Category Picker dialog.
 * Allows users to manually assign an application to any App Library category.
 */
class ChangeCategoryPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractFloatingView(context, attrs, defStyleAttr), Insettable {

    private val launcher: Launcher = Launcher.getLauncher(context)
    private lateinit var itemInfo: ItemInfo
    private lateinit var scrimView: View
    private lateinit var cardContainer: FrameLayout
    private lateinit var glassBg: PopupGlassView
    private lateinit var frostBase: View
    private lateinit var contentColumn: LinearLayout

    init {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    private fun isDarkTheme(): Boolean {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun buildUI() {
        removeAllViews()
        val density = resources.displayMetrics.density
        val cardWidth = (280 * density).toInt()
        val cornerRadiusPx = 20f * density
        val isDark = isDarkTheme()
        val dividerColor = Color.parseColor("#2E000000")
        val hairlineThickness = maxOf(1, (0.55f * density).toInt())
        val geometry = ContinuousCornerGeometry()

        val rootFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            clipChildren = false
            clipToPadding = false
        }

        // Full-screen dim scrim
        scrimView = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#59000000"))
            alpha = 0f
            isClickable = true
            isFocusable = false
            setOnClickListener { close(true) }
        }
        rootFrame.addView(scrimView)

        // Content Column
        contentColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                cardWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Header (Title & Subtitle)
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding((16 * density).toInt(), (18 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
        }

        val appTitle = itemInfo.title?.toString() ?: "App"

        val titleView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            text = context.getString(R.string.change_category_title)
            textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.BLACK)
        }
        headerLayout.addView(titleView)

        val messageView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (4 * density).toInt()
            }
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            text = context.getString(R.string.change_category_message, appTitle)
            textSize = 13f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(Color.parseColor("#7A000000"))
        }
        headerLayout.addView(messageView)
        contentColumn.addView(headerLayout)

        // Divider below header
        val headerDivider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, hairlineThickness)
            setBackgroundColor(dividerColor)
        }
        contentColumn.addView(headerDivider)

        // Category list inside ScrollView
        val categoryProvider = CategoryProvider.getInstance(context)
        val currentCategory = categoryProvider.getCategory(itemInfo)

        val categoriesToDisplay = AppLibraryCategory.values().filter { it != AppLibraryCategory.SUGGESTIONS }

        val categoryListContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val rowHeight = (44 * density).toInt()
        val rippleColor = Color.argb(25, 0, 0, 0)

        for (i in categoriesToDisplay.indices) {
            val cat = categoriesToDisplay[i]
            val isCurrent = (cat == currentCategory)

            if (i > 0) {
                val rowDivider = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, hairlineThickness)
                    setBackgroundColor(dividerColor)
                }
                categoryListContainer.addView(rowDivider)
            }

            val rowView = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeight)
                isClickable = true
                isFocusable = true
                background = RippleDrawable(
                    ColorStateList.valueOf(rippleColor),
                    ColorDrawable(Color.TRANSPARENT),
                    ColorDrawable(Color.WHITE)
                )
                setPadding((18 * density).toInt(), 0, (18 * density).toInt(), 0)

                val label = TextView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    }
                    text = cat.title
                    textSize = 16f
                    setTextColor(Color.BLACK)
                    if (isCurrent) {
                        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    } else {
                        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    }
                }
                addView(label)

                if (isCurrent) {
                    val checkmark = ImageView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            (20 * density).toInt(),
                            (20 * density).toInt()
                        ).apply {
                            gravity = Gravity.CENTER_VERTICAL or Gravity.END
                        }
                        setImageResource(R.drawable.ic_check_blue)
                    }
                    addView(checkmark)
                }

                setOnClickListener {
                    val pkg = (itemInfo.targetComponent?.packageName ?: itemInfo.targetPackage ?: "").lowercase()
                    if (pkg.isNotEmpty()) {
                        categoryProvider.setCategoryOverride(pkg, itemInfo.user, cat)
                        AppLibraryModel.getInstance(context).refreshCategories()

                        val expanded = AppLibraryCategoryExpandedView.getOpen(launcher)
                        expanded?.close(true)

                        val toastMessage = "Moved to ${cat.title}"
                        LiquidGlassToast.show(
                            launcher,
                            toastMessage,
                            R.drawable.ic_ios_check_circle
                        )
                    }
                    close(true)
                }
            }
            categoryListContainer.addView(rowView)
        }

        // Limit scroll view height so card doesn't exceed screen
        val screenHeight = resources.displayMetrics.heightPixels
        val maxScrollHeight = (screenHeight * 0.42f).toInt()

        val scrollView = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val mode = MeasureSpec.getMode(heightMeasureSpec)
                val size = MeasureSpec.getSize(heightMeasureSpec)
                val clampedHeight = if (mode == MeasureSpec.UNSPECIFIED) {
                    maxScrollHeight
                } else {
                    minOf(size, maxScrollHeight)
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(clampedHeight, MeasureSpec.AT_MOST))
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(categoryListContainer)
        }
        contentColumn.addView(scrollView)

        // Divider above Cancel
        val footerDivider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, hairlineThickness)
            setBackgroundColor(dividerColor)
        }
        contentColumn.addView(footerDivider)

        // Cancel Button
        val cancelBtn = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeight)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            text = "Cancel"
            textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor("#007AFF"))
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf(rippleColor),
                ColorDrawable(Color.TRANSPARENT),
                ColorDrawable(Color.WHITE)
            )
            setOnClickListener { close(true) }
        }
        contentColumn.addView(cancelBtn)

        // Glass background layers
        glassBg = PopupGlassView(context).apply {
            setPopupRadius(cornerRadiusPx)
        }

        frostBase = View(context).apply {
            setBackgroundColor(if (isDark) Color.argb(40, 0, 0, 0) else Color.TRANSPARENT)
        }

        val maxCardHeight = (screenHeight * 0.75f).toInt()
        cardContainer = object : FrameLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val widthSpec = MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY)
                contentColumn.measure(widthSpec, MeasureSpec.makeMeasureSpec(maxCardHeight, MeasureSpec.AT_MOST))
                val targetHeight = contentColumn.measuredHeight.coerceAtMost(maxCardHeight)
                val heightSpec = MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY)

                glassBg.measure(widthSpec, heightSpec)
                frostBase.measure(widthSpec, heightSpec)

                setMeasuredDimension(cardWidth, targetHeight)
            }

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                val w = right - left
                val h = bottom - top
                glassBg.layout(0, 0, w, h)
                frostBase.layout(0, 0, w, h)
                contentColumn.layout(0, 0, w, h)
                pivotX = w / 2f
                pivotY = h / 2f
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    if (view.width <= 0 || view.height <= 0) return
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val path = geometry.getPath(
                            width = view.width.toFloat(),
                            height = view.height.toFloat(),
                            radius = cornerRadiusPx,
                            smoothing = GlassShapeSpec.IOS_SMOOTHING
                        )
                        outline.setConvexPath(path)
                    } else {
                        outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                    }
                }
            }
            clipToOutline = true
        }

        cardContainer.addView(glassBg)
        cardContainer.addView(frostBase)
        cardContainer.addView(contentColumn)

        rootFrame.addView(cardContainer)
        addView(rootFrame)
    }

    private fun showInternal() {
        val dragLayer = launcher.dragLayer ?: return
        buildUI()

        val lp = InsettableFrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            ignoreInsets = true
        }
        layoutParams = lp
        elevation = 300f
        dragLayer.addView(this)
        mIsOpen = true
        launcher.updateDisallowBack()

        post {
            glassBg.bindToWallpaper()
        }

        cardContainer.scaleX = 0.88f
        cardContainer.scaleY = 0.88f
        cardContainer.alpha = 0f

        val spring = PathInterpolator(0.16f, 1f, 0.3f, 1f)
        val scaleXAnim = ObjectAnimator.ofFloat(cardContainer, View.SCALE_X, 0.88f, 1f)
        val scaleYAnim = ObjectAnimator.ofFloat(cardContainer, View.SCALE_Y, 0.88f, 1f)
        val alphaAnim = ObjectAnimator.ofFloat(cardContainer, View.ALPHA, 0f, 1f)
        val scrimAnim = ObjectAnimator.ofFloat(scrimView, View.ALPHA, 0f, 1f)

        AnimatorSet().apply {
            playTogether(scaleXAnim, scaleYAnim, alphaAnim, scrimAnim)
            duration = 260
            interpolator = spring
            start()
        }
    }

    override fun canHandleBack(): Boolean = true

    override fun onControllerInterceptTouchEvent(ev: MotionEvent?): Boolean = false

    private var isClosing = false

    override fun handleClose(animate: Boolean) {
        if (!mIsOpen || isClosing) return
        isClosing = true
        mIsOpen = false
        launcher.updateDisallowBack()

        if (animate) {
            val exitScaleX = ObjectAnimator.ofFloat(cardContainer, View.SCALE_X, 1f, 0.92f)
            val exitScaleY = ObjectAnimator.ofFloat(cardContainer, View.SCALE_Y, 1f, 0.92f)
            val exitCardAlpha = ObjectAnimator.ofFloat(cardContainer, View.ALPHA, 1f, 0f)
            val exitScrimAlpha = ObjectAnimator.ofFloat(scrimView, View.ALPHA, 1f, 0f)

            AnimatorSet().apply {
                playTogether(exitScaleX, exitScaleY, exitCardAlpha, exitScrimAlpha)
                duration = 150
                interpolator = AccelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        launcher.dragLayer?.removeView(this@ChangeCategoryPickerView)
                        launcher.updateDisallowBack()
                    }
                })
                start()
            }
        } else {
            launcher.dragLayer?.removeView(this)
            launcher.updateDisallowBack()
        }
    }

    override fun isOfType(type: Int): Boolean {
        return (type and TYPE_OPTIONS_POPUP_DIALOG) != 0
    }

    override fun setInsets(insets: Rect?) {}

    companion object {
        @JvmStatic
        fun show(launcher: Launcher, itemInfo: ItemInfo): ChangeCategoryPickerView {
            closeAllOpenViews(launcher)
            val view = ChangeCategoryPickerView(launcher).apply {
                this.itemInfo = itemInfo
            }
            view.showInternal()
            return view
        }
    }
}
