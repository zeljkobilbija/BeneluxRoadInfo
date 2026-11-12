package com.zeljkobilbija001.beneluxroadinfo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cene goriva i punjenja po zemlji, iz otvorenih izvora. Svaka zemlja daje jedan ili dva bloka:
 * <ul>
 *   <li>BE: zvanične maksimalne cene (FOD Economie, be.STAT/Statbel, CSV), skoro svakog dana;</li>
 *   <li>NL: dnevni prosek (CBS 80416ned, kasni ~nedelju dana) + kvartalni prosek sa CNG i punjenjem (CBS 84991NED);</li>
 *   <li>LU: nedeljni prosek (EU Weekly Oil Bulletin, preko fuel-prices.eu, CC BY 4.0)
 *       + zvanični mesečni prosek sa LPG, vodonikom i punjenjem (data.public.lu).</li>
 * </ul>
 * Svaki preuzeti fajl se čuva 6 sati; bez interneta se koristi poslednji sačuvani.
 */
public final class CeneGoriva {

    /** Vrste blokova; svaka ima svoju napomenu ispod cena. */
    public static final int BE_MAKS = 0, NL_DAN = 1, NL_KVARTAL = 2, LU_NEDELJA = 3, LU_MESEC = 4;

    public static final class Cena {
        /** Naziv goriva (npr. "Diesel"); ako je prazan, koristi se nazivRes. */
        public final String naziv;
        public final int nazivRes;
        public final double eura;
        public final String jedinica;

        Cena(String naziv, int nazivRes, double eura, String jedinica) {
            this.naziv = naziv;
            this.nazivRes = nazivRes;
            this.eura = eura;
            this.jedinica = jedinica;
        }

        public String naziv(Context c) {
            return naziv.isEmpty() ? c.getString(nazivRes) : naziv;
        }
    }

    public static final class Blok {
        public final int vrsta;
        /** Period na koji se cene odnose, već napisan za prikaz (datum, mesec ili kvartal). */
        public final String period;
        public final List<Cena> cene;

        Blok(int vrsta, String period, List<Cena> cene) {
            this.vrsta = vrsta;
            this.period = period;
            this.cene = cene;
        }
    }

    public interface Odgovor {
        /** Poziva se na glavnoj niti; prazna lista ako cene nisu dostupne. */
        void stiglo(List<Blok> blokovi);
    }

    private static final String BE_URL =
            "https://bestat.statbel.fgov.be/bestat/api/views/665e2960-bf86-4d64-b4a8-90f2d30ea892/result/CSV";
    private static final String NL_DAN_URL =
            "https://opendata.cbs.nl/ODataApi/odata/80416ned/TypedDataSet?$filter=Perioden+ge+'%s'";
    private static final String NL_KVARTAL_URL = "https://opendata.cbs.nl/ODataApi/odata/84991NED/TypedDataSet";
    private static final String LU_NEDELJA_URL = "https://www.fuel-prices.eu/Luxembourg/?format=json";
    private static final String LU_MESEC_API =
            "https://data.public.lu/api/1/datasets/comparaison-des-prix-de-carburants-par-motorisation/";

    private static final String L = "€/L", KG = "€/kg", KWH = "€/kWh";
    private static final long VAZI_MS = 6 * 60 * 60 * 1000L;
    private static final String PREFS = "cene_goriva";
    private static final ExecutorService NIT = Executors.newSingleThreadExecutor();

    private CeneGoriva() { }

    public static void ucitaj(Context ctx, String zemlja, Odgovor odgovor) {
        Context c = ctx.getApplicationContext();
        Handler glavna = new Handler(Looper.getMainLooper());
        NIT.execute(() -> {
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            List<Blok> l = new ArrayList<>();
            switch (zemlja) {
                case "BE":
                    dodaj(l, belgija(tekst(p, "BE", BE_URL)));
                    break;
                case "NL": {
                    Calendar k = Calendar.getInstance();
                    k.add(Calendar.DAY_OF_MONTH, -30);
                    String od = new SimpleDateFormat("yyyyMMdd", Locale.US).format(k.getTime());
                    dodaj(l, holandijaDan(tekst(p, "NL", String.format(Locale.US, NL_DAN_URL, od))));
                    dodaj(l, holandijaKvartal(tekst(p, "NL_KV", NL_KVARTAL_URL)));
                    break;
                }
                default:
                    dodaj(l, luksemburgNedelja(tekst(p, "LU", LU_NEDELJA_URL)));
                    dodaj(l, luksemburgMesec(tekst(p, "LU_MES", null)));
            }
            glavna.post(() -> odgovor.stiglo(l));
        });
    }

    private static void dodaj(List<Blok> l, Blok b) {
        if (b != null && !b.cene.isEmpty()) l.add(b);
    }

    /** Sveže sačuvan tekst, inače preuzimanje; bez interneta poslednji sačuvani (ili null). */
    private static String tekst(SharedPreferences p, String kljuc, String url) {
        String sacuvano = p.getString(kljuc, null);
        if (sacuvano != null && System.currentTimeMillis() - p.getLong(kljuc + "_vreme", 0) < VAZI_MS) {
            return sacuvano;
        }
        try {
            String t = url != null ? get(url) : get(adresaLuMesec());
            p.edit().putString(kljuc, t).putLong(kljuc + "_vreme", System.currentTimeMillis()).apply();
            return t;
        } catch (Exception e) {
            return sacuvano;
        }
    }

    /** Adresa CSV-a na data.public.lu se menja sa svakim osvežavanjem, pa se čita iz opisa skupa. */
    private static String adresaLuMesec() throws Exception {
        JSONArray r = new JSONObject(get(LU_MESEC_API)).getJSONArray("resources");
        for (int i = 0; i < r.length(); i++) {
            String u = r.getJSONObject(i).optString("url");
            if (u.endsWith(".csv")) return u;
        }
        throw new Exception("nema CSV");
    }

    // ---------- Belgija ----------

    /** CSV be.STAT: "Product group","Product","Day","Price excl. VAT","Price difference excl. VAT","Price incl. VAT". */
    private static Blok belgija(String csv) {
        if (csv == null) return null;
        try {
            String[][] trazeno = {
                    {"Euro Super 95 E10", "Euro 95 E10"},
                    {"Euro Super 95 E5", "Euro 95 E5"},
                    {"Super Plus 98 E10", "Super 98 E10"},
                    {"Super Plus 98 E5", "Super 98 E5"},
                    {"Road Diesel B7", "Diesel B7"},
                    {"Road Diesel B10", "Diesel B10"},
                    {"Road Diesel XTL", "Diesel XTL (HVO)"},
                    {"Autogas LPG (at the pump)", "LPG"},
            };
            List<Cena> cene = new ArrayList<>();
            Date datum = null;
            String[] redovi = csv.split("\n");
            for (String[] t : trazeno) {
                for (String red : redovi) {
                    String[] k = red.replace("\"", "").split(",");
                    if (k.length < 6 || !k[1].startsWith(t[0])) continue;
                    cene.add(new Cena(t[1], 0, Double.parseDouble(k[k.length - 1].trim()), L));
                    if (datum == null) datum = new SimpleDateFormat("ddMMMyy", Locale.US).parse(k[2].trim());
                    break;
                }
            }
            return new Blok(BE_MAKS, dan(datum), cene);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- Holandija ----------

    /** CBS 80416ned: poslednji dan koji ima cene. */
    private static Blok holandijaDan(String json) {
        if (json == null) return null;
        try {
            JSONArray a = new JSONObject(json).getJSONArray("value");
            for (int i = a.length() - 1; i >= 0; i--) {
                JSONObject o = a.getJSONObject(i);
                if (o.isNull("BenzineEuro95_1") || o.isNull("Diesel_2")) continue;
                List<Cena> cene = new ArrayList<>();
                cene.add(new Cena("Euro 95", 0, o.getDouble("BenzineEuro95_1"), L));
                cene.add(new Cena("Diesel", 0, o.getDouble("Diesel_2"), L));
                if (!o.isNull("Lpg_3")) cene.add(new Cena("LPG", 0, o.getDouble("Lpg_3"), L));
                Date d = new SimpleDateFormat("yyyyMMdd", Locale.US).parse(o.getString("Perioden").trim());
                return new Blok(NL_DAN, dan(d), cene);
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** CBS 84991NED: poslednji kvartal; odatle samo CNG i punjenje (benzin i dizel su sveži u dnevnoj tabeli). */
    private static Blok holandijaKvartal(String json) {
        if (json == null) return null;
        try {
            JSONArray a = new JSONObject(json).getJSONArray("value");
            for (int i = a.length() - 1; i >= 0; i--) {
                JSONObject o = a.getJSONObject(i);
                String per = o.optString("Perioden", "");
                if (!per.contains("KW")) continue; // samo kvartali (npr. "2026KW02")
                List<Cena> cene = new ArrayList<>();
                if (!o.isNull("Cng_4")) cene.add(new Cena("CNG", 0, o.getDouble("Cng_4"), KG));
                if (!o.isNull("Elektrisch_5")) cene.add(new Cena("", R.string.cena_punjenje, o.getDouble("Elektrisch_5"), KWH));
                if (cene.isEmpty()) continue;
                return new Blok(NL_KVARTAL, "Q" + per.substring(per.length() - 1) + " " + per.substring(0, 4), cene);
            }
        } catch (Exception ignored) { }
        return null;
    }

    // ---------- Luksemburg ----------

    /** fuel-prices.eu (Weekly Oil Bulletin): fp.prices[].fuel_code/mean, fp.updated. */
    private static Blok luksemburgNedelja(String json) {
        if (json == null) return null;
        try {
            JSONObject fp = new JSONObject(json).getJSONObject("fp");
            JSONArray a = fp.getJSONArray("prices");
            List<Cena> cene = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                double v = o.optDouble("mean", Double.NaN);
                if (Double.isNaN(v)) continue;
                String kod = o.optString("fuel_code");
                String naziv;
                switch (kod) {
                    case "E5": naziv = "Euro 95"; break;
                    case "E10": naziv = "Euro 95 E10"; break;
                    case "B7": naziv = "Diesel"; break;
                    case "LPG": naziv = "LPG"; break;
                    default: naziv = o.optString("fuel", kod);
                }
                cene.add(new Cena(naziv, 0, v, L));
            }
            String upd = fp.optString("updated", "");
            Date d = upd.length() >= 10 ? new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(upd.substring(0, 10)) : null;
            return new Blok(LU_NEDELJA, dan(d), cene);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * data.public.lu (Ministère de la Mobilité), mesečno:
     * Intervalle,Essence_E10,Diesel_B7,Hydrogene,LPG,Electricie_Fournisseur,Recharge_AC,Recharge_DC.
     * Odatle LPG, vodonik i punjenje (benzin i dizel su sveži u nedeljnom bloku).
     */
    private static Blok luksemburgMesec(String csv) {
        if (csv == null) return null;
        try {
            String[] redovi = csv.replace("﻿", "").trim().split("\n");
            String[] zag = redovi[0].trim().split(",");
            String[] k = redovi[redovi.length - 1].trim().split(",");
            List<Cena> cene = new ArrayList<>();
            for (int i = 1; i < zag.length && i < k.length; i++) {
                double v;
                try {
                    v = Double.parseDouble(k[i].trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                switch (zag[i].trim()) {
                    case "LPG": cene.add(new Cena("LPG", 0, v, L)); break;
                    case "Hydrogene": cene.add(new Cena("", R.string.cena_vodonik, v, KG)); break;
                    case "Recharge_AC": cene.add(new Cena("", R.string.cena_punjenje_ac, v, KWH)); break;
                    case "Recharge_DC": cene.add(new Cena("", R.string.cena_punjenje_dc, v, KWH)); break;
                    default: break;
                }
            }
            String mesec = k[0].trim();
            try {
                Date d = new SimpleDateFormat("MMM-yy", Locale.US).parse(mesec);
                mesec = new SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(d);
            } catch (Exception ignored) { }
            return new Blok(LU_MESEC, mesec, cene);
        } catch (Exception e) {
            return null;
        }
    }

    private static String dan(Date d) {
        return d == null ? "?" : DateFormat.getDateInstance(DateFormat.MEDIUM).format(d);
    }

    private static String get(String adresa) throws Exception {
        HttpURLConnection v = (HttpURLConnection) new URL(adresa).openConnection();
        v.setConnectTimeout(10000);
        v.setReadTimeout(15000);
        v.setRequestProperty("User-Agent", "BeneluxRoadInfo-Android");
        v.setRequestProperty("Accept", "application/json, text/csv, */*");
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
