package com.zeljkobilbija001.beneluxroadinfo;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** Autoputevi jedne zemlje (Holandija, Belgija ili Luksemburg). */
public class ZemljaActivity extends AppCompatActivity {

    private static final String EXTRA_ZEMLJA = "zemlja";

    /** Kartica koja se okrenula pri otvaranju; pri povratku se vraća nazad. */
    private View okrenutaKartica;
    private boolean otvaranjeUToku;
    private ImageButton dugmePrivatnosti;

    public static Intent namera(Context c, String zemlja) {
        return new Intent(c, ZemljaActivity.class).putExtra(EXTRA_ZEMLJA, zemlja);
    }

    @StringRes
    public static int ime(String zemlja) {
        switch (zemlja) {
            case "BE": return R.string.zemlja_be;
            case "LU": return R.string.zemlja_lu;
            default: return R.string.zemlja_nl;
        }
    }

    @DrawableRes
    public static int zastava(String zemlja) {
        switch (zemlja) {
            case "BE": return R.drawable.zastava_be;
            case "LU": return R.drawable.zastava_lu;
            default: return R.drawable.zastava_nl;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_zemlja);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        String zemlja = getIntent().getStringExtra(EXTRA_ZEMLJA);
        if (zemlja == null) zemlja = "NL";
        ((TextView) findViewById(R.id.imeZemlje)).setText(ime(zemlja));
        ((ImageView) findViewById(R.id.zastava)).setImageResource(zastava(zemlja));
        findViewById(R.id.nazad).setOnClickListener(v -> finish());

        RecyclerView lista = findViewById(R.id.listaAutoputeva);
        lista.setLayoutManager(new GridLayoutManager(this, 2));
        lista.setAdapter(new AutoputAdapter(Autoputevi.zaZemlju(zemlja), this::otvori));

        findViewById(R.id.dugmePodeli).setOnClickListener(v -> MainActivity.podeliAplikaciju(this));

        // Saglasnost je već prikupljena na početnom ekranu; ovde samo baner i dugme Privatnost
        dugmePrivatnosti = findViewById(R.id.dugmePrivatnosti);
        dugmePrivatnosti.setOnClickListener(v ->
                Reklame.prikaziPodesavanjaPrivatnosti(this, this::posleSaglasnosti));
        posleSaglasnosti();
    }

    private void posleSaglasnosti() {
        Reklame.prikaziBaner(this, findViewById(R.id.reklamaKontejner));
        dugmePrivatnosti.setVisibility(Reklame.trebaDugmePrivatnosti(this) ? View.VISIBLE : View.GONE);
    }

    /** Kartica se okrene oko vertikalne ose, pa se otvara ekran autoputa koji se "dookrene". */
    private void otvori(Autoput a, View kartica) {
        if (!a.dostupan()) {
            Toast.makeText(this, getString(R.string.uskoro_poruka, a.oznaka()), Toast.LENGTH_SHORT).show();
            return;
        }
        if (otvaranjeUToku) return;
        otvaranjeUToku = true;
        okrenutaKartica = kartica;
        kartica.setCameraDistance(9000 * getResources().getDisplayMetrics().density);
        kartica.animate()
                .rotationY(90f)
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setDuration(280)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    Intent i = AutoputActivity.namera(this, a.id)
                            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(i);
                    otvaranjeUToku = false;
                })
                .start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Povratak sa ekrana autoputa: kartica se okrene nazad na svoje mesto
        View k = okrenutaKartica;
        okrenutaKartica = null;
        if (k != null) {
            k.setRotationY(-90f);
            k.animate()
                    .rotationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(380)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }
}
