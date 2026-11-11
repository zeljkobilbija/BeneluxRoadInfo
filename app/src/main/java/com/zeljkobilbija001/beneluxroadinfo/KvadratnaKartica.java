package com.zeljkobilbija001.beneluxroadinfo;

import android.content.Context;
import android.util.AttributeSet;

import com.google.android.material.card.MaterialCardView;

/** Kartica koja je uvek kvadrat (visina = širina). */
public class KvadratnaKartica extends MaterialCardView {

    public KvadratnaKartica(Context context) {
        super(context);
    }

    public KvadratnaKartica(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public KvadratnaKartica(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }
}
