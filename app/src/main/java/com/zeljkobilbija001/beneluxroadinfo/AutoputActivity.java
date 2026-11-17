package com.zeljkobilbija001.beneluxroadinfo;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Ekran jednog autoputa: deonica, smer vožnje, filteri i lista poređana po kilometru. */
public class AutoputActivity extends AppCompatActivity {

    private static final String EXTRA_ID = "autoput";
    private static final String STANJE_DEONICA = "deonica", STANJE_SMER = "smer", STANJE_FILTER = "filter";

    /** Filteri: oznaka u stringovima i uslov. */
    private static final int SVE = 0, PUMPE = 1, HRANA = 2, PARKING = 3, IZLAZI = 4, PUNJACI = 5;
    private static final int[] FILTER_NAZIVI = {R.string.filter_sve, R.string.filter_pumpe,
            R.string.filter_hrana, R.string.filter_parking, R.string.filter_izlazi, R.string.filter_punjaci};

    public static Intent namera(Context c, String id) {
        return new Intent(c, AutoputActivity.class).putExtra(EXTRA_ID, id);
    }

    private Podaci podaci;
    private Autoput autoput;
    private int deonica = 1;
    private char smer = '+';
    private int filter = SVE;

    private StavkaAdapter adapter;
    private LinearLayout deoniceRed, smeroviRed, filteriRed;
    private TextView podnaslov, prazno;
    private RecyclerView lista;
    private View koren;
    private boolean zatvaranje;
    /** Stavke koje su trenutno u listi (za odmotavanje do kilometra). */
    private List<Stavka> prikazano = new ArrayList<>();
    /** Gde je vozač (iz lokacije): deonica, km i smer ('?' = nepoznat); mojaDeonica -1 = nepoznato. */
    private int mojaDeonica = -1;
    private double mojKm;
    private char mojSmer = '?';
    private long pozicijaVreme;
    private static final long ZASTARELO_MS = 5 * 60_000L;
    private final android.os.Handler glavna = new android.os.Handler(android.os.Looper.getMainLooper());
    /**
     * Vožnja je vozača videla na ovom autoputu. Samo tada se pri prelasku na drugi autoput ekran
     * sam prebacuje; ako je otvoren neki drugi autoput (gleda se unapred), ostaje otvoren.
     */
    private boolean vozacBioOvde;
    /** Dok je Vožnja uključena: strelica se sama pomera (svakih 5 s). */
    private final Runnable pracenje = new Runnable() {
        @Override
        public void run() {
            String sadAutoput = VoznjaService.sadAutoput;
            if (autoput.id.equals(sadAutoput)) {
                vozacBioOvde = true;
            } else if (vozacBioOvde && sadAutoput != null && !zatvaranje) {
                // vozač je sa ovog autoputa prešao na drugi: otvara se ekran novog
                Autoput novi = Autoputevi.poId(sadAutoput);
                if (novi != null && novi.dostupan()) {
                    predjiNa(novi);
                    return;
                }
            }
            if (autoput.id.equals(sadAutoput)) {
                mojaDeonica = VoznjaService.sadDeonica;
                mojKm = VoznjaService.sadKm;
                pozicijaVreme = System.currentTimeMillis();
                char sadSmer = VoznjaService.sadSmer;
                if (sadSmer != '?' && sadSmer != mojSmer) {
                    // smer vožnje je tek saznat (ili se vozač okrenuo): lista prikazuje parkinge i pumpe
                    // samo sa njegove strane kolovoza; redosled (dugme smera) ostaje kako ga je vozač izabrao
                    mojSmer = sadSmer;
                    mojaDeonica = VoznjaService.sadDeonica;
                    deonica = mojaDeonica;
                    osvezi(false);
                    int poz = adapter.pozicijaOvde();
                    if (poz > 0) ((LinearLayoutManager) lista.getLayoutManager())
                            .scrollToPositionWithOffset(poz, Izgled.dp(AutoputActivity.this, 24));
                } else {
                    postaviStrelicu();
                }
            } else if (mojaDeonica >= 0 && (VoznjaService.radi
                    || System.currentTimeMillis() - pozicijaVreme > ZASTARELO_MS)) {
                // više nije na ovom autoputu (ili je pozicija stara): strelica se sklanja
                mojaDeonica = -1;
                mojSmer = '?';
                postaviStrelicu();
            }
            glavna.postDelayed(this, 5_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_autoput);

        Autoput a = Autoputevi.poId(getIntent().getStringExtra(EXTRA_ID));
        autoput = a;
        podaci = Podaci.za(this, a);
        if (podaci == null || podaci.deonice.isEmpty()) {
            android.widget.Toast.makeText(this, R.string.greska_podaci, android.widget.Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (savedInstanceState != null) {
            deonica = savedInstanceState.getInt(STANJE_DEONICA, deonica);
            smer = savedInstanceState.getChar(STANJE_SMER, smer);
            filter = savedInstanceState.getInt(STANJE_FILTER, filter);
        }
        if (deonica >= podaci.deonice.size()) deonica = 0;

        koren = findViewById(R.id.koren);
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
        stit.setText(a.oznaka());
        stit.setBackgroundResource(a.stit());
        ((TextView) findViewById(R.id.ruta)).setText(a.ruta);
        podnaslov = findViewById(R.id.podnaslov);
        prazno = findViewById(R.id.prazno);
        deoniceRed = findViewById(R.id.deonice);
        // autoput u jednom komadu: dugmad za deonice samo ako ih ima više
        if (podaci.deonice.size() < 2) ((View) deoniceRed.getParent()).setVisibility(View.GONE);
        smeroviRed = findViewById(R.id.smerovi);
        filteriRed = findViewById(R.id.filteri);

        lista = findViewById(R.id.lista);
        lista.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StavkaAdapter(this::otvoriDetalje);
        lista.setAdapter(adapter);

        findViewById(R.id.nazad).setOnClickListener(v -> zatvori());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                zatvori();
            }
        });

        napraviDeonice();
        napraviFiltere();
        osvezi(true);

        findViewById(R.id.mojaLokacija).setOnClickListener(v -> poLokaciji(true));
        if (savedInstanceState == null) {
            ulaznaAnimacija();
            poLokaciji(false);
        }
        Reklame.prikaziBaner(this, findViewById(R.id.reklamaKontejner));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STANJE_DEONICA, deonica);
        out.putChar(STANJE_SMER, smer);
        out.putInt(STANJE_FILTER, filter);
    }

    // ---------- Gde sam: deonica, smer i kilometar iz lokacije ----------

    /**
     * Ako je vozač na ovom autoputu: bira njegovu deonicu i smer i odmotava listu do njegovog kilometra.
     * Režim vožnje već zna sve; inače se uzima jedna lokacija, a smer iz GPS kursa (dok se vozi).
     *
     * @param pitaj dugme je pritisnuto: javi i kad lokacija nije dostupna ili nije na ovom autoputu
     */
    @android.annotation.SuppressLint("MissingPermission")
    private void poLokaciji(boolean pitaj) {
        if (autoput.id.equals(VoznjaService.sadAutoput)) {
            postaviMesto(VoznjaService.sadDeonica, VoznjaService.sadKm, VoznjaService.sadSmer);
            return;
        }
        boolean ima = androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (!ima) {
            if (pitaj) Toast.makeText(this, R.string.voznja_dozvola, Toast.LENGTH_LONG).show();
            return;
        }
        com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(l -> {
                    // bez lokacije ili netačna (mreža, ±stotine metara): ne postavlja se ništa
                    if (l == null || !l.hasAccuracy() || l.getAccuracy() > 100f) {
                        if (pitaj) Toast.makeText(this, R.string.lokacija_nema, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Context app = getApplicationContext();
                    String id = autoput.id;
                    new Thread(() -> {
                        Polozaj.Tacka t = Polozaj.nadji(app, l.getLatitude(), l.getLongitude(), id);
                        char s = '?';
                        if (t != null && l.hasBearing() && l.hasSpeed() && l.getSpeed() > 3f) {
                            s = Polozaj.smerIzKursa(app, l.getLatitude(), l.getLongitude(), l.getBearing(), id);
                        }
                        char smerKonacno = s;
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            Geometrija.Rezultat g = Geometrija.najblizi(app, l.getLatitude(), l.getLongitude(), id);
                            boolean naKolovozu = g != null && g.udaljenost <= 0.1 + Math.min(l.getAccuracy(), 150f) / 1000.0;
                            if (t == null || !naKolovozu) {
                                if (pitaj) Toast.makeText(this, getString(R.string.lokacija_nije_na,
                                        autoput.oznaka()), Toast.LENGTH_SHORT).show();
                                return;
                            }
                            postaviMesto(t.deonica, t.km, smerKonacno);
                        });
                    }).start();
                })
                .addOnFailureListener(e -> {
                    if (pitaj) Toast.makeText(this, R.string.lokacija_nema, Toast.LENGTH_SHORT).show();
                });
    }

    private void postaviMesto(int novaDeonica, double km, char noviSmer) {
        if (novaDeonica < 0 || novaDeonica >= podaci.deonice.size()) return;
        deonica = novaDeonica;
        mojaDeonica = novaDeonica;
        pozicijaVreme = System.currentTimeMillis();
        mojKm = km;
        if (noviSmer == '+' || noviSmer == '-') {
            smer = noviSmer;
            mojSmer = noviSmer;
        }
        filter = SVE;
        osvezi(true);
        int poz = adapter.pozicijaOvde();
        if (poz > 0) {
            ((LinearLayoutManager) lista.getLayoutManager())
                    .scrollToPositionWithOffset(poz, Izgled.dp(this, 24));
        }

        Deonica d = podaci.deonice.get(deonica);
        String kmTekst = String.format(Locale.getDefault(), "%.1f", d.kmZaPrikaz(km));
        String poruka = noviSmer == '+' || noviSmer == '-'
                ? getString(R.string.lokacija_ovde_smer, kmTekst, smer == '+' ? d.plus : d.minus)
                : getString(R.string.lokacija_ovde, kmTekst);
        Toast.makeText(this, poruka, Toast.LENGTH_LONG).show();
    }

    /**
     * Red "Vi ste ovde" sa strelicom smera: samo kad je izabrana deonica vozača i njegov smer
     * (ili smer još nije poznat), između mesta iza njega i prvog mesta ispred.
     */
    private void postaviStrelicu() {
        if (mojaDeonica != deonica || prikazano.isEmpty()) {
            if (adapter.pozicijaOvde() >= 0) adapter.postaviOvde(-1, 0, "", false, false);
            return;
        }
        int i = 0;
        while (i < prikazano.size() && (smer == '+' ? prikazano.get(i).km < mojKm : prikazano.get(i).km > mojKm)) i++;
        Deonica d = podaci.deonice.get(deonica);
        // tekst uvek kazuje kuda vozač stvarno ide; u suprotnom smeru liste strelica gleda nagore
        String tekst = mojSmer == '?' ? getString(R.string.lokacija_vi_ste_ovde)
                : getString(R.string.lokacija_vi_ste_ovde) + " · " + (mojSmer == '+' ? d.plus : d.minus);
        adapter.postaviOvde(i, d.kmZaPrikaz(mojKm), tekst, mojSmer != '?', mojSmer != '?' && mojSmer != smer);
    }

    @Override
    protected void onResume() {
        super.onResume();
        glavna.postDelayed(pracenje, 5_000);
    }

    @Override
    protected void onPause() {
        glavna.removeCallbacks(pracenje);
        super.onPause();
    }

    // ---------- Animacija okretanja ----------

    private void ulaznaAnimacija() {
        koren.setCameraDistance(12000 * getResources().getDisplayMetrics().density);
        koren.setRotationY(-90f);
        koren.setAlpha(0.4f);
        koren.animate()
                .rotationY(0f)
                .alpha(1f)
                .setDuration(420)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /** Vožnja: vozač je prešao na drugi autoput, pa se ovaj ekran okreće i otvara ekran novog. */
    @SuppressWarnings("deprecation")
    private void predjiNa(Autoput novi) {
        zatvaranje = true;
        koren.setCameraDistance(12000 * getResources().getDisplayMetrics().density);
        koren.animate()
                .rotationY(90f)
                .alpha(0.4f)
                .setDuration(240)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    startActivity(namera(this, novi.id));
                    finish();
                    overridePendingTransition(0, 0);
                })
                .start();
    }

    @SuppressWarnings("deprecation")
    private void zatvori() {
        if (zatvaranje) return;
        zatvaranje = true;
        koren.setCameraDistance(12000 * getResources().getDisplayMetrics().density);
        koren.animate()
                .rotationY(90f)
                .alpha(0.4f)
                .setDuration(240)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    finish();
                    overridePendingTransition(0, 0);
                })
                .start();
    }

    // ---------- Dugmad ----------

    private MaterialButton pilula(String tekst) {
        MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle);
        b.setText(tekst);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setLetterSpacing(0f);
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setMinHeight(Izgled.dp(this, 40));
        b.setMinimumHeight(Izgled.dp(this, 40));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(Izgled.dp(this, 14), 0, Izgled.dp(this, 14), 0);
        b.setCornerRadius(Izgled.dp(this, 20));
        b.setElevation(0f);
        b.setStateListAnimator(null);
        return b;
    }

    private void napraviDeonice() {
        deoniceRed.removeAllViews();
        for (int i = 0; i < podaci.deonice.size(); i++) {
            final int idx = i;
            MaterialButton b = pilula(podaci.deonice.get(i).naziv());
            b.setOnClickListener(v -> {
                if (deonica == idx) return;
                deonica = idx;
                osvezi(true);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.setMarginStart(Izgled.dp(this, 8));
            deoniceRed.addView(b, lp);
        }
        // Jedno dugme: piše "odakle → kuda", dodir okreće smer
        smeroviRed.removeAllViews();
        MaterialButton b = pilula("");
        b.setTextSize(15);
        b.setCornerRadius(Izgled.dp(this, 10));
        // dugi nazivi idu u dva reda
        b.setMaxLines(2);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setLineSpacing(0f, 0.95f);
        b.setPadding(Izgled.dp(this, 14), Izgled.dp(this, 8), Izgled.dp(this, 12), Izgled.dp(this, 8));
        b.setIconResource(R.drawable.ic_zameni_smer);
        b.setIconGravity(MaterialButton.ICON_GRAVITY_END);
        b.setIconPadding(Izgled.dp(this, 10));
        b.setOnClickListener(v -> {
            smer = smer == '+' ? '-' : '+';
            osvezi(true);
            // u vožnji: posle okretanja liste odmah na red "Vi ste ovde"
            int poz = adapter.pozicijaOvde();
            if (poz > 0) ((LinearLayoutManager) lista.getLayoutManager())
                    .scrollToPositionWithOffset(poz, Izgled.dp(this, 24));
        });
        smeroviRed.addView(b, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void napraviFiltere() {
        filteriRed.removeAllViews();
        for (int i = 0; i < FILTER_NAZIVI.length; i++) {
            final int f = i;
            MaterialButton b = pilula(getString(FILTER_NAZIVI[i]));
            b.setMinHeight(Izgled.dp(this, 36));
            b.setMinimumHeight(Izgled.dp(this, 36));
            b.setCornerRadius(Izgled.dp(this, 18));
            b.setStrokeWidth(Izgled.dp(this, 1.5f));
            b.setOnClickListener(v -> {
                if (filter == f) return;
                filter = f;
                osvezi(true);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.setMarginStart(Izgled.dp(this, 8));
            filteriRed.addView(b, lp);
        }
    }

    /** Boje dugmadi prema izboru, pa nova lista. */
    private void osvezi(boolean naVrh) {
        int bela = 0xFFFFFFFF;
        int plava = Izgled.boja(this, R.color.plava);
        int plavaTamna = Izgled.boja(this, R.color.plava_tamna);
        int providna = 0x24FFFFFF;

        for (int i = 0; i < deoniceRed.getChildCount(); i++) {
            MaterialButton b = (MaterialButton) deoniceRed.getChildAt(i);
            boolean on = i == deonica;
            b.setBackgroundTintList(Izgled.jedna(on ? bela : providna));
            b.setTextColor(on ? plavaTamna : bela);
            b.setSelected(on);
        }

        Deonica d = podaci.deonice.get(deonica);
        MaterialButton smerDugme = (MaterialButton) smeroviRed.getChildAt(0);
        // '+' = od početka deonice ka kraju (minus → plus), '-' = obrnuto; tvrdi razmak drži strelicu uz naziv
        String odakle = smer == '+' ? d.minus : d.plus, kuda = smer == '+' ? d.plus : d.minus;
        smerDugme.setText(odakle + "\u00A0→ " + kuda);
        smerDugme.setContentDescription(getString(R.string.smer_voznje) + ": " + odakle + " → " + kuda);
        smerDugme.setBackgroundTintList(Izgled.jedna(bela));
        smerDugme.setTextColor(plavaTamna);
        smerDugme.setIconTint(Izgled.jedna(plavaTamna));

        int povrsina = Izgled.boja(this, R.color.povrsina);
        int ivica = Izgled.boja(this, R.color.ivica_filtera);
        int tekst = Izgled.boja(this, R.color.cip_obicno_tekst);
        for (int i = 0; i < filteriRed.getChildCount(); i++) {
            MaterialButton b = (MaterialButton) filteriRed.getChildAt(i);
            boolean on = i == filter;
            b.setBackgroundTintList(Izgled.jedna(on ? plava : povrsina));
            b.setStrokeColor(Izgled.jedna(on ? plava : ivica));
            b.setTextColor(on ? bela : tekst);
            b.setSelected(on);
        }

        // Lista: samo tačke ove deonice sa strane kolovoza kojom se vozi. Kad se zna stvarni smer vožnje,
        // strana je uvek njegova, a dugme smera menja samo redosled (strelica nagore ili nadole).
        char strana = mojaDeonica == deonica && (mojSmer == '+' || mojSmer == '-') ? mojSmer : smer;
        List<Stavka> rezultat = new ArrayList<>();
        int izlaza = 0, pumpi = 0;
        for (Stavka s : podaci.stavke) {
            if (s.deonica != deonica) continue;
            if (s.jeIzlaz()) izlaza++;
            if (!s.gorivo.isEmpty() && s.strana == strana) pumpi++;
            if (s.uSmeru(strana) && prolazi(s)) rezultat.add(s);
        }
        Collections.sort(rezultat, (x, y) -> smer == '+' ? Double.compare(x.km, y.km) : Double.compare(y.km, x.km));

        podnaslov.setText(getString(R.string.podnaslov_lista, izlaza, pumpi));
        adapter.postavi(rezultat);
        prikazano = rezultat;
        postaviStrelicu();
        prazno.setVisibility(rezultat.isEmpty() ? View.VISIBLE : View.GONE);
        if (naVrh) lista.scrollToPosition(0);
    }

    /** Dodir na red: ekran sa svim podacima o tom mestu. */
    private void otvoriDetalje(Stavka st) {
        int i = podaci.stavke.indexOf(st);
        if (i >= 0) startActivity(DetaljiActivity.namera(this, autoput.id, i));
    }

    private boolean prolazi(Stavka s) {
        switch (filter) {
            case PUMPE: return !s.gorivo.isEmpty();
            case HRANA: return s.imaHranu();
            case PARKING: return s.tip == Stavka.PARKING;
            case IZLAZI: return s.jeIzlaz();
            case PUNJACI: return !s.punjaci.isEmpty();
            default: return true;
        }
    }
}
