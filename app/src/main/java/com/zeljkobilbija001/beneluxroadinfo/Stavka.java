package com.zeljkobilbija001.beneluxroadinfo;

import java.util.Collections;
import java.util.List;

/** Jedna tačka na autoputu: izlaz, čvorište, odmorište, Autohof ili parking. */
public final class Stavka {
    public static final char IZLAZ = 'A', CVORISTE = 'K', ODMORISTE = 'R', AUTOHOF = 'H', PARKING = 'P';

    public char tip;
    public int deonica;
    public double km;
    /** '+' = smer rastućih kilometara, '-' = suprotan smer; izlazi važe za oba smera. */
    public char strana = '+';
    public String naziv = "";
    public String ref = "";
    public List<String> gorivo = Collections.emptyList();
    public List<String> hrana = Collections.emptyList();
    public List<String> punjaci = Collections.emptyList();
    public boolean restoran, wc, tus, hotel, prodavnica;
    public String kamioni = "";
    public String radnoVreme = "";
    /** Koordinate (WGS84); NaN ako ih nema. */
    public double lat = Double.NaN, lon = Double.NaN;

    public boolean imaKoordinate() {
        return !Double.isNaN(lat) && !Double.isNaN(lon);
    }

    public boolean jeIzlaz() {
        return tip == IZLAZ || tip == CVORISTE;
    }

    public boolean imaHranu() {
        return restoran || !hrana.isEmpty();
    }

    /** Da li se vidi kada se vozi u datom smeru. */
    public boolean uSmeru(char smer) {
        return jeIzlaz() || strana == smer;
    }
}
