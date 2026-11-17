package com.zeljkobilbija001.beneluxroadinfo;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.List;

/**
 * Kupovina „Bez reklama“ (jednokratni proizvod {@value #PROIZVOD} na Google Play-u, 2,99 €).
 *
 * Stanje se pamti lokalno (brz početak bez mreže), a pri svakom pokretanju potvrđuje kod Play-a:
 * kupovina je vezana za Google nalog, pa se posle nove instalacije ili na drugom telefonu sama vraća.
 * Kupovina mora da se potvrdi (acknowledge) u roku od 3 dana, inače Google vraća novac.
 */
public final class Kupovina {

    /** ID proizvoda u Play Console-u (Monetizza → Prodotti a pagamento singolo). Ne menja se. */
    public static final String PROIZVOD = "remove_ads";
    private static final String PODESAVANJA = "kupovina", KLJUC = "bez_reklama";

    private static BillingClient klijent;
    private static Runnable posleKupovine;

    private Kupovina() { }

    /** Da li je korisnik kupio „Bez reklama“ (poslednje poznato stanje). */
    public static boolean bezReklama(Context c) {
        return prefs(c).getBoolean(KLJUC, false);
    }

    /** Pozvati na početnom ekranu: poveže se sa Play-om i osveži stanje kupovine. */
    public static void proveri(Context c, Runnable kadaSePromeni) {
        Context app = c.getApplicationContext();
        if (klijent == null) {
            klijent = BillingClient.newBuilder(app)
                    .setListener((rezultat, kupovine) -> {
                        if (rezultat.getResponseCode() == BillingClient.BillingResponseCode.OK && kupovine != null) {
                            obradi(app, kupovine);
                        } else if (rezultat.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                            sacuvaj(app, true);
                            if (posleKupovine != null) posleKupovine.run();
                        }
                    })
                    .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                    .enableAutoServiceReconnection()
                    .build();
        }
        if (klijent.isReady()) { osvezi(app, kadaSePromeni); return; }
        klijent.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult r) {
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) osvezi(app, kadaSePromeni);
            }
            @Override public void onBillingServiceDisconnected() { }
        });
    }

    /** Pokreće Google Play prozor za kupovinu (u njemu je cena u valuti korisnika). */
    public static void kupi(Activity a, Runnable posle) {
        posleKupovine = posle;
        if (klijent == null || !klijent.isReady()) {
            proveri(a, null);
            Toast.makeText(a, R.string.kupovina_nedostupna, Toast.LENGTH_SHORT).show();
            return;
        }
        QueryProductDetailsParams upit = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PROIZVOD).setProductType(BillingClient.ProductType.INAPP).build()))
                .build();
        klijent.queryProductDetailsAsync(upit, (rezultat, odgovor) -> {
            List<ProductDetails> lista = odgovor.getProductDetailsList();
            if (rezultat.getResponseCode() != BillingClient.BillingResponseCode.OK || lista.isEmpty()) {
                a.runOnUiThread(() -> Toast.makeText(a, R.string.kupovina_nedostupna, Toast.LENGTH_SHORT).show());
                return;
            }
            BillingFlowParams tok = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(Collections.singletonList(
                            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(lista.get(0)).build()))
                    .build();
            a.runOnUiThread(() -> klijent.launchBillingFlow(a, tok));
        });
    }

    private static void osvezi(Context app, Runnable kadaSePromeni) {
        klijent.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                (rezultat, kupovine) -> {
                    if (rezultat.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
                    boolean pre = bezReklama(app);
                    boolean ima = false;
                    for (Purchase p : kupovine) if (jeNasa(p)) { ima = true; potvrdi(p); }
                    sacuvaj(app, ima);
                    if (pre != ima && kadaSePromeni != null) kadaSePromeni.run();
                });
    }

    private static void obradi(Context app, List<Purchase> kupovine) {
        for (Purchase p : kupovine) {
            if (!jeNasa(p)) continue;
            potvrdi(p);
            sacuvaj(app, true);
            if (posleKupovine != null) posleKupovine.run();
        }
    }

    /** Plaćena kupovina našeg proizvoda (ne ona koja još čeka uplatu). */
    private static boolean jeNasa(Purchase p) {
        return p.getProducts().contains(PROIZVOD) && p.getPurchaseState() == Purchase.PurchaseState.PURCHASED;
    }

    private static void potvrdi(Purchase p) {
        if (p.isAcknowledged()) return;
        klijent.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.getPurchaseToken()).build(), r -> { });
    }

    private static void sacuvaj(Context c, boolean kupljeno) {
        prefs(c).edit().putBoolean(KLJUC, kupljeno).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PODESAVANJA, Context.MODE_PRIVATE);
    }
}
