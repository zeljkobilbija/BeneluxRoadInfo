package com.zeljkobilbija001.beneluxroadinfo;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Obaveštenja o stanju na putu (zahtevi korisnika 18.11.2026):
 * <ol>
 * <li>Jedan prekidač za sve autoputeve; isključen = ne stiže ništa i u traci nema nijednog obaveštenja.</li>
 * <li>Uključen = obaveštenja samo za praćene autoputeve: izabrani u aplikaciji i onaj na kom je vozač
 *     (Vožnja), nikad iz cele zemlje.</li>
 * <li>Promena autoputa (u Vožnji ili izborom u aplikaciji) ne menja prekidač.</li>
 * <li>Isključivanje uklanja sva prikazana obaveštenja.</li>
 * <li>Uključivanje (i svaka promena praćenih autoputeva) prikazuje aktuelne važne poruke praćenih
 *     autoputeva; obaveštenja autoputeva koji se više ne prate se uklanjaju.</li>
 * </ol>
 * Posle toga provera u pozadini (WorkManager, 15 min) i Vožnja javljaju samo nove poruke.
 */
public final class Obavestenja {

    private static final String PREFS = "obavestenja";
    private static final String PRACENI = "praceni";             // stari spisak (pre 18.11.2026)
    private static final String VIDJENO = "vidjeno_";
    private static final String STIGLO = "stiglo_";
    private static final String UKLJUCENO = "ukljuceno";
    private static final String IZABRANI = "izabrani_autoput";   // poslednji otvoren u aplikaciji
    private static final String POSLEDNJI = "poslednji_autoput"; // poslednji na kom je vozač bio (Vožnja)
    private static final String POSAO = "provera_stanja";
    private static final String KANAL = "stanje_na_putu";
    public static final int MINUTA = 15;

    private static final java.util.concurrent.ScheduledExecutorService NIT =
            Executors.newSingleThreadScheduledExecutor();

    private Obavestenja() { }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Prekidač, zajednički za sve autoputeve. Stari spisak praćenih se prevodi: neprazan = uključeno. */
    public static boolean ukljucena(Context c) {
        SharedPreferences sp = p(c);
        if (!sp.contains(UKLJUCENO)) {
            Set<String> stari = sp.getStringSet(PRACENI, new HashSet<>());
            SharedPreferences.Editor e = sp.edit().putBoolean(UKLJUCENO, !stari.isEmpty()).remove(PRACENI);
            if (!stari.isEmpty() && !sp.contains(IZABRANI)) e.putString(IZABRANI, stari.iterator().next());
            e.apply();
            return !stari.isEmpty();
        }
        return sp.getBoolean(UKLJUCENO, false);
    }

    /** Praćeni autoputevi: izabrani u aplikaciji i onaj na kom je vozač; prazno ako je prekidač isključen. */
    public static Set<String> praceni(Context c) {
        Set<String> s = new HashSet<>();
        if (!ukljucena(c)) return s;
        String izabrani = p(c).getString(IZABRANI, null);
        String poslednji = p(c).getString(POSLEDNJI, null);
        if (StanjeNaPutu.podrzan(izabrani)) s.add(izabrani); // samo gde postoji izvor (NL, Flandrija, LU)
        if (StanjeNaPutu.podrzan(poslednji)) s.add(poslednji); // samo gde postoji izvor (NL, Flandrija, LU)
        return s;
    }

    /** Korisnik je u aplikaciji otvorio autoput: on postaje izabrani (prekidač se ne menja). */
    public static void izabran(Context c, String autoput) {
        if (autoput.equals(p(c).getString(IZABRANI, null))) return;
        p(c).edit().putString(IZABRANI, autoput).apply();
        if (ukljucena(c)) promenjeno(c);
    }

    /** Vožnja javlja autoput na kom je vozač (prekidač se ne menja). */
    public static void naAutoputu(Context c, String autoput) {
        if (autoput.equals(p(c).getString(POSLEDNJI, null))) return;
        p(c).edit().putString(POSLEDNJI, autoput).apply();
        if (ukljucena(c)) promenjeno(c);
    }

    /** Prekidač na ekranu stanja na putu; otvoreni autoput je ujedno izabrani. */
    public static void postavi(Context c, String autoput, boolean ukljuci, List<Dogadjaj> trenutno) {
        ukljucena(c); // prevod starog spiska, ako postoji
        p(c).edit().putBoolean(UKLJUCENO, ukljuci).putString(IZABRANI, autoput).apply();
        if (ukljuci) {
            promenjeno(c);
        } else {
            NotificationManagerCompat nm = NotificationManagerCompat.from(c);
            for (Autoput a : Autoputevi.SVE) nm.cancel(a.id.hashCode()); // stalno obaveštenje Vožnje ostaje
            zakazi(c);
        }
    }

    /** Praćeni autoputevi su se promenili ili je prekidač uključen: provera u pozadini i usklađivanje traka. */
    private static void promenjeno(Context c) {
        zakazi(c);
        Context app = c.getApplicationContext();
        // promenu često javi više mesta zaredom (Vožnja, ekran autoputa koji se sam prebaci, izbor):
        // usklađuje se jednom, 3 s posle poslednje
        synchronized (NIT) {
            if (cekanje != null) cekanje.cancel(false);
            cekanje = NIT.schedule(() -> uskladi(app), 3, TimeUnit.SECONDS);
        }
    }

    private static java.util.concurrent.ScheduledFuture<?> cekanje;

    /**
     * Uklanja obaveštenja autoputeva koji se ne prate i za praćene prikazuje sve aktuelne važne
     * poruke (tiho, bez zvuka), poslednja stigla na vrhu. Sve one se beleže kao viđene.
     */
    private static void uskladi(Context c) {
        Set<String> praceni = praceni(c);
        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        for (Autoput a : Autoputevi.SVE) if (!praceni.contains(a.id)) nm.cancel(a.id.hashCode());
        for (String id : praceni) {
            List<Dogadjaj> trenutno;
            try {
                trenutno = StanjeNaPutu.preuzmi(c, id);
            } catch (Exception e) {
                Dnevnik.upisi(c, "OBAVEŠTENJA " + id + ": aktuelne nisu stigle, " + e.getMessage());
                continue;
            }
            synchronized (Obavestenja.class) {
                if (!ukljucena(c) || !praceni(c).contains(id)) continue; // u međuvremenu promenjeno
                List<Dogadjaj> vazni = new ArrayList<>();
                for (Dogadjaj d : trenutno) if (d.zaObavestenje()) vazni.add(d);
                Map<String, Long> stiglo = stiglo(c, id, trenutno);
                long sad = System.currentTimeMillis();
                int i = 0;
                Set<String> vidjeno = new HashSet<>();
                for (Dogadjaj d : vazni) {
                    vidjeno.add(d.id);
                    if (!stiglo.containsKey(d.id)) stiglo.put(d.id, sad - i++);
                }
                sacuvaj(c, id, vidjeno, stiglo);
                Dnevnik.upisi(c, "OBAVEŠTENJA " + id + ": aktuelnih " + vazni.size());
                if (vazni.isEmpty()) nm.cancel(id.hashCode());
                else prikazi(c, id, poStizanju(vazni, stiglo), null, true);
            }
        }
    }

    /**
     * Aplikacija je skroz zatvorena (sklonjena iz liste otvorenih): uklanjaju se sva obaveštenja o stanju
     * na putu i gasi se provera u pozadini. Prekidač ostaje kakav jeste; pri sledećem otvaranju
     * ({@link #otvorena}) sve se vraća.
     */
    public static void ugasiSve(Context c) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        for (Autoput a : Autoputevi.SVE) nm.cancel(a.id.hashCode());
        WorkManager.getInstance(c.getApplicationContext()).cancelUniqueWork(POSAO);
        Dnevnik.upisi(c, "APLIKACIJA ZATVORENA: obaveštenja uklonjena, provera ugašena");
    }

    /** Da li je aplikacija još među otvorenima (nije sklonjena iz liste nedavnih). */
    public static boolean otvorena(Context c) {
        android.app.ActivityManager am = c.getSystemService(android.app.ActivityManager.class);
        return am == null || !am.getAppTasks().isEmpty();
    }

    /** Aplikacija je ponovo otvorena: provera u pozadini i aktuelne vesti, ako su obaveštenja uključena. */
    public static void otvorena(Context c, boolean pokreni) {
        zakazi(c);
        if (pokreni && ukljucena(c)) promenjeno(c);
    }

    /** Pokreće ili gasi povremenu proveru, zavisno od toga da li se išta prati. */
    public static void zakazi(Context c) {
        WorkManager wm = WorkManager.getInstance(c.getApplicationContext());
        if (praceni(c).isEmpty()) {
            wm.cancelUniqueWork(POSAO);
            return;
        }
        PeriodicWorkRequest zahtev = new PeriodicWorkRequest.Builder(ProveraWorker.class, MINUTA, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        wm.enqueueUniquePeriodicWork(POSAO, ExistingPeriodicWorkPolicy.UPDATE, zahtev);
    }

    public static boolean smeDaObavesti(Context c) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        return NotificationManagerCompat.from(c).areNotificationsEnabled();
    }

    /** Id poruke koja je poslednja stigla u obaveštenju za ovaj autoput, ili null. */
    public static String poslednjeStiglo(Context c, String autoputId) {
        String id = null;
        long max = Long.MIN_VALUE;
        for (Map.Entry<String, Long> e : stiglo(c, autoputId, null).entrySet()) {
            if (e.getValue() > max) { max = e.getValue(); id = e.getKey(); }
        }
        return id;
    }

    /** Trenutak stizanja (id → ms) javljenih poruka; sa {@code trenutno} samo one koje još postoje. */
    private static Map<String, Long> stiglo(Context c, String autoputId, List<Dogadjaj> trenutno) {
        Set<String> postoje = null;
        if (trenutno != null) {
            postoje = new HashSet<>();
            for (Dogadjaj d : trenutno) postoje.add(d.id);
        }
        Map<String, Long> m = new HashMap<>();
        for (String s : p(c).getStringSet(STIGLO + autoputId, new HashSet<>())) {
            int t = s.lastIndexOf('\t');
            if (t <= 0) continue;
            try {
                String id = s.substring(0, t);
                if (postoje == null || postoje.contains(id)) m.put(id, Long.parseLong(s.substring(t + 1)));
            } catch (NumberFormatException ignored) { }
        }
        return m;
    }

    private static void sacuvaj(Context c, String autoputId, Set<String> vidjeno, Map<String, Long> stiglo) {
        Set<String> zapis = new HashSet<>();
        for (Map.Entry<String, Long> e : stiglo.entrySet()) zapis.add(e.getKey() + "\t" + e.getValue());
        p(c).edit().putStringSet(VIDJENO + autoputId, vidjeno).putStringSet(STIGLO + autoputId, zapis).apply();
    }

    /** Poruke koje imaju trenutak stizanja, poslednja stigla prva. */
    private static List<Dogadjaj> poStizanju(List<Dogadjaj> l, Map<String, Long> stiglo) {
        List<Dogadjaj> r = new ArrayList<>();
        for (Dogadjaj d : l) if (stiglo.containsKey(d.id)) r.add(d);
        Collections.sort(r, (a, b) -> Long.compare(stiglo.get(b.id), stiglo.get(a.id)));
        return r;
    }

    /** Poziva ProveraWorker: javlja nove važne poruke sa celog praćenog autoputa. */
    static void obradi(Context c, String autoputId, List<Dogadjaj> trenutno) {
        List<Dogadjaj> vazni = new ArrayList<>();
        for (Dogadjaj d : trenutno) if (d.zaObavestenje()) vazni.add(d);
        obradi(c, autoputId, trenutno, vazni, null);
    }

    /**
     * Javlja poruke iz {@code kandidati} koje još nisu javljene. Spisak viđenih je zajednički za praćenje
     * i režim vožnje (ista poruka se ne javlja dvaput), a čuvaju se samo poruke koje još postoje.
     *
     * @param udaljenosti km do poruke (režim vožnje) ili null
     */
    static synchronized void obradi(Context c, String autoputId, List<Dogadjaj> trenutno,
                                    List<Dogadjaj> kandidati, Map<String, Integer> udaljenosti) {
        if (!praceni(c).contains(autoputId)) {
            Dnevnik.upisi(c, "OBAVEŠTENJA " + autoputId + ": " + (ukljucena(c) ? "ne prati se" : "isključena")
                    + ", ništa se ne javlja");
            return;
        }
        Set<String> vidjeno = new HashSet<>();
        // stare sačuvane oznake (pre stabilnih) svode se na stabilne, da se ništa ne javi ponovo
        for (String s : p(c).getStringSet(VIDJENO + autoputId, new HashSet<>())) vidjeno.add(Dogadjaj.stabilanId(s));
        Set<String> postoje = new HashSet<>();
        for (Dogadjaj d : trenutno) postoje.add(d.id);
        vidjeno.retainAll(postoje);
        List<Dogadjaj> novi = new ArrayList<>();
        for (Dogadjaj d : kandidati) if (vidjeno.add(d.id)) novi.add(d);
        Map<String, Long> stiglo = stiglo(c, autoputId, trenutno);
        long sad = System.currentTimeMillis();
        for (int i = 0; i < novi.size(); i++) stiglo.put(novi.get(i).id, sad - i); // u istoj proveri: po redu liste
        sacuvaj(c, autoputId, vidjeno, stiglo);
        for (Dogadjaj d : novi) Dnevnik.upisi(c, "OBAVEŠTENJE " + autoputId + ": " + red(c, d, udaljenosti));
        if (!novi.isEmpty() && !smeDaObavesti(c)) Dnevnik.upisi(c, "   (obaveštenja nisu dozvoljena – nije prikazano)");
        if (novi.isEmpty()) return;
        // u obaveštenju sve javljene poruke koje još važe, poslednja stigla na vrhu (ne po važnosti)
        prikazi(c, autoputId, poStizanju(kandidati, stiglo), udaljenosti, false);
    }

    @SuppressWarnings("MissingPermission")
    private static void prikazi(Context c, String autoputId, List<Dogadjaj> novi,
                                Map<String, Integer> udaljenosti, boolean tiho) {
        if (!smeDaObavesti(c) || novi.isEmpty()) return;
        napraviKanal(c);
        Autoput a = Autoputevi.poId(autoputId);
        String oznaka = a != null ? a.oznaka() : autoputId;

        Intent otvori = StanjeActivity.namera(c, autoputId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, autoputId.hashCode(), otvori,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.InboxStyle stil = new NotificationCompat.InboxStyle();
        for (Dogadjaj d : novi) stil.addLine(red(c, d, udaljenosti));
        Dogadjaj prvi = novi.get(0);
        String naslov = novi.size() == 1
                ? oznaka + " · " + c.getString(DogadjajAdapter.naziv(prvi.vrsta))
                : c.getString(R.string.obav_naslov_vise, oznaka, novi.size());

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, KANAL)
                .setSmallIcon(R.drawable.ic_obavestenje)
                .setColor(Izgled.boja(c, R.color.plava))
                .setContentTitle(naslov)
                .setContentText(red(c, prvi, udaljenosti))
                .setStyle(stil)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setSilent(tiho)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(c).notify(autoputId.hashCode(), b.build());
    }

    /** "Unfall · za 12 km · Köln → Frankfurt · Idstein – Wiesbadener Kreuz · +11 min" */
    private static String red(Context c, Dogadjaj d, Map<String, Integer> udaljenosti) {
        StringBuilder sb = new StringBuilder(c.getString(DogadjajAdapter.naziv(d.vrsta)));
        Integer km = udaljenosti != null ? udaljenosti.get(d.id) : null;
        if (km != null) sb.append(" · ").append(c.getString(R.string.voznja_za_km, Math.max(0, km)));
        if (!d.smer.isEmpty()) sb.append(" · ").append(d.smer);
        if (!d.deonica.isEmpty()) sb.append(" · ").append(d.deonica);
        if (d.kasnjenjeMin > 0) sb.append(" · ").append(c.getString(R.string.stanje_kasnjenje, d.kasnjenjeMin));
        return sb.toString();
    }

    private static void napraviKanal(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(KANAL) != null) return;
        nm.createNotificationChannel(new NotificationChannel(KANAL,
                c.getString(R.string.obav_kanal), NotificationManager.IMPORTANCE_DEFAULT));
    }
}
