package com.zeljkobilbija001.beneluxroadinfo;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AdMob baner i saglasnost za privatnost (GDPR, preko Google UMP).
 *
 * Tok: početni ekran pri svakom pokretanju traži saglasnost (prozor se prikazuje
 * samo kada je potrebno, npr. korisnicima u EU). Tek kada je dozvoljeno,
 * pokreće se Mobile Ads SDK i učitavaju baneri. Ostali ekrani samo prikazuju
 * baner ako je saglasnost već sređena.
 */
public final class Reklame {

    private static final AtomicBoolean SDK_POKRENUT = new AtomicBoolean(false);
    private static final int VISINA_BANERA_DP = 70;

    private Reklame() { }

    /** Da li smemo da tražimo reklame (saglasnost data ili nije potrebna). */
    public static boolean dozvoljene(Context c) {
        // kupljeno „Bez reklama“: nema banera ni interstitiala
        return !Kupovina.bezReklama(c) && UserMessagingPlatform.getConsentInformation(c).canRequestAds();
    }

    /** Da li korisniku treba ponuditi dugme za podešavanja privatnosti. */
    public static boolean trebaDugmePrivatnosti(Context c) {
        return UserMessagingPlatform.getConsentInformation(c).getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    /** Prozor gde korisnik može da promeni izbor o privatnosti. */
    public static void prikaziPodesavanjaPrivatnosti(Activity a, Runnable posle) {
        UserMessagingPlatform.showPrivacyOptionsForm(a, greska -> {
            if (posle != null) posle.run();
        });
    }

    /**
     * Pozvati na početnom ekranu pri svakom pokretanju. Kada je saglasnost
     * sređena (ili nije potrebna), pokreće SDK i poziva {@code kadaSpremno}.
     */
    public static void prikupiSaglasnost(Activity a, Runnable kadaSpremno) {
        if (Kupovina.bezReklama(a)) { if (kadaSpremno != null) kadaSpremno.run(); return; }
        ConsentInformation info = UserMessagingPlatform.getConsentInformation(a);
        ConsentRequestParameters parametri = new ConsentRequestParameters.Builder().build();
        info.requestConsentInfoUpdate(a, parametri,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(a, greska -> {
                    if (info.canRequestAds()) pokreniSdk(a, kadaSpremno);
                    else if (kadaSpremno != null) kadaSpremno.run(); // npr. da se osveži dugme privatnosti
                }),
                // Bez interneta i sl.: koristi se izbor iz prethodnog pokretanja
                greska -> {
                    if (info.canRequestAds()) pokreniSdk(a, kadaSpremno);
                });
        // Ako je saglasnost već data ranije, ne čekamo mrežu
        if (info.canRequestAds()) pokreniSdk(a, kadaSpremno);
    }

    private static void pokreniSdk(Activity a, Runnable kadaSpremno) {
        if (!SDK_POKRENUT.getAndSet(true)) {
            Context app = a.getApplicationContext();
            // Inicijalizacija na pozadinskoj niti, da ne usporava pokretanje
            new Thread(() -> MobileAds.initialize(app, status -> { })).start();
        }
        if (kadaSpremno != null) a.runOnUiThread(kadaSpremno);
    }

    /**
     * Postavlja prilagodljivi baner u kontejner, ako su reklame dozvoljene.
     * Baner sam prati životni ciklus ekrana (pauza, nastavak, uništavanje).
     */
    public static void prikaziBaner(AppCompatActivity a, FrameLayout kontejner) {
        if (kontejner == null || kontejner.getChildCount() > 0 || !dozvoljene(a)) return;
        if (!SDK_POKRENUT.get()) {
            SDK_POKRENUT.set(true);
            Context app = a.getApplicationContext();
            new Thread(() -> MobileAds.initialize(app, status -> { })).start();
        }
        // Širina je poznata tek kada se raspored izračuna
        kontejner.post(() -> {
            if (a.isFinishing() || a.isDestroyed() || kontejner.getChildCount() > 0) return;
            AdView baner = new AdView(a);
            baner.setAdUnitId(a.getString(R.string.admob_banner_id));
            // Najviše 70 dp visine (veliki prilagodljivi baner je zauzimao previše ekrana)
            baner.setAdSize(AdSize.getInlineAdaptiveBannerAdSize(sirinaDp(a, kontejner), VISINA_BANERA_DP));
            kontejner.addView(baner);
            baner.loadAd(new AdRequest.Builder().build());
            ucitajInterstitial(a); // da bude spreman kad zatreba

            a.getLifecycle().addObserver((LifecycleEventObserver) (izvor, dogadjaj) -> {
                if (dogadjaj == Lifecycle.Event.ON_PAUSE) baner.pause();
                else if (dogadjaj == Lifecycle.Event.ON_RESUME) baner.resume();
                else if (dogadjaj == Lifecycle.Event.ON_DESTROY) {
                    kontejner.removeView(baner);
                    baner.destroy();
                }
            });
        });
    }

    // ---------- Interstitial (oglas preko celog ekrana) ----------
    /** Najranije pri trećem otvaranju autoputa, pa svako treće, i najviše jednom u 3 minuta. */
    private static final int SVAKO_OTVARANJE = 3;
    private static final long RAZMAK_MS = 3 * 60 * 1000L;
    private static InterstitialAd interstitial;
    private static boolean ucitavaSe;
    private static int otvaranja;
    private static long poslednjiPrikaz = -RAZMAK_MS;

    /** Unapred učita interstitial (ako su reklame dozvoljene, ID je upisan i već nije učitan). */
    public static void ucitajInterstitial(Context c) {
        if (interstitial != null || ucitavaSe || !dozvoljene(c)) return;
        Context app = c.getApplicationContext();
        String id = app.getString(R.string.admob_interstitial_id);
        if (id.isEmpty()) return;
        ucitavaSe = true;
        InterstitialAd.load(app, id, new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override public void onAdLoaded(@NonNull InterstitialAd oglas) { interstitial = oglas; ucitavaSe = false; }
                    @Override public void onAdFailedToLoad(@NonNull LoadAdError greska) { ucitavaSe = false; }
                });
    }

    /**
     * Na prelazu između ekrana (otvaranje autoputa): ako je vreme za oglas i oglas je spreman,
     * prikaže ga pa posle zatvaranja nastavi; inače odmah nastavi. Korisnik nikad ne čeka učitavanje.
     * Dok je uključena Vožnja oglasa nema (aplikacija se koristi u autu).
     */
    public static void posleOglasa(Activity a, Runnable dalje) {
        otvaranja++;
        long sad = SystemClock.elapsedRealtime();
        InterstitialAd oglas = interstitial;
        if (oglas == null || VoznjaService.radi || otvaranja < SVAKO_OTVARANJE
                || sad - poslednjiPrikaz < RAZMAK_MS || !dozvoljene(a)) {
            dalje.run();
            ucitajInterstitial(a);
            return;
        }
        interstitial = null;
        otvaranja = 0;
        poslednjiPrikaz = sad;
        oglas.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { dalje.run(); ucitajInterstitial(a); }
            @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError greska) { dalje.run(); ucitajInterstitial(a); }
        });
        oglas.show(a);
    }

    /** Širina kontejnera u dp (ili širina ekrana ako kontejner još nema širinu). */
    private static int sirinaDp(@NonNull Activity a, @NonNull View kontejner) {
        DisplayMetrics m = a.getResources().getDisplayMetrics();
        int px = kontejner.getWidth() > 0 ? kontejner.getWidth() : m.widthPixels;
        return Math.max(1, (int) (px / m.density));
    }
}
