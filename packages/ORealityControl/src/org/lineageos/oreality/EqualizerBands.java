/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.oreality;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Ten vertical sliders, with the stock +/-6 dB range and whole-dB steps. */
final class EqualizerBands extends LinearLayout {
    interface Listener { void changed(int[] millibels); }
    private static final String[] FREQUENCIES = {"31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K"};
    private final int[] mGains = new int[10];
    private final Band[] mBands = new Band[10];
    private final TextView[] mValues = new TextView[10];
    private final Listener mListener;
    private boolean mEditable;

    EqualizerBands(Context context, Listener listener) {
        super(context); mListener = listener;
        setOrientation(HORIZONTAL); setLayoutDirection(LAYOUT_DIRECTION_LTR);
        for (int band = 0; band < 10; band++) {
            LinearLayout column = AudioUi.column(context); column.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            TextView value = new TextView(context); value.setTextSize(11);
            value.setTextColor(AudioUi.secondary(context)); mValues[band] = value; column.addView(value);
            Band slider = new Band(context, band); mBands[band] = slider;
            column.addView(slider, new LinearLayout.LayoutParams(-1, AudioUi.dp(context, 214)));
            TextView frequency = new TextView(context); frequency.setText(FREQUENCIES[band]);
            frequency.setTextSize(10); frequency.setTextColor(AudioUi.secondary(context)); column.addView(frequency);
            addView(column, new LinearLayout.LayoutParams(0, -2, 1));
        }
        setGains(new int[10], false);
    }

    void setGains(int[] gains, boolean editable) {
        mEditable = editable;
        for (int i = 0; i < 10; i++) {
            mGains[i] = Math.max(-600, Math.min(600, gains[i]));
            mValues[i].setText(AudioUi.gain(mGains[i]));
            mBands[i].setEnabled(editable); mBands[i].invalidate();
            mBands[i].setContentDescription(FREQUENCIES[i] + " Hz, " + mValues[i].getText() + " dB");
        }
        setAlpha(editable ? 1f : .45f);
    }

    private void change(int index, int gain) {
        gain = Math.max(-600, Math.min(600, Math.round(gain / 100f) * 100));
        if (mGains[index] == gain) return;
        mGains[index] = gain;
        mValues[index].setText(AudioUi.gain(gain)); mBands[index].invalidate();
        mBands[index].setContentDescription(FREQUENCIES[index] + " Hz, " + AudioUi.gain(gain) + " dB");
        mListener.changed(mGains.clone());
    }

    private final class Band extends View {
        final int mIndex;
        final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Band(Context context, int index) {
            super(context); mIndex = index; setFocusable(true); setClickable(true);
        }
        float top() { return AudioUi.dp(getContext(), 15); }
        float bottom() { return getHeight() - AudioUi.dp(getContext(), 15); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float x = getWidth() / 2f, mid = (top() + bottom()) / 2;
            float y = top() + (600 - mGains[mIndex]) * (bottom() - top()) / 1200f;
            mPaint.setStrokeCap(Paint.Cap.ROUND); mPaint.setStrokeWidth(AudioUi.dp(getContext(), 10));
            mPaint.setColor(AudioUi.secondary(getContext())); mPaint.setAlpha(40);
            canvas.drawLine(x, top(), x, bottom(), mPaint);
            mPaint.setAlpha(255); mPaint.setColor(AudioUi.accent(getContext()));
            mPaint.setStrokeWidth(AudioUi.dp(getContext(), 4)); canvas.drawLine(x, mid, x, y, mPaint);
            mPaint.setColor(AudioUi.color(getContext(), android.R.attr.textColorPrimary));
            canvas.drawCircle(x, y, AudioUi.dp(getContext(), 6), mPaint);
            if (isPressed() || isFocused()) {
                mPaint.setColor(AudioUi.accent(getContext())); mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(AudioUi.dp(getContext(), 2));
                canvas.drawCircle(x, y, AudioUi.dp(getContext(), 9), mPaint); mPaint.setStyle(Paint.Style.FILL);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (!mEditable) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true); setPressed(true); break;
                case MotionEvent.ACTION_MOVE: break;
                case MotionEvent.ACTION_UP:
                    setPressed(false); getParent().requestDisallowInterceptTouchEvent(false); performClick(); break;
                case MotionEvent.ACTION_CANCEL:
                    setPressed(false); getParent().requestDisallowInterceptTouchEvent(false); return true;
                default: return false;
            }
            float fraction = Math.max(0, Math.min(1, (event.getY() - top()) / (bottom() - top())));
            change(mIndex, Math.round(600 - fraction * 1200)); invalidate(); return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
        @Override public boolean onKeyDown(int key, KeyEvent event) {
            if (mEditable && (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN)) {
                change(mIndex, mGains[mIndex] + (key == KeyEvent.KEYCODE_DPAD_UP ? 100 : -100)); return true;
            }
            return super.onKeyDown(key, event);
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.SeekBar");
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                    AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, -6, 6, mGains[mIndex] / 100f));
            if (mEditable) {
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            }
        }
        @Override public boolean performAccessibilityAction(int action, Bundle args) {
            if (mEditable) {
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) { change(mIndex, mGains[mIndex] + 100); return true; }
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) { change(mIndex, mGains[mIndex] - 100); return true; }
                if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()
                        && args != null && args.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)) {
                    change(mIndex, Math.round(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) * 100)); return true;
                }
            }
            return super.performAccessibilityAction(action, args);
        }
    }
}
