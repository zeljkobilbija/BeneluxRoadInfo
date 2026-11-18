package com.zeljkobilbija001.beneluxroadinfo;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Režim „Vožnja“: korisnik ga sam uključi; dok traje, u traci stoji stalno obaveštenje.
 * Iz lokacije se određuje autostrada, kilometar i smer (za red „Vi ste ovde“ i ekran autostrade).
 * Za Beneluks zasad nema izvora podataka o saobraćaju, pa nema ni poruka o udesima i kolonama.
 * Lokacija ostaje na telefonu.
 */
public class VoznjaService extends Service {

    private static final String AKCIJA_STOP = "stop";
    private static final String KANAL = "voznja";
    private static final int ID_OBAVESTENJA = 7001;
    private static final long BEZ_AUTOPUTA_MS = 45 * 60_000L;   // toliko van autoputa → samo se gasi
    private static final long NAJDUZE_MS = 12 * 60 * 60_000L;
    private static final float MIN_BRZINA = 8f; // m/s (~30 km/h)
    /** Sporije od MIN_BRZINA (kolona, stajanje): autoput se prepoznaje samo ovoliko blizu kolovoza (km). */
    private static final double SPORO_KM = 0.03;
    /** Smer vožnje se zaključuje (i okretanje prepoznaje) posle ovoliko pređenih km po autoputu (ranije 0,5). */
    private static final double SMER_KM = 0.2;
    private int kandidatPotvrda;
    /** Do koliko km od kolovoza je vozač "na autoputu" (plus tačnost GPS-a). */
    private static final double NA_KOLOVOZU_KM = 0.06;
    /** Lokacije lošije od ovoga (m) se ne koriste. */
    private static final float MAX_NETACNOST_M = 100f;

    /** Stanje za početni ekran. */
    public static volatile boolean radi;
    public static volatile String opisStanja = "";
    private static Runnable slusalac;
    /** Gde je vozač sada (za ekran autoputa); null autoput = nepoznato. */
    public static volatile String sadAutoput;
    public static volatile int sadDeonica;
    public static volatile double sadKm;
    public static volatile char sadSmer = '?';
    /** Poslednji red o lokaciji (za ekran Dnevnik). */
    public static volatile String uzivo = "";
    private static VoznjaService instanca;

    public static void pokreni(Context c) {
        ContextCompat.startForegroundService(c, new Intent(c, VoznjaService.class));
    }

    public static void zaustavi(Context c) {
        c.stopService(new Intent(c, VoznjaService.class));
    }

    private static Object vlasnikSlusaoca;

    /**
     * Ekran se prijavljuje da osveži prikaz kad se stanje promeni (poziva se na glavnoj niti). Svaki ekran
     * posle skida samo svoje slušanje: ranije je početni ekran pri zaustavljanju brisao slušanje ekrana
     * zemlje koji je upravo otvoren, pa taj nije saznao autoput dok se ne otvori ponovo.
     */
    public static void postaviSlusaoca(Object vlasnik, Runnable r) {
        vlasnikSlusaoca = vlasnik;
        slusalac = r;
    }

    public static void skiniSlusaoca(Object vlasnik) {
        if (vlasnikSlusaoca != vlasnik) return;
        vlasnikSlusaoca = null;
        slusalac = null;
    }

    private final Handler glavna = new Handler(Looper.getMainLooper());
    private final ExecutorService nit = Executors.newSingleThreadExecutor();
    private FusedLocationProviderClient lokacije;
    private final LocationCallback povratni = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult r) {
            Location l = r.getLastLocation();
            if (l != null) nit.execute(() -> obradiLokaciju(l));
        }
    };

    // stanje (menja se samo na niti "nit")
    private String autoput;          // trenutni autoput ili null
    private int deonica, grupa;
    private double km, kmUkupno, kmZaSmer = Double.NaN;
    private char smer = '?';
    private String kandidat;         // autoput koji se pojavio jednom, čeka potvrdu
    private Location prethodna;
    private double udaljenostOdTrenutnog = Double.NaN;
    private int vanKolovoza;
    private long pocetak, poslednjiPutNaAutoputu;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && AKCIJA_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (radi) return START_NOT_STICKY;

        napraviKanal();
        try {
            ServiceCompat.startForeground(this, ID_OBAVESTENJA, obavestenje(getString(R.string.voznja_trazim)),
                    Build.VERSION.SDK_INT >= 29 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0);
        } catch (Exception e) {
            // nema dozvole za lokaciju ili sistem ne dozvoljava pokretanje
            stopSelf();
            return START_NOT_STICKY;
        }
        radi = true;
        instanca = this;
        Dnevnik.upisi(this, "START režim vožnje");
        pocetak = poslednjiPutNaAutoputu = System.currentTimeMillis();
        javiStanje(getString(R.string.voznja_trazim));

        lokacije = LocationServices.getFusedLocationProviderClient(this);
        LocationRequest zahtev = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15_000L)
                .setMinUpdateIntervalMillis(10_000L)
                .setMinUpdateDistanceMeters(0f)
                .build();
        try {
            lokacije.requestLocationUpdates(zahtev, povratni, Looper.getMainLooper());
        } catch (SecurityException e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    /** Aplikacija je sklonjena iz liste otvorenih (skroz zatvorena): gasi se Vožnja i sva obaveštenja. */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Dnevnik.upisi(this, "APLIKACIJA ZATVORENA: Vožnja se gasi");
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        glavna.removeCallbacksAndMessages(null);
        if (lokacije != null) lokacije.removeLocationUpdates(povratni);
        nit.shutdownNow();
        radi = false;
        instanca = null;
        sadAutoput = null;
        opisStanja = "";
        uzivo = "";
        Dnevnik.upisi(this, "STOP režim vožnje");
        Runnable r = slusalac;
        if (r != null) r.run();
        super.onDestroy();
    }

    // ---------- Lokacija → autoput, kilometar, smer ----------

    private void obradiLokaciju(Location l) {
        long sad = System.currentTimeMillis();
        if (sad - pocetak > NAJDUZE_MS) {
            Dnevnik.upisi(this, "STOP: 12 sati");
            glavna.post(this::stopSelf);
            return;
        }
        // netačna lokacija (mreža umesto GPS-a, ±300–900 m, često sa izmišljenom brzinom) ne menja ništa:
        // ranije je okretala smer i držala vozača "na autoputu" posle izlaska
        if (!l.hasAccuracy() || l.getAccuracy() > MAX_NETACNOST_M) {
            Dnevnik.upisi(this, String.format(Locale.US, "LOK %.5f,%.5f ±%.0fm – preskočeno (netačno)",
                    l.getLatitude(), l.getLongitude(), l.getAccuracy()));
            return;
        }
        String stari = autoput;
        char stariSmer = smer;
        float v = brzina(l);

        // Na autoputu = do ~100 m od kolovoza (tačna linija iz Geometrija), uz tačnost GPS-a.
        // Kilometar daje Polozaj (linija kroz izlaze), a ovde se samo odlučuje NA kom je autoputu.
        double lat = l.getLatitude(), lon = l.getLongitude();
        double tol = NA_KOLOVOZU_KM + Math.min(l.getAccuracy(), 150f) / 1000.0;
        Geometrija.Rezultat g = Geometrija.najblizi(this, lat, lon, null);
        Polozaj.Tacka t = null;
        boolean naTrenutnom = false;
        if (autoput != null) {
            Geometrija.Rezultat gt = Geometrija.najblizi(this, lat, lon, autoput);
            udaljenostOdTrenutnog = gt != null ? gt.udaljenost : Double.NaN;
            naTrenutnom = gt != null && gt.udaljenost <= tol + 0.05;
            // čvorište: drugi autoput je jasno bliži, a stari se udaljava
            if (naTrenutnom && g != null && !g.autoput.equals(autoput) && g.udaljenost <= tol
                    && gt.udaljenost - g.udaljenost >= 0.05 && v >= MIN_BRZINA) naTrenutnom = false;
        }
        if (naTrenutnom) {
            vanKolovoza = 0;
            t = Polozaj.nadji(this, lat, lon, autoput);
        } else if (autoput != null && ++vanKolovoza < 2) {
            // jedna lokacija van kolovoza se toleriše (skok GPS-a, tunel)
            t = Polozaj.nadji(this, lat, lon, autoput);
        } else {
            // nije (više) na trenutnom: novi autoput tek posle dve uzastopne lokacije, i samo u vožnji
            // u koloni ili pri stajanju (Vožnja uključena dok vozač stoji): i bez brzine, ali samo
            // sasvim blizu kolovoza (do 30 m) i tri lokacije zaredom, da parking pored puta ne zavara
            boolean sporo = g != null && v < MIN_BRZINA && g.udaljenost <= SPORO_KM;
            if (g != null && g.udaljenost <= tol && (v >= MIN_BRZINA || sporo)) {
                if (!g.autoput.equals(kandidat)) {
                    kandidat = g.autoput;
                    kandidatPotvrda = 1;
                } else if (++kandidatPotvrda >= (sporo ? 3 : 2)) {
                    t = Polozaj.nadji(this, lat, lon, g.autoput);
                }
            } else {
                kandidat = null;
            }
            if (t == null && autoput != null) {
                Dnevnik.upisi(this, "NAPUSTIO " + autoput);
                autoput = null;
                smer = '?';
                kmZaSmer = Double.NaN;
                vanKolovoza = 0;
            }
        }

        if (t != null) {
            kandidat = null;
            poslednjiPutNaAutoputu = sad;
            // smer se pamti i pri prelasku na sledeću deonicu iste grupe (km teče dalje)
            if (!t.autoput.equals(autoput) || t.grupa != grupa) {
                autoput = t.autoput;
                grupa = t.grupa;
                smer = '?';
                kmZaSmer = t.kmUkupno;
            } else if (Double.isNaN(kmZaSmer)) {
                kmZaSmer = t.kmUkupno;
            } else if (Math.abs(t.kmUkupno - kmZaSmer) >= SMER_KM) {
                smer = t.kmUkupno > kmZaSmer ? '+' : '-';
                kmZaSmer = t.kmUkupno;
            }
            deonica = t.deonica;
            km = t.km;
            kmUkupno = t.kmUkupno;
            sadDeonica = deonica;
            sadKm = km;
            sadSmer = smer;
            sadAutoput = autoput;
        } else {
            sadAutoput = null;
        }
        if (t == null && sad - poslednjiPutNaAutoputu > BEZ_AUTOPUTA_MS) {
            Dnevnik.upisi(this, "STOP: 45 min van autoputa");
            glavna.post(this::stopSelf);
            return;
        }

        if (Dnevnik.ukljucen(this)) {
            String najblizi = g == null ? "nema kolovoza u krugu 1 km"
                    : String.format(Locale.US, "kolovoz %s na %.0f m (granica %.0f m)", g.autoput, g.udaljenost * 1000, tol * 1000);
            String kuda = kuda();
            String trenutno = autoput == null ? (kandidat != null ? "kandidat " + kandidat + " (čeka potvrdu)" : "van autoputa")
                    : String.format(Locale.US, "NA %s km %.1f [tabla %s] (%.2f km od kolovoza) smer %s%s", autoput, km,
                    kmTablaTekst(), udaljenostOdTrenutnog, smer, kuda != null ? " → " + kuda : "");
            String red = String.format(Locale.US, "LOK %.5f,%.5f ±%.0fm %.0f km/h | %s | %s",
                    l.getLatitude(), l.getLongitude(), l.getAccuracy(), v * 3.6f, najblizi, trenutno);
            Dnevnik.upisi(this, red);
            uzivo = red;
        }

        osveziObavestenje();
    }

    /** Brzina u m/s: iz GPS-a, a ako je nema, iz razdaljine od prethodne lokacije. */
    private float brzina(Location l) {
        Location pre = prethodna;
        prethodna = l;
        if (l.hasSpeed() && l.getSpeed() > 0) return l.getSpeed();
        if (pre == null) return 0;
        long ms = l.getTime() - pre.getTime();
        return ms > 0 ? l.distanceTo(pre) * 1000f / ms : 0;
    }

    private void osveziObavestenje() {
        String tekst;
        if (autoput == null) {
            tekst = getString(kandidat != null ? R.string.voznja_trazim : R.string.voznja_van);
        } else {
            Autoput a = Autoputevi.poId(autoput);
            String oznaka = a != null ? a.oznaka() : autoput;
            String kuda = kuda();
            tekst = kuda == null ? getString(R.string.voznja_na, oznaka)
                    : getString(R.string.voznja_na_smer, oznaka, kuda);
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(ID_OBAVESTENJA, obavestenje(tekst));
        javiStanje(tekst);
    }

    /** Kilometar sa tabli na trenutnoj poziciji (za dnevnik), ili "?". */
    private String kmTablaTekst() {
        Podaci p = autoput != null ? Podaci.za(this, Autoputevi.poId(autoput)) : null;
        if (p == null || deonica >= p.deonice.size()) return "?";
        double z = p.deonice.get(deonica).kmTabla(km);
        return Double.isNaN(z) ? "?" : String.format(Locale.US, "%.1f", z);
    }

    /** Naziv mesta prema kome se vozi, iz podataka o deonici. */
    private String kuda() {
        if (autoput == null || smer == '?') return null;
        Podaci p = Podaci.za(this, Autoputevi.poId(autoput));
        if (p == null || deonica >= p.deonice.size()) return null;
        Deonica d = p.deonice.get(deonica);
        return smer == '+' ? d.plus : d.minus;
    }

    private void javiStanje(String tekst) {
        opisStanja = tekst;
        glavna.post(() -> {
            Runnable r = slusalac;
            if (r != null) r.run();
        });
    }

    // ---------- Stalno obaveštenje ----------

    private Notification obavestenje(String tekst) {
        Intent otvori = autoput != null ? AutoputActivity.namera(this, autoput) : new Intent(this, MainActivity.class);
        otvori.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 1, otvori,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 2,
                new Intent(this, VoznjaService.class).setAction(AKCIJA_STOP), PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, KANAL)
                .setSmallIcon(R.drawable.ic_obavestenje)
                .setColor(Izgled.boja(this, R.color.plava))
                .setContentTitle(getString(R.string.voznja_naslov))
                .setContentText(tekst)
                .setContentIntent(pi)
                .addAction(0, getString(R.string.voznja_zaustavi), stop)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    private void napraviKanal() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(KANAL) != null) return;
        nm.createNotificationChannel(new NotificationChannel(KANAL,
                getString(R.string.voznja_kanal), NotificationManager.IMPORTANCE_LOW));
    }
}
