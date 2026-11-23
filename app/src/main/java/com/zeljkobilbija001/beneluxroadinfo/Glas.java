package com.zeljkobilbija001.beneluxroadinfo;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Glasovne poruke u Vožnji (ugrađeni govornik telefona, bez interneta): pokretanje, autoput/km/pravac,
 * odmorište ispred, upozorenje o saobraćaju, zaustavljanje. Dok govori, muzika/navigacija se utišaju.
 * Uključuje se/isključuje dugmetom 🔊 na kartici Vožnja (podrazumevano uključeno).
 */
public final class Glas {
    private static final String PODESAVANJA = "glas";
    private static final String KLJUC = "ukljucen";

    private final Context c;
    private TextToSpeech tts;
    private boolean spreman, gasiPosle;
    private final List<String> naCekanju = new ArrayList<>();
    private AudioManager am;
    private AudioFocusRequest fokus;
    private int brojac;

    public static boolean ukljucen(Context c) {
        return c.getSharedPreferences(PODESAVANJA, Context.MODE_PRIVATE).getBoolean(KLJUC, true);
    }

    public static void postavi(Context c, boolean da) {
        SharedPreferences.Editor e = c.getSharedPreferences(PODESAVANJA, Context.MODE_PRIVATE).edit();
        e.putBoolean(KLJUC, da).apply();
    }

    public Glas(Context c) {
        this.c = c.getApplicationContext();
        am = (AudioManager) this.c.getSystemService(Context.AUDIO_SERVICE);
        tts = new TextToSpeech(this.c, status -> {
            if (status != TextToSpeech.SUCCESS || tts == null) return;
            // jezik aplikacije; ako telefon nema glas za njega, engleski
            Locale jezik = this.c.getResources().getConfiguration().getLocales().get(0);
            int r = tts.setLanguage(jezik);
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.UK);
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { gotovo(); }
                @Override public void onError(String id) { gotovo(); }
            });
            synchronized (this) {
                spreman = true;
                for (String t : naCekanju) izgovori(t);
                naCekanju.clear();
            }
        });
    }

    /** Izgovori poruku (redom, posle prethodne). Ne radi ako je glas isključen. */
    public synchronized void reci(String tekst) {
        if (tekst == null || tekst.isEmpty() || !ukljucen(c) || tts == null) return;
        if (!spreman) { naCekanju.add(tekst); return; }
        izgovori(tekst);
    }

    private void izgovori(String tekst) {
        if (Build.VERSION.SDK_INT >= 26 && am != null && fokus == null) {
            fokus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .build();
            am.requestAudioFocus(fokus);
        }
        tts.speak(tekst, TextToSpeech.QUEUE_ADD, new Bundle(), "g" + (++brojac));
    }

    private synchronized void gotovo() {
        if (tts == null || tts.isSpeaking()) return;
        if (fokus != null && am != null && Build.VERSION.SDK_INT >= 26) am.abandonAudioFocusRequest(fokus);
        fokus = null;
        if (gasiPosle) ugasiOdmah();
    }

    /** Ugasi govornik kad izgovori ono što je u redu (npr. „Vožnja je zaustavljena“), najkasnije za 8 s. */
    public synchronized void ugasi() {
        if (tts == null || !spreman) { ugasiOdmah(); return; }
        gasiPosle = true;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::ugasiOdmah, 8000L);
    }

    private synchronized void ugasiOdmah() {
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        if (fokus != null && am != null && Build.VERSION.SDK_INT >= 26) am.abandonAudioFocusRequest(fokus);
        fokus = null;
    }
}
