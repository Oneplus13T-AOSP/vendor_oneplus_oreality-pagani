/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.oreality;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class AudioUi {
    static int dp(Context c, float value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    static int color(Context c, int attr) {
        TypedValue value = new TypedValue();
        c.getTheme().resolveAttribute(attr, value, true);
        return value.resourceId != 0 ? c.getColor(value.resourceId) : value.data;
    }
    static int accent(Context c) { return color(c, android.R.attr.colorAccent); }
    static int secondary(Context c) { return color(c, android.R.attr.textColorSecondary); }
    static int surface(Context c) { return color(c, android.R.attr.colorBackgroundFloating); }
    static GradientDrawable rounded(Context c, int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(dp(c, 18));
        if (stroke != Color.TRANSPARENT) d.setStroke(dp(c, 2), stroke);
        return d;
    }
    static void surface(View view) {
        Context c = view.getContext();
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22777777),
                rounded(c, surface(c), Color.TRANSPARENT), null));
    }
    static TextView text(Context c, int resource, float size) {
        TextView t = new TextView(c); t.setText(resource); t.setTextSize(size);
        t.setTextColor(color(c, android.R.attr.textColorPrimary));
        return t;
    }
    static LinearLayout column(Context c) {
        LinearLayout layout = new LinearLayout(c); layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    static LinearLayout.LayoutParams rowParams(Context c, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(c, bottom); return p;
    }
    static void pad(View view, int horizontal, int vertical) {
        view.setPadding(dp(view.getContext(), horizontal), dp(view.getContext(), vertical),
                dp(view.getContext(), horizontal), dp(view.getContext(), vertical));
    }
    static String gain(int millibels) {
        return String.format(java.util.Locale.getDefault(), "%+.0f", millibels / 100f);
    }
}
