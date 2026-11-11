package com.zeljkobilbija001.beneluxroadinfo;

/** Jedan autoput na početnom ekranu (NL A2, BE A10, LU A1…). */
public final class Autoput {
    public final String zemlja;   // "NL", "BE" ili "LU"
    public final String ref;      // "A2"
    public final String id;       // "NL-A2" (jedinstven, jer A1 postoji u sve tri zemlje)
    public final String ruta;     // "Amsterdam – Utrecht – Eindhoven – Maastricht"
    public final String datoteka; // "autoputevi/nl-a2.json" ili null ako podaci još ne postoje

    public Autoput(String zemlja, String ref, String ruta, String datoteka) {
        this.zemlja = zemlja;
        this.ref = ref;
        this.id = zemlja + "-" + ref;
        this.ruta = ruta;
        this.datoteka = datoteka;
    }

    /** Oznaka kao na tabli: "A2". */
    public String oznaka() {
        return ref;
    }

    /** Holandski štit je crven, belgijski i luksemburški plavi. */
    public int stit() {
        return "NL".equals(zemlja) ? R.drawable.bg_stit : R.drawable.bg_stit_plavi;
    }

    public boolean dostupan() {
        return datoteka != null;
    }
}
