package com.zeljkobilbija001.beneluxroadinfo;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

/**
 * Ekran za probu (samo debug verzija): uživo stanje režima vožnje i ceo dnevnik odluka.
 * Tekstovi su namerno samo na srpskom – korisnici ga ne vide.
 */
public class DnevnikActivity extends AppCompatActivity {

    private final Handler glavna = new Handler(Looper.getMainLooper());
    private TextView stanje, dnevnik;
    private ScrollView skrol;

    private final Runnable osvezavanje = new Runnable() {
        @Override
        public void run() {
            osvezi(false);
            glavna.postDelayed(this, 5000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        if (!Dnevnik.ukljucen(this)) {
            finish();
            return;
        }
        int pad = Izgled.dp(this, 12);

        LinearLayout koren = new LinearLayout(this);
        koren.setOrientation(LinearLayout.VERTICAL);
        koren.setBackgroundColor(Izgled.boja(this, R.color.pozadina_liste));

        stanje = new TextView(this);
        stanje.setTextColor(0xFFFFFFFF);
        stanje.setBackgroundColor(Izgled.boja(this, R.color.plava));
        stanje.setPadding(pad, pad, pad, pad);
        stanje.setTextSize(13);
        koren.addView(stanje);

        LinearLayout dugmad = new LinearLayout(this);
        dugmad.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
        dugmad.addView(dugme("Osveži", () -> osvezi(true)));
        dugmad.addView(dugme("Pošalji", this::posalji));
        dugmad.addView(dugme("Obriši", () -> {
            Dnevnik.obrisi(this);
            osvezi(true);
        }));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.addView(dugmad);
        koren.addView(hs);

        skrol = new ScrollView(this);
        dnevnik = new TextView(this);
        dnevnik.setTypeface(Typeface.MONOSPACE);
        dnevnik.setTextSize(11);
        dnevnik.setTextColor(Izgled.boja(this, R.color.tekst));
        dnevnik.setPadding(pad, 0, pad, pad);
        dnevnik.setTextIsSelectable(true);
        skrol.addView(dnevnik);
        koren.addView(skrol, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(koren);
        ViewCompat.setOnApplyWindowInsetsListener(koren, (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(b.left, 0, b.right, b.bottom);
            stanje.setPadding(pad, pad + b.top, pad, pad);
            return insets;
        });
    }

    private MaterialButton dugme(String tekst, Runnable akcija) {
        MaterialButton b = new MaterialButton(this);
        b.setText(tekst);
        b.setAllCaps(false);
        b.setOnClickListener(v -> akcija.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Izgled.dp(this, 6));
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    protected void onResume() {
        super.onResume();
        osvezi(true);
        glavna.postDelayed(osvezavanje, 5000);
    }

    @Override
    protected void onPause() {
        glavna.removeCallbacks(osvezavanje);
        super.onPause();
    }

    private void osvezi(boolean naDno) {
        StringBuilder s = new StringBuilder();
        s.append("Režim vožnje: ").append(VoznjaService.radi ? "UKLJUČEN" : "isključen").append('\n');
        if (VoznjaService.radi) s.append(VoznjaService.opisStanja).append('\n').append(VoznjaService.uzivo).append('\n');
        stanje.setText(s);

        boolean bioNaDnu = !skrol.canScrollVertically(1);
        String t = Dnevnik.procitaj(this);
        dnevnik.setText(t.isEmpty() ? "Dnevnik je prazan. Uključi režim vožnje." : t);
        if (naDno || bioNaDnu) skrol.post(() -> skrol.fullScroll(ScrollView.FOCUS_DOWN));
    }

    /** Ceo dnevnik kao tekst (npr. Gmail ili Telegram), da se može poslati na analizu. */
    private void posalji() {
        String t = Dnevnik.procitaj(this);
        if (t.length() > 90_000) t = t.substring(t.length() - 90_000);
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "AutobahnInfo dnevnik")
                .putExtra(Intent.EXTRA_TEXT, t);
        startActivity(Intent.createChooser(i, "Pošalji dnevnik"));
    }
}
