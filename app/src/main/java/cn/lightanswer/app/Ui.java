package cn.lightanswer.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG = Color.rgb(245,247,252), INK = Color.rgb(22,33,59), MUTED = Color.rgb(114,125,148);
    static final int BLUE = Color.rgb(48,85,233), GREEN = Color.rgb(21,139,104), RED = Color.rgb(220,66,84);
    static int dp(Context c, float n) { return Math.round(c.getResources().getDisplayMetrics().density * n); }
    static GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    static LinearLayout column(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    static LinearLayout row(Context c) { LinearLayout l = new LinearLayout(c); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    static TextView text(Context c, String value, int size, int color, boolean bold) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setLineSpacing(dp(c, 3), 1); return t;
    }
    static Button button(Context c, String label, int bg, int fg) {
        Button b = new Button(c); b.setText(label); b.setTextSize(15); b.setTextColor(fg); b.setAllCaps(false);
        b.setMinHeight(dp(c, 50)); b.setMinimumHeight(dp(c, 50)); b.setStateListAnimator(null);
        b.setBackground(shape(bg, dp(c, 14))); b.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        return b;
    }
    static void space(LinearLayout parent, int height) { View v = new View(parent.getContext()); parent.addView(v, new LinearLayout.LayoutParams(1, dp(parent.getContext(), height))); }
    static LinearLayout card(Context c) { LinearLayout l = column(c); l.setPadding(dp(c,20), dp(c,20), dp(c,20), dp(c,20)); l.setBackground(shape(Color.WHITE, dp(c,22))); return l; }
    static void insets(Activity a, View root, boolean dark) {
        int flags = dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        if (Build.VERSION.SDK_INT >= 30) a.getWindow().setDecorFitsSystemWindows(false);
        else flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        a.getWindow().setStatusBarColor(Color.TRANSPARENT);
        a.getWindow().setNavigationBarColor(Color.TRANSPARENT);
        a.getWindow().getDecorView().setSystemUiVisibility(flags);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(i.left, i.top, i.right, i.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return Build.VERSION.SDK_INT >= 30 ? WindowInsets.CONSUMED : insets.consumeSystemWindowInsets();
        });
        root.requestApplyInsets();
    }
    static LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1,-2); }
}
