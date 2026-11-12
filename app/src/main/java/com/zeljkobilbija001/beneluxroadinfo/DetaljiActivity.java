package com.zeljkobilbija001.beneluxroadinfo;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.ColorRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ekran jednog mesta na autoputu (dodir na red liste): gde je, šta tamo ima,
 * a na dnu mapa, navigacija i deljenje.
 */
public class DetaljiActivity extends AppCompatActivity {

    private static final String EXTRA_AUTOPUT = "autoput", EXTRA_STAVKA = "stavka";

    private Autoput autoput;
    private Podaci podaci;
    private Stavka s;
    private LinearLayout sadrzaj;

    public static Intent namera(Context c, String autoputId, int stavka) {
        return new Intent(c, DetaljiActivity.class)
                .putExtra(EXTRA_AUTOPUT, autoputId)
                .putExtra(EXTRA_STAVKA, stavka);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_detalji);

        autoput = Autoputevi.poId(getIntent().getStringExtra(EXTRA_AUTOPUT));
        podaci = autoput == null ? null : Podaci.za(this, autoput);
        int i = getIntent().getIntExtra(EXTRA_STAVKA, -1);
        if (podaci == null || i < 0 || i >= podaci.stavke.size()) {
            finish();
            return;
        }
        s = podaci.stavke.get(i);

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

        TextView stit = findViewById(R.id.stit);
        stit.setText(autoput.oznaka());
        stit.setBackgroundResource(autoput.stit());
        ((TextView) findViewById(R.id.naziv)).setText(StavkaAdapter.naziv(this, s));
        ((TextView) findViewById(R.id.opis)).setText(StavkaAdapter.opis(this, s));

        findViewById(R.id.nazad).setOnClickListener(v -> finish());
        View navigacija = findViewById(R.id.navigacija);
        if (s.imaKoordinate()) navigacija.setOnClickListener(v -> navigiraj());
        else navigacija.setVisibility(View.GONE);
        findViewById(R.id.podeli).setOnClickListener(v -> podeli());

        sadrzaj = findViewById(R.id.sadrzaj);
        napraviSadrzaj();
    }

    private void napraviSadrzaj() {
        Deonica d = podaci.deonice.get(s.deonica);

        // Gde je mesto
        LinearLayout k = kartica(R.string.det_lokacija, R.color.plava_tekst);
        red(k, getString(R.string.det_autoput), autoput.oznaka() + " · " + autoput.ruta);
        red(k, getString(R.string.det_deonica), d.naziv());
        red(k, getString(R.string.det_kilometar), String.format(Locale.getDefault(), "%.1f", s.km));
        if (s.jeIzlaz() && !s.ref.isEmpty()) {
            red(k, getString(s.tip == Stavka.CVORISTE ? R.string.det_ka : R.string.det_broj_izlaza), s.ref);
        }
        if (!s.jeIzlaz()) {
            red(k, getString(R.string.smer_voznje),
                    s.strana == '+' ? d.minus + " → " + d.plus : d.plus + " → " + d.minus);
        }

        // Gorivo
        if (!s.gorivo.isEmpty()) {
            LinearLayout g = kartica(R.string.det_gorivo, R.color.cip_gorivo_tekst);
            cipovi(g, s.gorivo, R.color.cip_gorivo, R.color.cip_gorivo_tekst);
            cene(g);
        }

        // Hrana
        if (s.imaHranu()) {
            LinearLayout h = kartica(R.string.det_hrana, R.color.cip_hrana_tekst);
            List<String> l = new ArrayList<>(s.hrana);
            if (l.isEmpty()) l.add(getString(R.string.cip_restoran));
            cipovi(h, l, R.color.cip_hrana, R.color.cip_hrana_tekst);
        }

        // Punjači za električna vozila
        if (!s.punjaci.isEmpty()) {
            LinearLayout p = kartica(R.string.det_punjaci, R.color.cip_struja_tekst);
            cipovi(p, s.punjaci, R.color.cip_struja, R.color.cip_struja_tekst);
        }

        // Ostali sadržaji
        List<String> usluge = new ArrayList<>();
        if (s.wc) usluge.add(getString(R.string.cip_wc));
        if (s.tus) usluge.add(getString(R.string.cip_tus));
        if (s.prodavnica) usluge.add(getString(R.string.cip_prodavnica));
        if (s.hotel) usluge.add(getString(R.string.cip_hotel));
        if (!s.kamioni.isEmpty()) usluge.add(getString(R.string.cip_kamioni, s.kamioni));
        if (!usluge.isEmpty() || !s.radnoVreme.isEmpty()) {
            LinearLayout u = kartica(R.string.det_usluge, R.color.cip_obicno_tekst);
            if (!usluge.isEmpty()) cipovi(u, usluge, R.color.cip_obicno, R.color.cip_obicno_tekst);
            if (!s.radnoVreme.isEmpty()) {
                String rv = "24/7".equals(s.radnoVreme) ? getString(R.string.cip_0_24) : s.radnoVreme;
                red(u, getString(R.string.det_radno_vreme), rv);
            }
        }

        if (!s.jeIzlaz() && s.tip != Stavka.PARKING && s.gorivo.isEmpty() && !s.imaHranu()
                && s.punjaci.isEmpty() && usluge.isEmpty()) {
            TextView t = tekst(getString(R.string.det_nema_podataka), 14, R.color.tekst_sekundarni, false);
            t.setPadding(Izgled.dp(this, 4), Izgled.dp(this, 12), Izgled.dp(this, 4), 0);
            sadrzaj.addView(t);
        }

        TextView izvor = tekst(getString(R.string.det_izvor), 12, R.color.tekst_sekundarni, false);
        izvor.setPadding(Izgled.dp(this, 4), Izgled.dp(this, 14), Izgled.dp(this, 4), Izgled.dp(this, 8));
        sadrzaj.addView(izvor);
    }

    /** Cene goriva ispod brendova; stižu sa interneta (ili iz keša). */
    private void cene(LinearLayout k) {
        LinearLayout blok = new LinearLayout(this);
        blok.setOrientation(LinearLayout.VERTICAL);
        blok.setPadding(0, Izgled.dp(this, 12), 0, 0);
        k.addView(blok);
        blok.addView(tekst(getString(R.string.det_cene_ucitavanje), 13, R.color.tekst_sekundarni, false));

        CeneGoriva.ucitaj(this, autoput.zemlja, blokovi -> {
            if (isFinishing() || isDestroyed()) return;
            blok.removeAllViews();
            if (blokovi.isEmpty()) {
                blok.addView(tekst(getString(R.string.det_cene_greska), 13, R.color.tekst_sekundarni, false));
                return;
            }
            for (CeneGoriva.Blok b : blokovi) {
                View linija = new View(this);
                linija.setBackgroundColor(Izgled.boja(this, R.color.linija));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Izgled.dp(this, 1));
                lp.topMargin = Izgled.dp(this, blok.getChildCount() > 0 ? 10 : 0);
                lp.bottomMargin = Izgled.dp(this, 4);
                blok.addView(linija, lp);

                for (CeneGoriva.Cena c : b.cene) {
                    LinearLayout red = new LinearLayout(this);
                    red.setOrientation(LinearLayout.HORIZONTAL);
                    red.setPadding(0, Izgled.dp(this, 6), 0, Izgled.dp(this, 6));
                    red.addView(tekst(c.naziv(this), 16, R.color.tekst, false),
                            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                    red.addView(tekst(String.format(Locale.getDefault(), "%.3f %s", c.eura, c.jedinica),
                            17, R.color.tekst, true));
                    blok.addView(red);
                }

                int napomena;
                switch (b.vrsta) {
                    case CeneGoriva.BE_MAKS: napomena = R.string.det_cene_be; break;
                    case CeneGoriva.NL_DAN: napomena = R.string.det_cene_nl; break;
                    case CeneGoriva.NL_KVARTAL: napomena = R.string.det_cene_nl_kv; break;
                    case CeneGoriva.LU_MESEC: napomena = R.string.det_cene_lu_mes; break;
                    default: napomena = R.string.det_cene_lu;
                }
                TextView t = tekst(getString(napomena, b.period), 12.5f, R.color.tekst_sekundarni, false);
                t.setPadding(0, Izgled.dp(this, 4), 0, 0);
                blok.addView(t);
            }
        });
    }

    /** Bela kartica sa naslovom; vraća je da se u nju dodaju redovi. */
    private LinearLayout kartica(int naslov, @ColorRes int boja) {
        LinearLayout k = new LinearLayout(this);
        k.setOrientation(LinearLayout.VERTICAL);
        k.setBackground(Izgled.oblik(this, Izgled.boja(this, R.color.povrsina), 14,
                Izgled.boja(this, R.color.linija), 1));
        int p = Izgled.dp(this, 14);
        k.setPadding(p, Izgled.dp(this, 12), p, Izgled.dp(this, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Izgled.dp(this, 10);
        sadrzaj.addView(k, lp);

        TextView n = tekst(getString(naslov).toUpperCase(Locale.getDefault()), 12.5f, boja, true);
        n.setLetterSpacing(0.06f);
        k.addView(n);
        return k;
    }

    /** Red „naziv: vrednost“ u kartici. */
    private void red(LinearLayout k, String naziv, String vrednost) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, Izgled.dp(this, 8), 0, 0);
        r.addView(tekst(naziv, 12.5f, R.color.tekst_sekundarni, false));
        r.addView(tekst(vrednost, 16, R.color.tekst, false));
        k.addView(r);
    }

    private void cipovi(LinearLayout k, List<String> tekstovi, @ColorRes int pozadina, @ColorRes int boja) {
        ChipGroup g = new ChipGroup(this);
        g.setChipSpacingHorizontal(Izgled.dp(this, 6));
        g.setChipSpacingVertical(Izgled.dp(this, 6));
        g.setPadding(0, Izgled.dp(this, 8), 0, 0);
        for (String t : tekstovi) {
            TextView c = tekst(t, 15, boja, true);
            c.setBackground(Izgled.oblik(this, Izgled.boja(this, pozadina), 8, 0, 0));
            int h = Izgled.dp(this, 10), v = Izgled.dp(this, 5);
            c.setPadding(h, v, h, v);
            g.addView(c);
        }
        k.addView(g);
    }

    private TextView tekst(String t, float sp, @ColorRes int boja, boolean podebljano) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(Izgled.boja(this, boja));
        if (podebljano) v.setTypeface(v.getTypeface(), Typeface.BOLD);
        return v;
    }

    private String koordinate() {
        return String.format(Locale.US, "%.5f,%.5f", s.lat, s.lon);
    }

    private void navigiraj() {
        if (!pokreni(new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + koordinate())))) {
            pokreni(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + koordinate())));
        }
    }

    private boolean pokreni(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    /**
     * Deljenje mesta: naziv, vrsta, autoput/deonica/kilometar, smer,
     * šta tamo ima i link ka mapi (Google Maps radi i u pregledaču).
     */
    private void podeli() {
        Deonica d = podaci.deonice.get(s.deonica);
        StringBuilder t = new StringBuilder();
        t.append(StavkaAdapter.naziv(this, s)).append('\n');
        t.append(StavkaAdapter.opis(this, s)).append('\n');
        t.append(autoput.oznaka()).append(" · ").append(d.naziv()).append(" · ")
                .append(getString(R.string.km)).append(' ')
                .append(String.format(Locale.getDefault(), "%.1f", s.km)).append('\n');
        if (!s.jeIzlaz()) {
            t.append(getString(R.string.smer_voznje)).append(": ")
                    .append(s.strana == '+' ? d.minus + " → " + d.plus : d.plus + " → " + d.minus).append('\n');
        }
        List<String> tekstovi = new ArrayList<>();
        for (StavkaAdapter.Oznaka o : StavkaAdapter.oznake(this, s)) tekstovi.add(o.tekst);
        if (!tekstovi.isEmpty()) t.append(TextUtils.join(" · ", tekstovi)).append('\n');
        if (s.imaKoordinate()) {
            t.append("https://www.google.com/maps/search/?api=1&query=").append(koordinate()).append('\n');
        }
        t.append("— ").append(getString(R.string.app_name));

        Intent slanje = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, StavkaAdapter.naziv(this, s))
                .putExtra(Intent.EXTRA_TEXT, t.toString());
        startActivity(Intent.createChooser(slanje, getString(R.string.podeli_naslov)));
    }
}
