package com.zeljkobilbija001.beneluxroadinfo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Početni ekran: izbor zemlje (Holandija, Belgija, Luksemburg). */
public class MainActivity extends AppCompatActivity {

    private ImageButton dugmePrivatnosti;
    private MaterialButton voznjaDugme;
    private View voznjaTrazi;
    private TextView voznjaOpis;

    /** Dozvole za režim vožnje: precizna lokacija (obavezno) i obaveštenja (Android 13+). */
    private final ActivityResultLauncher<String[]> dozvoleVoznje = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::posleDozvola);

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
        Obavestenja.otvorena(this, savedInstanceState == null); // i posle potpunog zatvaranja: aktuelne vesti nazad

        voznjaDugme = findViewById(R.id.voznjaDugme);
        voznjaTrazi = findViewById(R.id.voznjaTrazi);
        voznjaOpis = findViewById(R.id.voznjaOpis);
        // proba (samo debug verzija): dodir na karticu "Vožnja" otvara dnevnik
        if (Dnevnik.ukljucen(this)) {
            findViewById(R.id.voznja).setOnClickListener(v ->
                    startActivity(new Intent(this, DnevnikActivity.class)));
        }
        // 🔊/🔇: glasovne poruke u Vožnji (podrazumevano uključene)
        android.widget.TextView glas = this.findViewById(R.id.voznjaGlas);
        if (glas != null) {
            glas.setText(Glas.ukljucen(this) ? "🔊" : "🔇");
            glas.setOnClickListener(v -> {
                boolean da = !Glas.ukljucen(this);
                Glas.postavi(this, da);
                glas.setText(da ? "🔊" : "🔇");
            });
            // dugi pritisak: izbor šta glas javlja
            glas.setOnLongClickListener(v -> {
                Glas.izbor(v.getContext());
                return true;
            });
        }
        voznjaDugme.setOnClickListener(v -> {
            if (VoznjaService.radi) VoznjaService.zaustavi(this);
            else pokreniVoznju();
        });

        // Kupovina „Bez reklama“: dugme dok nije kupljeno; promena stanja (kupovina,
        // vraćanje na novom telefonu) ponovo iscrta ekran, pa baner nestane ili se pojavi
        View dugmeBezReklama = findViewById(R.id.dugmeBezReklama);
        dugmeBezReklama.setVisibility(Kupovina.bezReklama(this) ? View.GONE : View.VISIBLE);
        dugmeBezReklama.setOnClickListener(v -> Kupovina.kupi(this, () -> runOnUiThread(() -> {
            Toast.makeText(this, R.string.kupovina_hvala, Toast.LENGTH_LONG).show();
            recreate();
        })));
        Kupovina.proveri(this, () -> runOnUiThread(this::recreate));
    }

    // ---------- Režim "Vožnja" ----------

    private boolean ima(String dozvola) {
        return ContextCompat.checkSelfPermission(this, dozvola) == PackageManager.PERMISSION_GRANTED;
    }

    private void pokreniVoznju() {
        List<String> traziti = new ArrayList<>();
        if (!ima(Manifest.permission.ACCESS_FINE_LOCATION)) {
            traziti.add(Manifest.permission.ACCESS_FINE_LOCATION);
            traziti.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && !ima(Manifest.permission.POST_NOTIFICATIONS)) {
            traziti.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (traziti.isEmpty()) {
            pokreniAkoLokacija();
        } else {
            dozvoleVoznje.launch(traziti.toArray(new String[0]));
        }
    }

    private void posleDozvola(Map<String, Boolean> rezultat) {
        if (ima(Manifest.permission.ACCESS_FINE_LOCATION)) {
            pokreniAkoLokacija();
        } else {
            // jasan prozor umesto sitne poruke na dnu: bez dozvole Vožnja ne može da radi
            prozorLokacije(R.string.voznja_bez_dozvole_poruka,
                    new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", this.getPackageName(), null)));
        }
    }

    /** Dozvola postoji; Vožnja se pokreće samo ako je lokacija na telefonu uključena. */
    private void pokreniAkoLokacija() {
        android.location.LocationManager lm = this.getSystemService(android.location.LocationManager.class);
        if (lm != null && !androidx.core.location.LocationManagerCompat.isLocationEnabled(lm)) {
            prozorLokacije(R.string.voznja_gps_iskljucen_poruka,
                    new android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }
        VoznjaService.pokreni(this);
        osveziVoznju();
    }

    /** Prozor "Vožnja ne može da se pokrene" sa dugmetom koje otvara podešavanja. */
    private void prozorLokacije(int poruka, android.content.Intent podesavanja) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.voznja_ne_moze_naslov)
                .setMessage(poruka)
                .setPositiveButton(R.string.stanje_podesavanja, (d, w) -> {
                    try {
                        this.startActivity(podesavanja);
                    } catch (Exception ignored) { }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Da li je Vožnja već jednom sama otvorila ekran autoputa (od poslednjeg uključenja). */
    private static boolean voznjaOtvorila;

    private void osveziVoznju() {
        boolean radi = VoznjaService.radi;
        // Vožnja je našla autoput i mesto na njemu: jednom po uključenju sama otvara taj autoput
        // (ekran autoputa ide do reda "Vi ste ovde"); povratak nazad posle toga ne vraća ponovo
        if (!VoznjaService.radi) voznjaOtvorila = false;
        else if (VoznjaService.sadAutoput != null && !voznjaOtvorila && !isFinishing()) {
            voznjaOtvorila = true;
            startActivity(AutoputActivity.namera(this, VoznjaService.sadAutoput));
        }
        voznjaDugme.setText(radi ? R.string.voznja_zaustavi : R.string.voznja_pokreni);
        String opis = VoznjaService.opisStanja;
        String tekst = radi && !opis.isEmpty() ? opis : getString(R.string.voznja_opis);
        if (Dnevnik.ukljucen(this)) tekst += "\nDNEVNIK › (dodir na karticu)";
        voznjaOpis.setText(tekst);
        // kružić dok Vožnja traži autoput ili smer; narandžasto kad je lokacija isključena ili nema interneta
        voznjaTrazi.setVisibility(radi && VoznjaService.trazi ? View.VISIBLE : View.GONE);
        voznjaOpis.setTextColor(radi && VoznjaService.upozorenje ? Izgled.boja(this, R.color.ovde)
                : Izgled.boja(this, R.color.plava_svetla));
    }

    @Override
    protected void onStart() {
        super.onStart();
        VoznjaService.postaviSlusaoca(this, this::osveziVoznju);
        osveziVoznju();
    }

    @Override
    protected void onStop() {
        VoznjaService.skiniSlusaoca(this);
        super.onStop();
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
