package com.zeljkobilbija001.beneluxroadinfo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Početni ekran: izbor zemlje (Holandija, Belgija, Luksemburg). */
public class MainActivity extends AppCompatActivity {

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

        ViewGroup zemlje = findViewById(R.id.zemlje);
        LayoutInflater inflater = LayoutInflater.from(this);
        String autoputevi = getString(R.string.naslov);
        for (String z : Autoputevi.ZEMLJE) {
            View k = inflater.inflate(R.layout.item_zemlja, zemlje, false);
            String ime = getString(ZemljaActivity.ime(z));
            int broj = Autoputevi.zaZemlju(z).size();
            ((ImageView) k.findViewById(R.id.zastava)).setImageResource(ZemljaActivity.zastava(z));
            ((TextView) k.findViewById(R.id.imeZemlje)).setText(ime);
            ((TextView) k.findViewById(R.id.brojAutoputeva)).setText(autoputevi + " · " + broj);
            k.setContentDescription(ime + ", " + autoputevi + " " + broj);
            k.setOnClickListener(v -> startActivity(ZemljaActivity.namera(this, z)));
            zemlje.addView(k);
        }

        findViewById(R.id.dugmePodeli).setOnClickListener(v -> podeliAplikaciju(this));

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
    static void podeliAplikaciju(Activity a) {
        String link = "https://play.google.com/store/apps/details?id=" + a.getPackageName();
        Intent slanje = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, a.getString(R.string.app_name))
                .putExtra(Intent.EXTRA_TEXT, a.getString(R.string.podeli_poruka, link));
        a.startActivity(Intent.createChooser(slanje, a.getString(R.string.podeli_naslov)));
    }
}
