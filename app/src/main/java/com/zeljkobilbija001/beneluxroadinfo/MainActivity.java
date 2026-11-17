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

        voznjaDugme = findViewById(R.id.voznjaDugme);
        voznjaOpis = findViewById(R.id.voznjaOpis);
        // proba (samo debug verzija): dodir na karticu "Vožnja" otvara dnevnik
        if (Dnevnik.ukljucen(this)) {
            findViewById(R.id.voznja).setOnClickListener(v ->
                    startActivity(new Intent(this, DnevnikActivity.class)));
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
            VoznjaService.pokreni(this);
            osveziVoznju();
        } else {
            dozvoleVoznje.launch(traziti.toArray(new String[0]));
        }
    }

    private void posleDozvola(Map<String, Boolean> rezultat) {
        if (ima(Manifest.permission.ACCESS_FINE_LOCATION)) {
            VoznjaService.pokreni(this);
            osveziVoznju();
        } else {
            Snackbar.make(findViewById(R.id.main), R.string.voznja_dozvola, Snackbar.LENGTH_LONG).show();
        }
    }

    private void osveziVoznju() {
        boolean radi = VoznjaService.radi;
        voznjaDugme.setText(radi ? R.string.voznja_zaustavi : R.string.voznja_pokreni);
        String opis = VoznjaService.opisStanja;
        String tekst = radi && !opis.isEmpty() ? opis : getString(R.string.voznja_opis);
        if (Dnevnik.ukljucen(this)) tekst += "\nDNEVNIK › (dodir na karticu)";
        voznjaOpis.setText(tekst);
    }

    @Override
    protected void onStart() {
        super.onStart();
        VoznjaService.postaviSlusaoca(this::osveziVoznju);
        osveziVoznju();
    }

    @Override
    protected void onStop() {
        VoznjaService.postaviSlusaoca(null);
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
