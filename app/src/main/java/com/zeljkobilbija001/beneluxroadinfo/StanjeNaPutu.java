package com.zeljkobilbija001.beneluxroadinfo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Stanje na putu za jedan autoput Beneluksa: udesi, kolone, zatvaranja i radovi preko našeg posrednika (/bnl):
 * NL – NDW (CC0), BE – Verkeerscentrum Vlaanderen (CC BY, Flandrija; Valonija zasad nema otvoren izvor),
 * LU – CITA (CC0). Posrednik vesti već smešta na naše autoputeve ("NL-A2") sa koordinatama početka i kraja.
 */
public final class StanjeNaPutu {

    public interface Odgovor {
        /** Poziva se na glavnoj niti; null ako podaci nisu stigli. */
        void stiglo(List<Dogadjaj> dogadjaji);
    }

    private static final String BNL = "https://evis-posrednik.zeljkobilbija.workers.dev/bnl?put=";
    private static final ExecutorService NIT = Executors.newSingleThreadExecutor();

    private StanjeNaPutu() { }

    /** Izvor postoji za sve autoputeve Holandije, Flandrije (Belgija) i Luksemburga. */
    public static boolean podrzan(String autoputId) {
        return autoputId != null && (autoputId.startsWith("NL-") || autoputId.startsWith("BE-") || autoputId.startsWith("LU-"));
    }

    public static void ucitaj(Context c, String autoput, Odgovor odgovor) {
        Context app = c.getApplicationContext();
        Handler glavna = new Handler(Looper.getMainLooper());
        NIT.execute(() -> {
            List<Dogadjaj> l;
            try {
                l = preuzmi(app, autoput);
            } catch (Exception e) {
                l = null;
            }
            List<Dogadjaj> konacno = l;
            glavna.post(() -> odgovor.stiglo(konacno));
        });
    }

    /**
     * Preuzimanje na tekućoj niti (i za proveru u pozadini). Najnovija poruka ide prva bez obzira
     * na važnost (važnost pokazuje boja), poruke bez vremena na kraj; isto vreme: važnija prva.
     */
    public static List<Dogadjaj> preuzmi(Context c, String autoput) throws Exception {
        if (!podrzan(autoput)) return new ArrayList<>();
        Autoput ap = Autoputevi.poId(autoput);
        Podaci p = c == null || ap == null ? null : Podaci.za(c, ap);
        List<Dogadjaj> l = new ArrayList<>(Dogadjaj.izCh(get(BNL + autoput), p));
        Collections.sort(l, (a, b) -> a.pocetak != b.pocetak ? Long.compare(b.pocetak, a.pocetak)
                : Integer.compare(a.vrsta, b.vrsta));
        return l;
    }

    private static String get(String adresa) throws Exception {
        HttpURLConnection v = (HttpURLConnection) new URL(adresa).openConnection();
        v.setConnectTimeout(10000);
        v.setReadTimeout(20000);
        v.setRequestProperty("User-Agent", "BeneluxRoadInfo-Android");
        v.setRequestProperty("Accept", "application/json");
        try {
            if (v.getResponseCode() != 200) throw new Exception("HTTP " + v.getResponseCode());
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(v.getInputStream(), StandardCharsets.UTF_8))) {
                String red;
                while ((red = r.readLine()) != null) sb.append(red).append('\n');
            }
            return sb.toString();
        } finally {
            v.disconnect();
        }
    }
}
