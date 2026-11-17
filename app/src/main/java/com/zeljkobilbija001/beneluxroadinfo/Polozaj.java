package com.zeljkobilbija001.beneluxroadinfo;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Određuje na kom autoputu, deonici i kilometru je neka tačka: autoput se aproksimira linijom
 * kroz izlaze i odmorišta (poređane po kilometru), a tačka se projektuje na najbliži odsečak.
 * Sve radi na telefonu, iz podataka u assets-u.
 */
public final class Polozaj {

    /**
     * Rezultat: autoput, deonica, kilometar i udaljenost od linije autoputa (km).
     * Deonice koje se fizički nastavljaju jedna na drugu imaju istu {@code grupa}, a {@code kmUkupno}
     * teče kroz njih bez prekida (da se vide poruke i iza granice deonice).
     */
    public static final class Tacka {
        public final String autoput;
        public final int deonica, grupa;
        public final double km, kmUkupno, udaljenost;

        Tacka(String autoput, int deonica, int grupa, double km, double kmUkupno, double udaljenost) {
            this.autoput = autoput;
            this.deonica = deonica;
            this.grupa = grupa;
            this.km = km;
            this.kmUkupno = kmUkupno;
            this.udaljenost = udaljenost;
        }
    }

    /** Jedna deonica jednog autoputa kao izlomljena linija. */
    private static final class Linija {
        final String autoput;
        final int deonica;
        int grupa;
        double pomak;   // km od početka grupe do početka ove deonice
        final double[] km, lat, lon;
        /** prekid[i]: između tačke i i i+1 autoput je prekinut (ne spaja se). */
        final boolean[] prekid;
        final double minLat, maxLat, minLon, maxLon;

        Linija(String autoput, int deonica, List<Stavka> tacke, Deonica deo) {
            this.autoput = autoput;
            this.deonica = deonica;
            int n = tacke.size();
            prekid = new boolean[n];
            for (int i = 0; i + 1 < n; i++) prekid[i] = deo.prekidIzmedju(tacke.get(i).km, tacke.get(i + 1).km);
            km = new double[n];
            lat = new double[n];
            lon = new double[n];
            double a = 90, b = -90, c = 180, d = -180;
            for (int i = 0; i < n; i++) {
                Stavka s = tacke.get(i);
                km[i] = s.km;
                lat[i] = s.lat;
                lon[i] = s.lon;
                a = Math.min(a, s.lat);
                b = Math.max(b, s.lat);
                c = Math.min(c, s.lon);
                d = Math.max(d, s.lon);
            }
            // okvir proširen za ~5 km, da se brzo preskoče daleki autoputevi
            minLat = a - 0.05;
            maxLat = b + 0.05;
            minLon = c - 0.08;
            maxLon = d + 0.08;
        }
    }

    private static List<Linija> linije;

    private Polozaj() { }

    private static synchronized List<Linija> linije(Context c) {
        if (linije != null) return linije;
        List<Linija> l = new ArrayList<>();
        for (Autoput a : Autoputevi.SVE) {
            Podaci p = Podaci.za(c, a);
            if (p == null) continue;
            Linija prethodna = null;
            int grupa = 0;
            for (int d = 0; d < p.deonice.size(); d++) {
                List<Stavka> tacke = new ArrayList<>();
                for (Stavka s : p.stavke) if (s.deonica == d && s.imaKoordinate()) tacke.add(s);
                if (tacke.size() < 2) continue;
                Collections.sort(tacke, (x, y) -> Double.compare(x.km, y.km));
                Linija li = new Linija(a.id, d, tacke, p.deonice.get(d));
                // nastavak prethodne deonice ako joj je početak do 5 km od kraja prethodne
                double razmak = prethodna == null ? Double.MAX_VALUE : km(prethodna, prethodna.km.length - 1, li, 0);
                if (razmak <= 5) {
                    li.grupa = prethodna.grupa;
                    li.pomak = prethodna.pomak + prethodna.km[prethodna.km.length - 1] + razmak;
                } else {
                    li.grupa = prethodna == null ? 0 : ++grupa;
                }
                l.add(li);
                prethodna = li;
            }
        }
        linije = l;
        return l;
    }

    /**
     * Smer vožnje iz GPS kursa (stepeni od severa): tačka 300 m napred po kursu se projektuje
     * na isti autoput; ako kilometar raste, smer je '+'. '?' ako se ne može odrediti.
     */
    public static char smerIzKursa(Context c, double lat, double lon, float kurs, String autoput) {
        Tacka sad = nadji(c, lat, lon, autoput);
        double r = Math.toRadians(kurs), d = 0.3;
        double lat2 = lat + d * Math.cos(r) / 110.57;
        double lon2 = lon + d * Math.sin(r) / (111.32 * Math.cos(Math.toRadians(lat)));
        Tacka napred = nadji(c, lat2, lon2, autoput);
        if (sad == null || napred == null || sad.grupa != napred.grupa) return '?';
        double razlika = napred.kmUkupno - sad.kmUkupno;
        if (Math.abs(razlika) < 0.05) return '?';
        return razlika > 0 ? '+' : '-';
    }

    /** Vazdušna razdaljina (km) između tačke i linije a i tačke j linije b. */
    private static double km(Linija a, int i, Linija b, int j) {
        double kx = 111.32 * Math.cos(Math.toRadians(a.lat[i])), ky = 110.57;
        double dx = (a.lon[i] - b.lon[j]) * kx, dy = (a.lat[i] - b.lat[j]) * ky;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Najbliži autoput tački (ili samo zadati autoput, ako {@code samoAutoput} nije null).
     * Vraća null ako u blizini (~5 km) nema nijednog.
     */
    public static Tacka nadji(Context c, double lat, double lon, String samoAutoput) {
        double kx = 111.32 * Math.cos(Math.toRadians(lat)), ky = 110.57;
        Tacka najbolja = null;
        for (Linija li : linije(c)) {
            if (samoAutoput != null && !samoAutoput.equals(li.autoput)) continue;
            if (lat < li.minLat || lat > li.maxLat || lon < li.minLon || lon > li.maxLon) continue;
            for (int i = 0; i + 1 < li.km.length; i++) {
                if (li.prekid[i]) continue;
                // ravan sistem u km, tačka je u koordinatnom početku
                double ax = (li.lon[i] - lon) * kx, ay = (li.lat[i] - lat) * ky;
                double bx = (li.lon[i + 1] - lon) * kx, by = (li.lat[i + 1] - lat) * ky;
                double dx = bx - ax, dy = by - ay, d2 = dx * dx + dy * dy;
                double t = d2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / d2));
                double px = ax + t * dx, py = ay + t * dy;
                double u = Math.sqrt(px * px + py * py);
                if (najbolja == null || u < najbolja.udaljenost) {
                    double km = li.km[i] + t * (li.km[i + 1] - li.km[i]);
                    najbolja = new Tacka(li.autoput, li.deonica, li.grupa, km, li.pomak + km, u);
                }
            }
        }
        return najbolja;
    }
}
