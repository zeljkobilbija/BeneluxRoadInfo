package com.zeljkobilbija001.beneluxroadinfo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;

/** Male pomoćne funkcije za izgled (dp, zaobljene pozadine). */
public final class Izgled {

    private Izgled() { }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static int boja(Context c, @ColorRes int id) {
        return ContextCompat.getColor(c, id);
    }

    /** Zaobljen pravougaonik sa ispunom i (po želji) okvirom. */
    public static GradientDrawable oblik(Context c, int ispuna, float radiusDp, int okvir, float okvirDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(c, radiusDp));
        g.setColor(ispuna);
        if (okvirDp > 0) g.setStroke(dp(c, okvirDp), okvir);
        return g;
    }

    public static void pozadina(View v, GradientDrawable g) {
        v.setBackground(g);
    }

    public static ColorStateList jedna(int boja) {
        return ColorStateList.valueOf(boja);
    }
}
