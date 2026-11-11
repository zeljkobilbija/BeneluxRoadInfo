package com.zeljkobilbija001.beneluxroadinfo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MainActivity extends AppCompatActivity {

    /** Kartica koja se okrenula pri otvaranju; pri povratku se vraća nazad. */
    private View okrenutaKartica;
    private boolean otvaranjeUToku;
    private ImageButton dugmePrivatnosti;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Splash mora pre super.onCreate()
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        RecyclerView lista = findViewById(R.id.listaAutoputeva);
        lista.setLayoutManager(new GridLayoutManager(this, 2));
        lista.setAdapter(new AutoputAdapter(Autoputevi.SVE, this::otvori));

        findViewById(R.id.dugmePodeli).setOnClickListener(v -> podeliAplikaciju());

        // Privatnost (GDPR) i baner: prozor za saglasnost se prikazuje samo kad je potreban
        dugmePrivatnosti = findViewById(R.id.dugmePrivatnosti);
        dugmePrivatnosti.setOnClickListener(v ->
                Reklame.prikaziPodesavanjaPrivatnosti(this, this::posleSaglasnosti));
        Reklame.prikupiSaglasnost(this, this::posleSaglasnosti);
    }

    private void posleSaglasnosti() {
        Reklame.prikaziBaner(this, findViewById(R.id.reklamaKontejner));
        dugmePrivatnosti.setVisibility(Reklame.trebaDugmePrivatnosti(this) ? View.VISIBLE : View.GONE);
    }

    /** Standardni Android meni za deljenje: kratka poruka i link ka aplikaciji na Play prodavnici. */
    private void podeliAplikaciju() {
        String link = "https://play.google.com/store/apps/details?id=" + getPackageName();
        Intent slanje = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                .putExtra(Intent.EXTRA_TEXT, getString(R.string.podeli_poruka, link));
        startActivity(Intent.createChooser(slanje, getString(R.string.podeli_naslov)));
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
