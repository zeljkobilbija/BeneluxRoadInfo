package com.zeljkobilbija001.beneluxroadinfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Jedna saobraćajna poruka sa verkehr.autobahn.de (upozorenje ili zatvaranje).
 * Tekst opisa je na nemačkom, onako kako ga daje Autobahn GmbH; vrsta se određuje iz njega.
 */
public final class Dogadjaj {

    /** Vrste, poređane po važnosti (manji broj = važnije). */
    public static final int ZATVORENO = 0, UDES = 1, OPASNOST = 2, KOLONA = 3,
            TRAKA = 4, SPORO = 5, PRILAZ = 6, OSTALO = 7;

    public final String id;
    public final int vrsta;
    public final String deonica;   // "Pforzheim-West – Karlsruhe" (može biti prazno)
    public final String smer;      // "Stuttgart → Karlsruhe"
    public final String opis;      // nemački tekst, redovi spojeni sa \n
    public final int kasnjenjeMin; // 0 = nepoznato
    public final long pocetak;     // ms, 0 = nepoznato
    public final double lat, lon;  // početak; NaN ako nema
    public final double lat2, lon2; // kraj (iz geometrije, u smeru vožnje); NaN ako nema
    private boolean bezObavestenja;  // npr. vozilo u kvaru (Austrija): samo u listi

    private Dogadjaj(String id, int vrsta, String deonica, String smer, String opis,
                     int kasnjenjeMin, long pocetak, double lat, double lon, double lat2, double lon2) {
        this.id = id;
        this.vrsta = vrsta;
        this.deonica = deonica;
        this.smer = smer;
        this.opis = opis;
        this.kasnjenjeMin = kasnjenjeMin;
        this.pocetak = pocetak;
        this.lat = lat;
        this.lon = lon;
        this.lat2 = lat2;
        this.lon2 = lon2;
    }

    /** Da li zbog ove poruke vredi poslati obaveštenje (udes, kolona, zatvoren put, opasnost). */
    public boolean zaObavestenje() {
        if (bezObavestenja) return false;
        switch (vrsta) {
            case ZATVORENO:
            case UDES:
            case OPASNOST:
                return true;
            case KOLONA:
                return kasnjenjeMin == 0 || kasnjenjeMin >= 10;
            default:
                return false;
        }
    }

    public boolean imaKoordinate() {
        return !Double.isNaN(lat) && !Double.isNaN(lon);
    }

    // ---------- Austrija: EVIS.AT / ASFINAG preko našeg posrednika (Cloudflare Worker) ----------

    /**
     * Švajcarska (FEDRO/ASTRA Traffic Data Platform preko posrednika /ch): isti oblik zapisa kao EVIS, ali
     * poruke na autoputevima nemaju koordinate – mesto je u polju m ("Villeneuve – Raststätte Yvorne") i
     * traži se po imenu među mestima autoputa (izlazi, petlje, odmorišta). Dva mesta i poznat smer
     * ("A9 Vevey -> Martigny"): početak i kraj u smeru vožnje; oba smera: samo prvo mesto (bez smera).
     */
    static List<Dogadjaj> izCh(String json, Podaci p) throws Exception {
        List<Dogadjaj> l = new ArrayList<>();
        JSONArray a = new JSONObject(json).optJSONArray("dogadjaji");
        if (a == null) return l;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            JSONArray t = o.optJSONArray("t"), d = o.optJSONArray("d");
            String tipovi = t == null ? "" : t.toString(), podtipovi = d == null ? "" : d.toString();
            int vrsta = vrstaEvis(tipovi, podtipovi);
            String opis = o.optString("o", "");
            String deonica = o.optString("m", "");
            String smer = o.optString("s", "");
            double la = Double.NaN, lo = Double.NaN, la2 = Double.NaN, lo2 = Double.NaN;
            JSONArray p1 = o.optJSONArray("a"), p2 = o.optJSONArray("b");
            if (p1 != null) {
                la = p1.optDouble(0);
                lo = p1.optDouble(1);
                if (p2 != null) {
                    la2 = p2.optDouble(0);
                    lo2 = p2.optDouble(1);
                }
            } else if (p != null && !deonica.isEmpty()) {
                String[] mesta = deonica.split(" – ");
                Stavka s1 = mestoPoImenu(p, mesta[0]);
                Stavka s2 = mesta.length > 1 ? mestoPoImenu(p, mesta[1]) : null;
                if (s1 == null && s2 != null) {
                    s1 = s2;
                    s2 = null;
                }
                if (s1 != null) {
                    la = s1.lat;
                    lo = s1.lon;
                    if (s2 != null && !smer.isEmpty() && s2 != s1) {
                        la2 = s2.lat;
                        lo2 = s2.lon;
                    }
                }
            }
            Dogadjaj x = new Dogadjaj(o.optString("id"), vrsta, deonica, smer.isEmpty() ? "" : "→ " + smer, opis, o.optInt("k", 0),
                    o.optLong("st", 0), la, lo, la2, lo2);
            x.bezObavestenja = tipovi.contains("VehicleObstruction") && podtipovi.contains("brokenDownVehicle")
                    && !tipovi.contains("Accident");
            l.add(x);
        }
        return l;
    }

    /** Mesto autoputa po imenu iz švajcarske poruke ("Raststätte Yvorne", "Verzweigung Hagnau", "Belmont-Tunnel"). */
    private static Stavka mestoPoImenu(Podaci p, String ime) {
        String trazi = normalizuj(ime.replaceAll("(?i)\\b(Autobahndreieck|Autobahnkreuz|Verzweigung|Raststätte|Rastplatz"
                + "|Parkplatz|Tunnel|Anschluss|Ortschaft|Zollamt|Grenze)\\b", " ").replaceAll("(?i)-?Tunnel$", " "));
        if (trazi.length() < 3) return null;
        Stavka tacno = null, pocetak = null;
        for (Stavka s : p.stavke) {
            if (!s.imaKoordinate()) continue;
            String n = normalizuj(s.naziv);
            if (n.isEmpty()) continue;
            if (n.equals(trazi)) {
                if (tacno == null) tacno = s;
            } else if (pocetak == null && (n.startsWith(trazi + " ") || trazi.startsWith(n + " "))) {
                pocetak = s;
            }
        }
        return tacno != null ? tacno : pocetak;
    }

    /** Bez akcenata i znakova; strane sveta i „Saint/Sankt“ izjednačene na sva tri jezika (Sion-Ost = Sion-Est). */
    private static String normalizuj(String s) {
        return java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim()
                .replaceAll("\\b(ost|est|east)\\b", "e").replaceAll("\\b(west|ouest|ovest)\\b", "w")
                .replaceAll("\\b(sud|sued)\\b", "s").replaceAll("\\bnord\\b", "n")
                .replaceAll("\\b(sankt|saint|san)\\b", "st");
    }

    /**
     * Odgovor posrednika evis-posrednik.zeljkobilbija.workers.dev: {"dogadjaji":[{id, p, t, d, st, en, o, m, s, a, b, man}]}
     * (t = vrste zapisa DATEX II: Accident, AbnormalTraffic, RoadOrCarriagewayOrLaneManagement…; d = podvrste).
     */
    static List<Dogadjaj> izEvis(String json) throws Exception {
        List<Dogadjaj> l = new ArrayList<>();
        JSONArray a = new JSONObject(json).optJSONArray("dogadjaji");
        if (a == null) return l;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            JSONArray t = o.optJSONArray("t"), d = o.optJSONArray("d");
            String tipovi = t == null ? "" : t.toString(), podtipovi = d == null ? "" : d.toString();
            int vrsta = vrstaEvis(tipovi, podtipovi);
            String opis = o.optString("o", "");
            String deonica = mestoEvis(opis);
            if (deonica.isEmpty()) deonica = o.optString("m", "");
            String smer = o.optString("s", "");
            JSONArray p1 = o.optJSONArray("a"), p2 = o.optJSONArray("b");
            Dogadjaj x = new Dogadjaj(o.optString("id"), vrsta, deonica, smer.isEmpty() ? "" : "→ " + smer, opis, 0,
                    o.optLong("st", 0),
                    p1 != null ? p1.optDouble(0) : Double.NaN, p1 != null ? p1.optDouble(1) : Double.NaN,
                    p2 != null ? p2.optDouble(0) : Double.NaN, p2 != null ? p2.optDouble(1) : Double.NaN);
            x.bezObavestenja = tipovi.contains("VehicleObstruction") && podtipovi.contains("brokenDownVehicle")
                    && !tipovi.contains("Accident");
            l.add(x);
        }
        return l;
    }

    // Planirani radovi: "A1 West Autobahn: In Fahrtrichtung von Linz nach Wien zwischen Anschlussstelle Haag und
    // Anschlussstelle St.Valentin sind …, 16.11.2026 - 11.12.2026: Mo, Di …" (ranije je naslov bio „Di“).
    private static final java.util.regex.Pattern EVIS_PLAN = java.util.regex.Pattern.compile(
            "[Ii]n Fahrtrichtung .*? (?:zwischen|bei) (.+?)(?: ist | sind | wird | Verkehr | veränderte | gesperrt| Die |,|$)");

    /** Mesto (deonica) iz opisa EVIS vesti, bez kilometara; prazno ako se ne prepozna. */
    static String mestoEvis(String opis) {
        String m = "";
        java.util.regex.Matcher pl = EVIS_PLAN.matcher(opis);
        if (pl.find()) {
            m = pl.group(1);
        } else {
            // Uživo: "A9 Pyhrn Autobahn, Graz Richtung Maribor (SLO), zwischen Übelbach (km 157.1) und Peggau (km 165.2), …"
            String[] delovi = opis.split(", ");
            if (delovi.length >= 3) m = delovi[2];
        }
        return m.replaceFirst("^zwischen ", "").replace("Anschlussstelle ", "").replace(" und ", " – ")
                .replaceAll(" \\(km [\\d.,]+\\)", "").trim();
    }

    static int vrstaEvis(String t, String d) {
        if (t.contains("Accident")) return UDES;
        if (t.contains("RoadOrCarriagewayOrLaneManagement") && (d.contains("roadClosed") || d.contains("carriagewayClosures"))) {
            return d.contains("slipRoads") && !d.contains("mainCarriageway") ? PRILAZ : ZATVORENO;
        }
        if (t.contains("AbnormalTraffic")) return d.contains("slowTraffic") || d.contains("heavyTraffic") ? SPORO : KOLONA;
        if (t.contains("Obstruction") || t.contains("PoorEnvironment") || t.contains("WeatherRelated")
                || t.contains("NonWeatherRelated")) return OPASNOST;
        if (t.contains("RoadOrCarriagewayOrLaneManagement") || t.contains("MaintenanceWorks")
                || t.contains("ConstructionWorks")) return TRAKA;
        if (t.contains("SpeedManagement")) return SPORO;
        return OSTALO;
    }

    // ---------- Čitanje JSON-a ----------

    /** Lista iz odgovora servisa "warning" ili "closure"; najavljena (buduća) zatvaranja se preskaču. */
    static List<Dogadjaj> izJsona(String json, String kljuc) throws Exception {
        List<Dogadjaj> l = new ArrayList<>();
        JSONArray a = new JSONObject(json).optJSONArray(kljuc);
        if (a == null) return l;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || o.optBoolean("future", false)) continue;
            Dogadjaj d = jedan(o);
            if (d != null) l.add(d);
        }
        return l;
    }

    private static Dogadjaj jedan(JSONObject o) {
        String id = stabilanId(o.optString("identifier", ""));
        if (id.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        JSONArray opisi = o.optJSONArray("description");
        if (opisi != null) {
            boolean prazan = true;
            for (int i = 0; i < opisi.length(); i++) {
                String red = opisi.optString(i, "").trim();
                if (red.isEmpty()) {
                    prazan = true;
                    continue;
                }
                if (sb.length() > 0) sb.append(prazan ? "\n\n" : "\n");
                sb.append(red);
                prazan = false;
            }
        }
        String opis = sb.toString();

        int kasnjenje = 0;
        try {
            kasnjenje = Integer.parseInt(o.optString("delayTimeValue", "0").trim());
        } catch (NumberFormatException ignored) { }

        String prikaz = o.optString("display_type", "");
        int vrsta;
        if ("CLOSURE".equals(prikaz)) vrsta = ZATVORENO;
        else if ("CLOSURE_ENTRY_EXIT".equals(prikaz)) vrsta = PRILAZ;
        else vrsta = vrstaUpozorenja(opis.toLowerCase(Locale.GERMAN),
                    o.optString("abnormalTrafficType", ""), "true".equals(o.optString("isBlocked")), kasnjenje);

        String naslov = o.optString("title", "");
        int crta = naslov.indexOf('|');
        String deonica = crta >= 0 ? naslov.substring(crta + 1).trim() : "";
        if ("Verkehrsmeldung".equalsIgnoreCase(deonica)) deonica = "";
        deonica = deonica.replace(" - ", " – ");

        String smer = o.optString("subtitle", "").trim().replace("->", "→");

        double lat = Double.NaN, lon = Double.NaN;
        JSONObject k = o.optJSONObject("coordinate");
        if (k != null) {
            lat = k.optDouble("lat", Double.NaN);
            lon = k.optDouble("long", Double.NaN);
        }
        // kraj poruke: poslednja tačka linije (GeoJSON: [lon, lat])
        double lat2 = Double.NaN, lon2 = Double.NaN;
        JSONObject g = o.optJSONObject("geometry");
        JSONArray tacke = g != null ? g.optJSONArray("coordinates") : null;
        if (tacke != null && tacke.length() > 1) {
            JSONArray z = tacke.optJSONArray(tacke.length() - 1);
            if (z != null && z.length() >= 2) {
                lon2 = z.optDouble(0, Double.NaN);
                lat2 = z.optDouble(1, Double.NaN);
            }
        }

        return new Dogadjaj(id, vrsta, deonica, smer, opis, kasnjenje,
                vreme(o.optString("startTimestamp", "")), lat, lon, lat2, lon2);
    }

    /**
     * Oznaka koja se ne menja dok poruka traje. Zvanične poruke ("NLW_2026_002954--vi-hind.<vreme>",
     * zatvaranja "2026-058313--…") posle "--" dobijaju vreme poslednje izmene, pa se uzima deo pre "--"
     * (broj događaja). INRIX poruke ("INRIX--vi-avl.<početak>_NNN") su stalne u celini.
     */
    static String stabilanId(String id) {
        int i = id.indexOf("--");
        if (i <= 0) return id;
        String pre = id.substring(0, i);
        return pre.matches(".*\\d.*") ? pre : id;
    }

    private static int vrstaUpozorenja(String t, String tip, boolean blokirano, int kasnjenje) {
        if (t.contains("falschfahrer")) return OPASNOST;
        if (t.contains("unfall")) return UDES;
        if (blokirano || t.contains("vollsperrung") || t.contains("fahrbahn gesperrt")
                || t.contains("gesperrt in beide")) return ZATVORENO;
        if (t.contains("gegenstand") || t.contains("brand") || t.contains("glätte") || t.contains("glatteis")
                || t.contains("nebel") || t.contains("tiere") || t.contains("personen auf der fahrbahn")
                || t.contains("fahrbahnschäden") || t.contains("liegengeblieben") || t.contains("panne")
                || t.contains("gefahr")) return OPASNOST;
        if ("QUEUING_TRAFFIC".equals(tip) || t.contains("stau")) return KOLONA;
        if (t.contains("fahrstreifen gesperrt") || t.contains("fahrstreifen blockiert")) return TRAKA;
        if ("SLOW_TRAFFIC".equals(tip) || "HEAVY_TRAFFIC".equals(tip) || "UNSPECIFIED_ABNORMAL_TRAFFIC".equals(tip)
                || t.contains("stockend") || t.contains("durchschnittsgeschwindigkeit")) {
            return kasnjenje >= 15 ? KOLONA : SPORO;
        }
        return OSTALO;
    }

    private static long vreme(String iso) {
        if (iso.length() < 19) return 0;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = f.parse(iso.substring(0, 19));
            return d == null ? 0 : d.getTime();
        } catch (Exception e) {
            return 0;
        }
    }
}
