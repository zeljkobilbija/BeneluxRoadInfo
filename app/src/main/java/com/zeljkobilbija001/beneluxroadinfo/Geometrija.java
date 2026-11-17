package com.zeljkobilbija001.beneluxroadinfo;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tačna linija kolovoza autoputeva (assets/geometrija.bin, alat alati/geometrija.js): služi samo da se zna
 * da li je tačka NA autoputu (desetine metara), dok kilometar daje {@link Polozaj}.
 */
public final class Geometrija {

    /** Najbliži autoput tački i udaljenost od njegovog kolovoza (km). */
    public static final class Rezultat {
        public final String autoput;
        public final double udaljenost;

        Rezultat(String autoput, double udaljenost) {
            this.autoput = autoput;
            this.udaljenost = udaljenost;
        }
    }

    private static final double CELIJA = 0.01; // ~1,1 km po geografskoj širini
    private static String[] oznake;
    /** Segmenti po ćeliji: niz [autoput, x1, y1, x2, y2] (stepeni·1e5). */
    private static Map<Long, List<int[]>> mreza;

    private Geometrija() { }

    private static long kljuc(int i, int j) {
        return ((long) i << 32) ^ (j & 0xffffffffL);
    }

    private static synchronized void ucitaj(Context c) {
        if (mreza != null) return;
        Map<Long, List<int[]>> m = new HashMap<>();
        List<String> o = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(c.getAssets().open("geometrija.bin"), 1 << 16))) {
            int na = in.readInt();
            for (int a = 0; a < na; a++) {
                o.add(in.readUTF());
                int np = in.readInt();
                for (int p = 0; p < np; p++) {
                    int n = in.readInt();
                    int pla = 0, plo = 0;
                    for (int i = 0; i < n; i++) {
                        int la = in.readInt(), lo = in.readInt();
                        if (i > 0) {
                            int[] s = {a, pla, plo, la, lo};
                            // segment se upisuje u sve ćelije koje njegov okvir dodiruje
                            int i0 = (int) Math.floor(Math.min(pla, la) / 1e5 / CELIJA), i1 = (int) Math.floor(Math.max(pla, la) / 1e5 / CELIJA);
                            int j0 = (int) Math.floor(Math.min(plo, lo) / 1e5 / CELIJA), j1 = (int) Math.floor(Math.max(plo, lo) / 1e5 / CELIJA);
                            for (int ci = i0; ci <= i1; ci++) {
                                for (int cj = j0; cj <= j1; cj++) {
                                    long k = kljuc(ci, cj);
                                    List<int[]> l = m.get(k);
                                    if (l == null) m.put(k, l = new ArrayList<>(4));
                                    l.add(s);
                                }
                            }
                        }
                        pla = la;
                        plo = lo;
                    }
                }
            }
        } catch (Exception e) {
            // bez geometrije: provera "na autoputu" se oslanja samo na Polozaj
        }
        oznake = o.toArray(new String[0]);
        mreza = m;
    }

    public static boolean dostupna(Context c) {
        ucitaj(c);
        return !mreza.isEmpty();
    }

    /**
     * Najbliži kolovoz u krugu ~1 km (samo zadati autoput ako {@code samo} nije null);
     * null ako ga nema.
     */
    public static Rezultat najblizi(Context c, double lat, double lon, String samo) {
        ucitaj(c);
        int ci = (int) Math.floor(lat / CELIJA), cj = (int) Math.floor(lon / CELIJA);
        double kx = 111.32 * Math.cos(Math.toRadians(lat)), ky = 110.57;
        double naj = Double.MAX_VALUE;
        int najA = -1;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                List<int[]> l = mreza.get(kljuc(ci + di, cj + dj));
                if (l == null) continue;
                for (int[] s : l) {
                    if (samo != null && !samo.equals(oznake[s[0]])) continue;
                    double ax = (s[2] / 1e5 - lon) * kx, ay = (s[1] / 1e5 - lat) * ky;
                    double bx = (s[4] / 1e5 - lon) * kx, by = (s[3] / 1e5 - lat) * ky;
                    double dx = bx - ax, dy = by - ay, d2 = dx * dx + dy * dy;
                    double t = d2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / d2));
                    double px = ax + t * dx, py = ay + t * dy;
                    double u = Math.sqrt(px * px + py * py);
                    if (u < naj) {
                        naj = u;
                        najA = s[0];
                    }
                }
            }
        }
        return najA < 0 ? null : new Rezultat(oznake[najA], naj);
    }
}
