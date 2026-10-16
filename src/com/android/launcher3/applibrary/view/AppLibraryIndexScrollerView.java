package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class AppLibraryIndexScrollerView extends View {

    public interface OnSectionSelectedListener {
        void onSectionSelected(String section);
    }

    private static final String[] SECTIONS = {
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J",
            "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U",
            "V", "W", "X", "Y", "Z", "#"
    };

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private OnSectionSelectedListener mListener;
    private int mLastSelectedIndex = -1;

    public AppLibraryIndexScrollerView(Context context) {
        this(context, null);
    }

    public AppLibraryIndexScrollerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibraryIndexScrollerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(false);
        float density = getResources().getDisplayMetrics().density;
        mPaint.setColor(0xCCFFFFFF); // Crisp semi-transparent white matching iOS
        mPaint.setTextSize(10f * density);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    }

    public void setOnSectionSelectedListener(OnSectionSelectedListener listener) {
        mListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int defaultWidth = (int) (18f * density);
        int defaultHeight = (int) (18f * density * SECTIONS.length); // 18dp per letter
        int width = resolveSize(defaultWidth, widthMeasureSpec);
        int height = resolveSize(defaultHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int height = getHeight();
        int width = getWidth();
        if (height <= 0 || width <= 0) return;

        float itemHeight = (float) height / SECTIONS.length;
        float fontCenterOffset = (mPaint.descent() + mPaint.ascent()) / 2f;

        for (int i = 0; i < SECTIONS.length; i++) {
            float x = width / 2f;
            float y = i * itemHeight + itemHeight / 2f - fontCenterOffset;
            canvas.drawText(SECTIONS[i], x, y, mPaint);
        }
    }

    private float mDownX;
    private float mDownY;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = event.getX();
                mDownY = event.getY();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                selectSectionAt(event.getY());
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - mDownX;
                float dy = event.getY() - mDownY;
                if (Math.abs(dx) > Math.abs(dy) * 2.0f && Math.abs(dx) > 40f) {
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    return false;
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                selectSectionAt(event.getY());
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mLastSelectedIndex = -1;
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void selectSectionAt(float y) {
        int height = getHeight();
        if (height <= 0) return;
        int index = (int) (y / ((float) height / SECTIONS.length));
        index = Math.max(0, Math.min(index, SECTIONS.length - 1));

        if (index != mLastSelectedIndex) {
            mLastSelectedIndex = index;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (mListener != null) {
                mListener.onSectionSelected(SECTIONS[index]);
            }
        }
    }
}
