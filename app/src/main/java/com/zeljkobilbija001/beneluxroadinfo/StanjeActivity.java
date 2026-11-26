package com.zeljkobilbija001.beneluxroadinfo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Stanje na putu za jedan autoput: udesi, kolone, zatvaranja + prekidač za obaveštenja. */
public class StanjeActivity extends AppCompatActivity {

    private static final String EXTRA_ID = "autoput";

    public static Intent namera(Context c, String id) {
        return new Intent(c, StanjeActivity.class).putExtra(EXTRA_ID, id);
    }

    private Autoput autoput;
    private DogadjajAdapter adapter;
    private TextView podnaslov, poruka;
    private MaterialSwitch prekidac;
    private View osvezi;
    private List<Dogadjaj> poslednje;
    private boolean ucitavanje, menjamPrekidac;

    private final ActivityResultLauncher<String> dozvola = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), data -> {
                if (data && Obavestenja.smeDaObavesti(this)) ukljuci(true);
                else odbijeno();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_stanje);

        autoput = Autoputevi.poId(getIntent().getStringExtra(EXTRA_ID));
        if (autoput == null) {
            finish();
            return;
        }
        Obavestenja.izabran(this, autoput.id);

        View koren = findViewById(R.id.koren);
        View zaglavlje = findViewById(R.id.zaglavlje);
        int gornji = zaglavlje.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(koren, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            zaglavlje.setPadding(zaglavlje.getPaddingLeft(), gornji + bars.top,
                    zaglavlje.getPaddingRight(), zaglavlje.getPaddingBottom());
            v.setPadding(bars.left, 0, bars.right, bars.bottom);
            return insets;
        });

        TextView stit = findViewById(R.id.stit); // štit po zemlji (DE plavi), a ne podrazumevani iz layouta
        stit.setText(autoput.oznaka());
        stit.setBackgroundResource(autoput.stit());
        ((TextView) findViewById(R.id.obavestenjaOpis)).setText(
                getString(R.string.stanje_obavestenja_opis, Obavestenja.MINUTA));
        podnaslov = findViewById(R.id.podnaslov);
        poruka = findViewById(R.id.poruka);
        osvezi = findViewById(R.id.osvezi);
        prekidac = findViewById(R.id.prekidac);

        RecyclerView lista = findViewById(R.id.lista);
        lista.setLayoutManager(new LinearLayoutManager(this));
        lista.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        adapter = new DogadjajAdapter(this::naMapi);
        lista.setAdapter(adapter);

        findViewById(R.id.nazad).setOnClickListener(v -> finish());
        osvezi.setOnClickListener(v -> ucitaj());
        findViewById(R.id.redObavestenja).setOnClickListener(v -> prekidac.toggle());
        prekidac.setOnCheckedChangeListener((b, ukljuceno) -> {
            if (menjamPrekidac) return;
            if (!ukljuceno) ukljuci(false);
            else if (Obavestenja.smeDaObavesti(this)) ukljuci(true);
            else if (Build.VERSION.SDK_INT >= 33) dozvola.launch(Manifest.permission.POST_NOTIFICATIONS);
            else odbijeno();
        });

        ucitaj();
        Reklame.prikaziBaner(this, findViewById(R.id.reklamaKontejner));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // prekidač je zajednički za sve autoputeve: uključen ako su obaveštenja uključena i obaveštenja su dozvoljena
        postaviPrekidac(Obavestenja.ukljucena(this) && Obavestenja.smeDaObavesti(this));
        if (!ucitavanje) prikaziListu();
    }

    private void postaviPrekidac(boolean on) {
        menjamPrekidac = true;
        prekidac.setChecked(on);
        menjamPrekidac = false;
    }

    private void ukljuci(boolean on) {
        Obavestenja.postavi(this, autoput.id, on, poslednje);
        postaviPrekidac(on);
        prikaziListu();
    }

    /** Dozvola odbijena: prekidač nazad i poruka sa prečicom do sistemskih podešavanja. */
    private void odbijeno() {
        postaviPrekidac(false);
        prikaziListu();
        // jasan prozor umesto sitne poruke na dnu: bez dozvole obaveštenja ne mogu da stižu
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.stanje_dozvola)
                .setMessage(R.string.stanje_dozvola_poruka)
                .setPositiveButton(R.string.stanje_podesavanja, (d, w) -> {
                    try {
                        startActivity(Build.VERSION.SDK_INT >= 26
                                ? new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                                : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", getPackageName(), null)));
                    } catch (Exception ignored) { }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void ucitaj() {
        if (ucitavanje) return;
        ucitavanje = true;
        osvezi.setEnabled(false);
        osvezi.animate().rotationBy(360f).setDuration(600).start();
        podnaslov.setText(R.string.stanje_ucitavanje);
        if (poslednje == null) prikaziPoruku(getString(R.string.stanje_ucitavanje));
        StanjeNaPutu.ucitaj(this, autoput.id, l -> {
            if (isFinishing() || isDestroyed()) return;
            ucitavanje = false;
            osvezi.setEnabled(true);
            if (l == null) {
                podnaslov.setText(autoput.ruta);
                if (poslednje == null) prikaziPoruku(getString(R.string.stanje_greska));
                else Toast.makeText(this, R.string.stanje_greska, Toast.LENGTH_SHORT).show();
                return;
            }
            poslednje = l;
            podnaslov.setText(getString(R.string.stanje_osvezeno,
                    DateFormat.getTimeFormat(this).format(new Date())));
            prikaziListu();
        });
    }

    /** Lista vesti samo kad su obaveštenja uključena; isključena = prazan ekran sa kratkom porukom. */
    private void prikaziListu() {
        if (!prekidac.isChecked()) {
            prikaziPoruku(getString(R.string.stanje_iskljuceno));
            return;
        }
        if (poslednje == null) {
            if (!ucitavanje) ucitaj();
            return;
        }
        adapter.postavi(poslednje, Obavestenja.poslednjeStiglo(this, autoput.id));
        if (poslednje.isEmpty()) prikaziPoruku(getString(R.string.stanje_nema, autoput.oznaka()));
        else poruka.setVisibility(View.GONE);
    }

    private void prikaziPoruku(String t) {
        adapter.postavi(java.util.Collections.emptyList(), null);
        poruka.setText(t);
        poruka.setVisibility(View.VISIBLE);
    }

    private void naMapi(Dogadjaj d) {
        Uri u = Uri.parse(String.format(Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f", d.lat, d.lon, d.lat, d.lon));
        Intent i = new Intent(Intent.ACTION_VIEW, u);
        try {
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US,
                    "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f", d.lat, d.lon))));
        }
    }
}
