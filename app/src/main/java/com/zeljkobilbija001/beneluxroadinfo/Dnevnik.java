package com.zeljkobilbija001.beneluxroadinfo;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Dnevnik za probu (samo u debug verziji): šta je režim vožnje i provera u pozadini videla i odlučila.
 * Čuva se u fajlu, da preživi gašenje aplikacije; kad pređe ~400 KB, stariji deo se odbacuje.
 */
public final class Dnevnik {

    private static final String FAJL = "dnevnik.txt";
    private static final long NAJVISE = 400_000L;
    private static Boolean ukljucen;

    private Dnevnik() { }

    /** Uključen samo u debug verziji (ne i u verziji za Google Play). */
    public static boolean ukljucen(Context c) {
        if (ukljucen == null) {
            ukljucen = (c.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        }
        return ukljucen;
    }

    public static synchronized void upisi(Context c, String tekst) {
        if (!ukljucen(c)) return;
        File f = new File(c.getFilesDir(), FAJL);
        String red = new SimpleDateFormat("dd.MM HH:mm:ss", Locale.US).format(new Date()) + "  " + tekst + "\n";
        try {
            if (f.length() > NAJVISE) {
                String sve = citaj(f);
                String ostatak = sve.substring(sve.length() / 2);
                int nl = ostatak.indexOf('\n');
                try (FileOutputStream out = new FileOutputStream(f, false)) {
                    out.write(ostatak.substring(nl + 1).getBytes(StandardCharsets.UTF_8));
                }
            }
            try (FileOutputStream out = new FileOutputStream(f, true)) {
                out.write(red.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { }
    }

    public static synchronized String procitaj(Context c) {
        File f = new File(c.getFilesDir(), FAJL);
        try {
            return f.exists() ? citaj(f) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String citaj(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public static synchronized void obrisi(Context c) {
        //noinspection ResultOfMethodCallIgnored
        new File(c.getFilesDir(), FAJL).delete();
    }
}
