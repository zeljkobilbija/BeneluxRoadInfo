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
            new Autoput("NL", "A1", "Amsterdam – Amersfoort – Deventer – Oldenzaal", "autoputevi/nl-a1.json"),
            new Autoput("NL", "A2", "Amsterdam – Utrecht – Eindhoven – Maastricht", "autoputevi/nl-a2.json"),
            new Autoput("NL", "A4", "Amsterdam – Den Haag – Rotterdam – Bergen op Zoom", "autoputevi/nl-a4.json"),
            new Autoput("NL", "A5", "Hoofddorp – Amsterdam-Westpoort", null),
            new Autoput("NL", "A6", "Muiderberg – Almere – Lelystad – Joure", "autoputevi/nl-a6.json"),
            new Autoput("NL", "A7", "Zaandam – Afsluitdijk – Groningen – Nieuweschans", "autoputevi/nl-a7.json"),
            new Autoput("NL", "A9", "Badhoevedorp – Amstelveen – Alkmaar", "autoputevi/nl-a9.json"),
            new Autoput("NL", "A10", "Ring Amsterdam", "autoputevi/nl-a10.json"),
            new Autoput("NL", "A12", "Den Haag – Utrecht – Arnhem – Zevenaar", "autoputevi/nl-a12.json"),
            new Autoput("NL", "A13", "Den Haag – Delft – Rotterdam", "autoputevi/nl-a13.json"),
            new Autoput("NL", "A15", "Maasvlakte – Rotterdam – Gorinchem – Nijmegen", "autoputevi/nl-a15.json"),
            new Autoput("NL", "A16", "Rotterdam – Dordrecht – Breda – Antwerpen", "autoputevi/nl-a16.json"),
            new Autoput("NL", "A17", "Moerdijk – Roosendaal", "autoputevi/nl-a17.json"),
            new Autoput("NL", "A20", "Maassluis – Rotterdam – Gouda", "autoputevi/nl-a20.json"),
            new Autoput("NL", "A27", "Breda – Gorinchem – Utrecht – Almere", "autoputevi/nl-a27.json"),
            new Autoput("NL", "A28", "Utrecht – Amersfoort – Zwolle – Groningen", "autoputevi/nl-a28.json"),
            new Autoput("NL", "A29", "Rotterdam – Hellegatsplein", "autoputevi/nl-a29.json"),
            new Autoput("NL", "A30", "Barneveld – Ede", "autoputevi/nl-a30.json"),
            new Autoput("NL", "A32", "Meppel – Heerenveen – Leeuwarden", "autoputevi/nl-a32.json"),
            new Autoput("NL", "A35", "Almelo – Hengelo – Enschede", "autoputevi/nl-a35.json"),
            new Autoput("NL", "A37", "Hoogeveen – Emmen – Duitse grens", "autoputevi/nl-a37.json"),
            new Autoput("NL", "A44", "Den Haag – Wassenaar – Leiden – Nieuw-Vennep", "autoputevi/nl-a44.json"),
            new Autoput("NL", "A50", "Eindhoven – Nijmegen – Arnhem – Zwolle", "autoputevi/nl-a50.json"),
            new Autoput("NL", "A58", "Eindhoven – Tilburg – Breda – Vlissingen", "autoputevi/nl-a58.json"),
            new Autoput("NL", "A59", "Zierikzee – Moerdijk – 's-Hertogenbosch – Oss", "autoputevi/nl-a59.json"),
            new Autoput("NL", "A65", "'s-Hertogenbosch – Tilburg", null),
            new Autoput("NL", "A67", "Antwerpen – Eindhoven – Venlo", null),
            new Autoput("NL", "A73", "Nijmegen – Venlo – Roermond", "autoputevi/nl-a73.json"),
            new Autoput("NL", "A76", "Geleen – Heerlen – Aachen", "autoputevi/nl-a76.json"),
            new Autoput("NL", "A79", "Maastricht – Heerlen", "autoputevi/nl-a79.json"),
            // Belgija
            new Autoput("BE", "A1", "E19 · Brussel – Antwerpen – Breda", "autoputevi/be-a1.json"),
            new Autoput("BE", "A2", "E314 · Leuven – Genk – Maastricht", "autoputevi/be-a2.json"),
            new Autoput("BE", "A3", "E40 · Brussel – Leuven – Liège – Aachen", "autoputevi/be-a3.json"),
            new Autoput("BE", "A4", "E411 · Bruxelles – Namur – Arlon – Luxembourg", "autoputevi/be-a4.json"),
            new Autoput("BE", "A7", "E19 · Nivelles – Mons – Paris", "autoputevi/be-a7.json"),
            new Autoput("BE", "A8", "E429 · Halle – Tournai – Lille", "autoputevi/be-a8.json"),
            new Autoput("BE", "A10", "E40 · Brussel – Gent – Brugge – Oostende", "autoputevi/be-a10.json"),
            new Autoput("BE", "A11", "Antwerpen – Brugge – Knokke", "autoputevi/be-a11.json"),
            new Autoput("BE", "A12", "Brussel – Boom – Antwerpen", "autoputevi/be-a12.json"),
            new Autoput("BE", "A13", "E313 · Antwerpen – Hasselt – Liège", "autoputevi/be-a13.json"),
            new Autoput("BE", "A14", "E17 · Antwerpen – Gent – Kortrijk – Lille", "autoputevi/be-a14.json"),
            new Autoput("BE", "A15", "E42 · Mons – Charleroi – Namur – Liège", null),
            new Autoput("BE", "A17", "E403 · Brugge – Kortrijk – Tournai", "autoputevi/be-a17.json"),
            new Autoput("BE", "A18", "E40 · Brugge – Veurne – Dunkerque", "autoputevi/be-a18.json"),
            new Autoput("BE", "A19", "Kortrijk – Ieper", null),
            new Autoput("BE", "A21", "E34 · Antwerpen – Turnhout – Eindhoven", "autoputevi/be-a21.json"),
            new Autoput("BE", "A25", "E25 · Liège – Maastricht", "autoputevi/be-a25.json"),
            new Autoput("BE", "A26", "E25 · Liège – Bastogne – Luxembourg", "autoputevi/be-a26.json"),
            new Autoput("BE", "A27", "E42 · Verviers – Sankt Vith", "autoputevi/be-a27.json"),
            new Autoput("BE", "A54", "E420 · Nivelles – Charleroi", null),
            // Luksemburg
            new Autoput("LU", "A1", "Luxembourg – Grevenmacher – Trier", null),
            new Autoput("LU", "A3", "Luxembourg – Dudelange – Thionville", "autoputevi/lu-a3.json"),
            new Autoput("LU", "A4", "Luxembourg – Esch-sur-Alzette", null),
            new Autoput("LU", "A6", "Luxembourg – Arlon", "autoputevi/lu-a6.json"),
            new Autoput("LU", "A7", "Luxembourg – Mersch – Ettelbruck", null),
            new Autoput("LU", "A13", "Pétange – Esch – Schengen", "autoputevi/lu-a13.json")
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
