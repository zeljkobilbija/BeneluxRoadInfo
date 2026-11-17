package com.zeljkobilbija001.beneluxroadinfo;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Učitava podatke jednog autoputa iz assets/autoputevi/*.json (jednom, pa čuva u memoriji). */
public final class Podaci {

    public final List<Deonica> deonice = new ArrayList<>();
    public final List<Stavka> stavke = new ArrayList<>();

    private static final Map<String, Podaci> KES = new HashMap<>();

    private Podaci() { }

    public static synchronized Podaci za(Context c, Autoput a) {
        if (a == null || !a.dostupan()) return null;
        Podaci p = KES.get(a.id);
        if (p == null) {
            try {
                p = ucitaj(c, a.datoteka);
                KES.put(a.id, p);
            } catch (Exception e) {
                return null;
            }
        }
        return p;
    }

    private static Podaci ucitaj(Context c, String datoteka) throws Exception {
        JSONObject koren = new JSONObject(procitaj(c, datoteka));
        Podaci p = new Podaci();

        JSONArray sek = koren.getJSONArray("sekcije");
        for (int i = 0; i < sek.length(); i++) {
            JSONObject s = sek.getJSONObject(i);
            Deonica d = new Deonica(s.getString("od"), s.getString("do"),
                    s.getString("plus"), s.getString("minus"));
            JSONArray kal = s.optJSONArray("kal");
            if (kal != null) {
                int n = kal.length();
                d.kalNas = new double[n];
                d.kalTabla = new double[n];
                d.kalSmer = new int[n];
                for (int j = 0; j < n; j++) {
                    JSONArray q = kal.getJSONArray(j);
                    d.kalNas[j] = q.getDouble(0);
                    d.kalTabla[j] = q.getDouble(1);
                    d.kalSmer[j] = q.getInt(2);
                }
            }
            // autostrada u jednom komadu (alati/spoji_deonice.js): stvarni prekidi
            JSONArray prek = s.optJSONArray("prekidi");
            if (prek != null) {
                d.prekidOd = new double[prek.length()];
                d.prekidDo = new double[prek.length()];
                for (int j = 0; j < prek.length(); j++) {
                    d.prekidOd[j] = prek.getJSONArray(j).getDouble(0);
                    d.prekidDo[j] = prek.getJSONArray(j).getDouble(1);
                }
            }
            p.deonice.add(d);
        }

        JSONArray niz = koren.getJSONArray("stavke");
        for (int i = 0; i < niz.length(); i++) {
            JSONObject o = niz.getJSONObject(i);
            Stavka s = new Stavka();
            s.tip = o.getString("t").charAt(0);
            s.deonica = o.getInt("si");
            s.km = o.getDouble("km");
            if (s.deonica >= 0 && s.deonica < p.deonice.size()) s.kmTabla = p.deonice.get(s.deonica).kmTabla(s.km);
            String strana = o.optString("s", "+");
            s.strana = strana.isEmpty() ? '+' : strana.charAt(0);
            s.naziv = o.optString("n", "");
            s.ref = o.optString("ref", "");
            s.gorivo = lista(o.optJSONArray("f"));
            s.hrana = lista(o.optJSONArray("h"));
            s.punjaci = lista(o.optJSONArray("e"));
            s.restoran = o.optInt("re", 0) == 1;
            s.wc = o.optInt("wc", 0) == 1;
            s.tus = o.optInt("du", 0) == 1;
            s.hotel = o.optInt("ho", 0) == 1;
            s.prodavnica = o.optInt("sh", 0) == 1;
            s.kamioni = o.optString("lkw", "");
            s.radnoVreme = o.optString("oh", "");
            s.telefon = o.optString("tel", "");
            s.mejl = o.optString("mail", "");
            s.sajt = o.optString("web", "");
            s.lat = o.optDouble("la", Double.NaN);
            s.lon = o.optDouble("lo", Double.NaN);
            p.stavke.add(s);
        }
        return p;
    }

    private static List<String> lista(JSONArray a) throws Exception {
        if (a == null || a.length() == 0) return Collections.emptyList();
        List<String> l = new ArrayList<>(a.length());
        for (int i = 0; i < a.length(); i++) l.add(a.getString(i));
        return l;
    }

    private static String procitaj(Context c, String datoteka) throws Exception {
        try (InputStream in = c.getAssets().open(datoteka)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Ukupna dužina: zbir dužina svih deonica (poslednja tačka na svakoj). */
    public int duzinaKm() {
        double[] max = new double[deonice.size()];
        for (Stavka s : stavke) {
            if (s.deonica >= 0 && s.deonica < max.length) max[s.deonica] = Math.max(max[s.deonica], s.km);
        }
        double z = 0;
        for (double m : max) z += m;
        return (int) Math.round(z);
    }
}
