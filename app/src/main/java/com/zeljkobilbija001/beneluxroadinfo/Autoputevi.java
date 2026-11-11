package com.zeljkobilbija001.beneluxroadinfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Spisak autoputeva u Holandiji, Belgiji i Luksemburgu. Novi autoput: dodaj JSON u assets/autoputevi
 * (node alati/benelux_skripta.js NL-A2) i upiši ime datoteke umesto null ("autoputevi/nl-a2.json").
 * Za Belgiju se u nazivu rute navodi i evropska oznaka (E40), jer je na tablama češća od A-broja.
 */
public final class Autoputevi {

    private Autoputevi() { }

    public static final List<Autoput> SVE = Arrays.asList(
            // Holandija
            new Autoput("NL", "A1", "Amsterdam – Amersfoort – Deventer – Oldenzaal", null),
            new Autoput("NL", "A2", "Amsterdam – Utrecht – Eindhoven – Maastricht", "autoputevi/nl-a2.json"),
            new Autoput("NL", "A4", "Amsterdam – Den Haag – Rotterdam – Bergen op Zoom", null),
            new Autoput("NL", "A5", "Hoofddorp – Amsterdam-Westpoort", null),
            new Autoput("NL", "A6", "Muiderberg – Almere – Lelystad – Joure", null),
            new Autoput("NL", "A7", "Zaandam – Afsluitdijk – Groningen – Nieuweschans", null),
            new Autoput("NL", "A9", "Badhoevedorp – Amstelveen – Alkmaar", null),
            new Autoput("NL", "A10", "Ring Amsterdam", null),
            new Autoput("NL", "A12", "Den Haag – Utrecht – Arnhem – Zevenaar", null),
            new Autoput("NL", "A13", "Den Haag – Delft – Rotterdam", null),
            new Autoput("NL", "A15", "Maasvlakte – Rotterdam – Gorinchem – Nijmegen", null),
            new Autoput("NL", "A16", "Rotterdam – Dordrecht – Breda – Antwerpen", null),
            new Autoput("NL", "A17", "Moerdijk – Roosendaal", null),
            new Autoput("NL", "A20", "Maassluis – Rotterdam – Gouda", null),
            new Autoput("NL", "A27", "Breda – Gorinchem – Utrecht – Almere", null),
            new Autoput("NL", "A28", "Utrecht – Amersfoort – Zwolle – Groningen", null),
            new Autoput("NL", "A29", "Rotterdam – Hellegatsplein", null),
            new Autoput("NL", "A30", "Barneveld – Ede", null),
            new Autoput("NL", "A32", "Meppel – Heerenveen – Leeuwarden", null),
            new Autoput("NL", "A35", "Almelo – Hengelo – Enschede", null),
            new Autoput("NL", "A37", "Hoogeveen – Emmen – Duitse grens", null),
            new Autoput("NL", "A44", "Den Haag – Wassenaar – Leiden – Nieuw-Vennep", null),
            new Autoput("NL", "A50", "Eindhoven – Nijmegen – Arnhem – Zwolle", null),
            new Autoput("NL", "A58", "Eindhoven – Tilburg – Breda – Vlissingen", null),
            new Autoput("NL", "A59", "Zierikzee – Moerdijk – 's-Hertogenbosch – Oss", null),
            new Autoput("NL", "A65", "'s-Hertogenbosch – Tilburg", null),
            new Autoput("NL", "A67", "Antwerpen – Eindhoven – Venlo", null),
            new Autoput("NL", "A73", "Nijmegen – Venlo – Roermond", null),
            new Autoput("NL", "A76", "Geleen – Heerlen – Aachen", null),
            new Autoput("NL", "A79", "Maastricht – Heerlen", null),
            // Belgija
            new Autoput("BE", "A1", "E19 · Brussel – Antwerpen – Breda", null),
            new Autoput("BE", "A2", "E314 · Leuven – Genk – Maastricht", null),
            new Autoput("BE", "A3", "E40 · Brussel – Leuven – Liège – Aachen", null),
            new Autoput("BE", "A4", "E411 · Bruxelles – Namur – Arlon – Luxembourg", null),
            new Autoput("BE", "A7", "E19 · Nivelles – Mons – Paris", null),
            new Autoput("BE", "A8", "E429 · Halle – Tournai – Lille", null),
            new Autoput("BE", "A10", "E40 · Brussel – Gent – Brugge – Oostende", null),
            new Autoput("BE", "A11", "Antwerpen – Brugge – Knokke", null),
            new Autoput("BE", "A12", "Brussel – Boom – Antwerpen", null),
            new Autoput("BE", "A13", "E313 · Antwerpen – Hasselt – Liège", null),
            new Autoput("BE", "A14", "E17 · Antwerpen – Gent – Kortrijk – Lille", null),
            new Autoput("BE", "A15", "E42 · Mons – Charleroi – Namur – Liège", null),
            new Autoput("BE", "A17", "E403 · Brugge – Kortrijk – Tournai", null),
            new Autoput("BE", "A18", "E40 · Brugge – Veurne – Dunkerque", null),
            new Autoput("BE", "A19", "Kortrijk – Ieper", null),
            new Autoput("BE", "A25", "E25 · Liège – Maastricht", null),
            new Autoput("BE", "A26", "E25 · Liège – Bastogne – Luxembourg", null),
            new Autoput("BE", "A27", "E42 · Verviers – Sankt Vith", null),
            new Autoput("BE", "A54", "E420 · Nivelles – Charleroi", null),
            // Luksemburg
            new Autoput("LU", "A1", "Luxembourg – Grevenmacher – Trier", null),
            new Autoput("LU", "A3", "Luxembourg – Dudelange – Thionville", null),
            new Autoput("LU", "A4", "Luxembourg – Esch-sur-Alzette", null),
            new Autoput("LU", "A6", "Luxembourg – Arlon", null),
            new Autoput("LU", "A7", "Luxembourg – Mersch – Ettelbruck", null),
            new Autoput("LU", "A13", "Pétange – Esch – Schengen", null)
    );

    /** Redosled zemalja na početnom ekranu. */
    public static final String[] ZEMLJE = {"NL", "BE", "LU"};

    /** Autoputevi jedne zemlje ("NL", "BE" ili "LU"), redom kao u spisku. */
    public static List<Autoput> zaZemlju(String zemlja) {
        List<Autoput> l = new ArrayList<>();
        for (Autoput a : SVE) if (a.zemlja.equals(zemlja)) l.add(a);
        return l;
    }

    public static Autoput poId(String id) {
        for (Autoput a : SVE) if (a.id.equals(id)) return a;
        return null;
    }
}
